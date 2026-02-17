package net.marllex.waselak.backend.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import net.marllex.waselak.backend.config.HmacConfig
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.fixedRateTimer

private val logger = LoggerFactory.getLogger("HmacVerification")

fun Application.configureHmacVerification() {
    val hmacConfig by inject<HmacConfig>()

    // Allow request body to be read twice (once for HMAC, once for route handler)
    install(DoubleReceive)

    // Nonce cache: tracks used nonces to prevent replay attacks
    val usedNonces = ConcurrentHashMap<String, Long>()

    // Cleanup expired nonces every 10 minutes
    fixedRateTimer("nonce-cleanup", daemon = true, period = 600_000L) {
        val cutoff = System.currentTimeMillis() - hmacConfig.timestampToleranceMs
        usedNonces.entries.removeIf { it.value < cutoff }
    }

    // --- Request HMAC verification ---
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()

        // Skip HMAC verification for health check, public endpoints, and admin routes
        if (path == "/health" ||
            path.startsWith("/public/") ||
            path.startsWith("/api/v1/admin")
        ) {
            return@intercept
        }

        // Only verify /api/ endpoints
        if (!path.startsWith("/api/")) {
            return@intercept
        }

        val timestamp = call.request.header("X-Timestamp")
        val nonce = call.request.header("X-Nonce")
        val signature = call.request.header("X-Signature")

        if (timestamp == null || nonce == null || signature == null) {
            logger.warn("HMAC: Missing headers on {} {} - ts={} nonce={} sig={}", call.request.httpMethod.value, path, timestamp != null, nonce != null, signature != null)
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Missing security headers")
            )
            finish()
            return@intercept
        }

        // 1. Validate timestamp window
        val requestTime = timestamp.toLongOrNull()
        if (requestTime == null) {
            logger.warn("HMAC: Invalid timestamp format: '{}'", timestamp)
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Invalid timestamp")
            )
            finish()
            return@intercept
        }

        val now = System.currentTimeMillis()
        if (kotlin.math.abs(now - requestTime) > hmacConfig.timestampToleranceMs) {
            logger.warn("HMAC: Expired request. now={} request={} diff={}ms", now, requestTime, now - requestTime)
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Request expired")
            )
            finish()
            return@intercept
        }

        // 2. Check nonce uniqueness (prevent replay)
        if (usedNonces.putIfAbsent(nonce, now) != null) {
            logger.warn("HMAC: Duplicate nonce: {}", nonce)
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Duplicate request")
            )
            finish()
            return@intercept
        }

        // 3. Verify HMAC signature
        val method = call.request.httpMethod.value
        val body = try {
            call.receiveText()
        } catch (_: Exception) {
            ""
        }
        val bodyHash = sha256(body)

        val payload = "$method\n$path\n$timestamp\n$nonce\n$bodyHash"
        val expectedSignature = hmacSha256(hmacConfig.secret, payload)

        if (!constantTimeEquals(signature, expectedSignature)) {
            logger.warn("HMAC: Signature mismatch on {} {}", method, path)
            logger.warn("HMAC: client_sig='{}' server_sig='{}'", signature, expectedSignature)
            logger.warn("HMAC: payload='{}'", payload)
            logger.warn("HMAC: body_length={} body_hash='{}'", body.length, bodyHash)
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Invalid request signature")
            )
            finish()
            return@intercept
        }

        logger.debug("HMAC: Verified {} {}", method, path)
    }

    // --- Response HMAC signing ---
    install(createApplicationPlugin("ResponseHmacSigning") {
        onCallRespond { call ->
            val path = call.request.path()

            // Only sign /api/ responses (same scope as request verification)
            if (!path.startsWith("/api/") ||
                path.startsWith("/api/v1/admin")
            ) {
                return@onCallRespond
            }

            transformBody { body ->
                // Skip HMAC signing for binary responses (PDF, Excel, images, etc.)
                // Binary data cannot be safely round-tripped through UTF-8 String
                // decoding, which causes the client to compute a different hash and
                // reject the response as tampered. The client gracefully handles
                // missing X-Response-Signature headers.
                val ct = if (body is OutgoingContent) {
                    body.contentType?.toString() ?: ""
                } else ""
                val isBinary = ct.contains("application/pdf") ||
                    ct.contains("application/octet-stream") ||
                    ct.contains("application/vnd.") ||
                    ct.startsWith("image/")

                if (isBinary) {
                    logger.debug("HMAC: Skipping response signing for binary content-type: {}", ct)
                    return@transformBody body
                }

                val responseBody = when (body) {
                    is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                    is OutgoingContent.NoContent -> ""
                    else -> ""
                }
                val timestamp = System.currentTimeMillis().toString()
                val bodyHash = sha256(responseBody)
                val payload = "$timestamp\n$bodyHash"
                val signature = hmacSha256(hmacConfig.secret, payload)

                call.response.header("X-Response-Timestamp", timestamp)
                call.response.header("X-Response-Signature", signature)

                body
            }
        }
    })
}

private fun hmacSha256(key: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private fun sha256(data: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(data.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].code xor b[i].code)
    }
    return result == 0
}
