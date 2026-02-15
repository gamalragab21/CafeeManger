package net.marllex.waselak.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Simple multiplatform JWT parser.
 * JWT is just base64url-encoded JSON: header.payload.signature
 */
object JwtParser {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    fun parsePayload(token: String): JsonObject? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payload = parts[1]
            // Base64url decode (replace URL-safe chars, add padding)
            val padded = payload
                .replace('-', '+')
                .replace('_', '/')
                .let {
                    val remainder = it.length % 4
                    if (remainder > 0) it + "=".repeat(4 - remainder) else it
                }

            val decoded = Base64.decode(padded).decodeToString()
            json.decodeFromString<JsonObject>(decoded)
        } catch (_: Exception) {
            null
        }
    }

    fun getSubject(token: String): String? =
        parsePayload(token)?.get("sub")?.jsonPrimitive?.content

    fun getClaim(token: String, claim: String): String? =
        parsePayload(token)?.get(claim)?.jsonPrimitive?.content

    fun isExpired(token: String, leewaySeconds: Long = 10): Boolean {
        val payload = parsePayload(token) ?: return true
        val exp = payload["exp"]?.jsonPrimitive?.long ?: return true
        val now = kotlinx.datetime.Clock.System.now().epochSeconds
        return now >= (exp - leewaySeconds)
    }
}
