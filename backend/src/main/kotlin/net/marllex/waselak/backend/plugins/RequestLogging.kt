package net.marllex.waselak.backend.plugins

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.util.*
import net.marllex.waselak.backend.domain.service.RequestLogService
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import org.slf4j.MDC

private val logger = LoggerFactory.getLogger("RequestLogging")

private val requestStartTimeKey = AttributeKey<Long>("requestStartTime")
private val requestBodyKey = AttributeKey<String>("requestBody")

private val SKIP_PATHS = listOf(
    "/health",
    "/uploads/",
    "/admin/",
)

fun Application.configureRequestLogging() {
    val config = environment.config
    val maxBodySize = config.propertyOrNull("logging.maxBodyLogSize")?.getString()?.toIntOrNull() ?: 10_240
    val requestLogService by inject<RequestLogService>()

    install(createApplicationPlugin("RequestLogging") {
        onCall { call ->
            val path = call.request.path()

            // Skip non-API and excluded paths
            if (SKIP_PATHS.any { path.startsWith(it) }) {
                return@onCall
            }

            call.attributes.put(requestStartTimeKey, System.currentTimeMillis())

            // Skip body capture entirely for multipart uploads. The body is a
            // binary stream of file bytes (PNG/JPG signatures contain 0x00),
            // which Postgres rejects as invalid UTF-8 when we try to persist
            // request_body to request_logs. Reading it here would also
            // double-buffer the whole upload in memory for no benefit, since
            // we never log the binary content anyway.
            val ct = call.request.header("Content-Type") ?: ""
            if (ct.startsWith("multipart/", ignoreCase = true)) {
                return@onCall
            }

            // Capture request body (DoubleReceive is already installed by HMAC plugin)
            try {
                val body = call.receiveText()
                if (body.isNotBlank()) {
                    call.attributes.put(requestBodyKey, truncate(body, maxBodySize))
                }
            } catch (_: Exception) {
                // Body may not be text (multipart, empty, etc.)
            }
        }

        onCallRespond { call ->
            val path = call.request.path()

            if (SKIP_PATHS.any { path.startsWith(it) }) {
                return@onCallRespond
            }

            transformBody { body ->
                val startTime = call.attributes.getOrNull(requestStartTimeKey)
                val durationMs = if (startTime != null) System.currentTimeMillis() - startTime else -1
                val statusCode = call.response.status()?.value ?: 0
                val method = call.request.httpMethod.value

                // Extract user principal from JWT (null for public routes)
                val principal = call.principal<UserPrincipal>()

                // Capture response body for ALL responses
                val responseText = when (body) {
                    is OutgoingContent.ByteArrayContent -> {
                        val ct = body.contentType?.toString() ?: ""
                        if (ct.contains("application/json") || ct.contains("text/")) {
                            truncate(body.bytes().decodeToString(), maxBodySize)
                        } else null
                    }
                    else -> null
                }

                val vendorId = principal?.vendorId
                val userId = principal?.userId
                val userRole = principal?.role
                val clientIp = call.request.header("X-Forwarded-For")
                    ?: call.request.local.remoteHost
                val userAgent = truncate(call.request.header("User-Agent") ?: "", 500)
                val reqBody = call.attributes.getOrNull(requestBodyKey)
                val sanitizedReqBody = reqBody?.let { sanitizeBody(it) }
                val queryParams = call.request.queryString().ifBlank { null }

                // Build MDC context for structured JSON logging
                try {
                    MDC.put("vendorId", vendorId ?: "anonymous")
                    MDC.put("userId", userId ?: "anonymous")
                    MDC.put("userRole", userRole ?: "none")
                    MDC.put("method", method)
                    MDC.put("path", path)
                    MDC.put("queryParams", queryParams)
                    MDC.put("statusCode", statusCode.toString())
                    MDC.put("durationMs", durationMs.toString())
                    MDC.put("clientIp", clientIp)
                    MDC.put("userAgent", userAgent)

                    if (sanitizedReqBody != null) {
                        MDC.put("requestBody", sanitizedReqBody)
                    }
                    if (responseText != null) {
                        MDC.put("responseBody", responseText)
                    }

                    val message = "$method $path -> $statusCode (${durationMs}ms)"
                    when {
                        statusCode >= 500 -> {
                            MDC.put("errorMessage", responseText ?: "Internal Server Error")
                            logger.error(message)
                        }
                        statusCode >= 400 -> {
                            MDC.put("errorMessage", responseText)
                            logger.warn(message)
                        }
                        else -> logger.info(message)
                    }
                } finally {
                    MDC.clear()
                }

                // Analyze path for resource, action, and tags
                val analysis = PathAnalyzer.analyze(
                    method = method,
                    path = path,
                    queryParams = queryParams,
                    requestBody = sanitizedReqBody
                )

                // Generate human-readable description of what the API call did
                val description = PathAnalyzer.generateDescription(
                    analysis = analysis,
                    statusCode = statusCode,
                    responseBody = responseText,
                )

                // Capture route trace (step-by-step logging from route handlers)
                val traceLog = call.attributes.getOrNull(routeTraceKey)?.toJson()

                // Persist to database (async, fire-and-forget)
                requestLogService.insertAsync(
                    RequestLogService.LogEntry(
                        vendorId = vendorId,
                        userId = userId,
                        userRole = userRole,
                        method = method,
                        path = path,
                        queryParams = queryParams,
                        statusCode = statusCode,
                        durationMs = durationMs,
                        clientIp = clientIp,
                        userAgent = userAgent,
                        requestBody = sanitizedReqBody,
                        responseBody = responseText,
                        errorMessage = if (statusCode >= 400) responseText else null,
                        resource = analysis.resource,
                        action = analysis.action,
                        tags = PathAnalyzer.tagsToJson(analysis.tags),
                        description = description,
                        traceLog = traceLog,
                    )
                )

                body // pass through unchanged
            }
        }
    })
}

/** Truncate a string to maxLen characters. */
private fun truncate(value: String, maxLen: Int): String {
    return if (value.length > maxLen) value.take(maxLen) + "...[truncated]" else value
}

/** Remove sensitive fields from JSON-like request bodies. */
private fun sanitizeBody(body: String): String {
    return body
        // Strip NULL bytes — Postgres' UTF-8 text columns refuse 0x00 and the
        // whole INSERT errors out, swallowing the legitimate request log. We
        // still skip multipart in onCall above; this is belt-and-suspenders
        // for any future caller that slips a binary blob through.
        .replace("\u0000", "")
        .replace(Regex(""""password"\s*:\s*"[^"]*""""), """"password":"***"""")
        .replace(Regex(""""password_hash"\s*:\s*"[^"]*""""), """"password_hash":"***"""")
        .replace(Regex(""""token"\s*:\s*"[^"]*""""), """"token":"***"""")
        .replace(Regex(""""refresh_token"\s*:\s*"[^"]*""""), """"refresh_token":"***"""")
        .replace(Regex(""""secret"\s*:\s*"[^"]*""""), """"secret":"***"""")
}
