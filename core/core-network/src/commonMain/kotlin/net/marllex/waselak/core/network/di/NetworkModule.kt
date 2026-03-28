package net.marllex.waselak.core.network.di

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter
import kotlinx.coroutines.runBlocking
import net.marllex.waselak.core.network.ApiException
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.auth.TokenProvider
import net.marllex.waselak.core.network.datasource.WorkerNetworkDataSource
import net.marllex.waselak.core.network.dto.ApiErrorResponse
import net.marllex.waselak.core.network.security.HmacSigner
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }
    }

    single<HttpClientEngineFactory<*>> { httpEngineFactory() }

    single {
        val tokenProvider = get<TokenProvider>()
        val hmacSecret = get<String>(qualifier = named("hmacSecret"))

        HttpClient(get<HttpClientEngineFactory<*>>()) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000   // 60 seconds for full request
                connectTimeoutMillis = 30_000   // 30 seconds to establish connection
                socketTimeoutMillis = 60_000    // 60 seconds for socket read/write
            }
            install(ContentNegotiation) {
                json(get<Json>())
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        co.touchlab.kermit.Logger.d("KtorHttp") { message }
                        // Log full HTTP to file only (AppLogger without Sentry bridge)
                        AppLogger.logToFileOnly("HTTP", message)
                        // Log to Sentry — full detail for debugging
                        CrashReporter.logInfo("HTTP", message)
                    }
                }
                level = LogLevel.ALL
            }
            HttpResponseValidator {
                validateResponse { response ->
                    val path = response.call.request.url.encodedPath
                    val method = response.call.request.method.value
                    val status = response.status.value
                    val queryString = response.call.request.url.encodedQuery
                    val fullPath = if (queryString.isNotBlank()) "$path?$queryString" else path

                    // Log request details to Sentry
                    val reqBody = try {
                        when (val body = response.call.request.content) {
                            is TextContent -> body.text
                            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                            else -> ""
                        }
                    } catch (_: Exception) { "" }
                    val reqTrunc = if (reqBody.length > 2000) reqBody.take(2000) + "..." else reqBody

                    CrashReporter.logNetwork(method, fullPath, status, 0L)

                    // Save response for body reading
                    val savedResp = response.call.save()
                    val respBody = try { savedResp.response.bodyAsText() } catch (_: Exception) { "<unreadable>" }
                    val respTrunc = if (respBody.length > 2000) respBody.take(2000) + "..." else respBody

                    // Log FULL request + response to Sentry as ONE entry
                    CrashReporter.logInfo("API", "$method $fullPath → $status\n" +
                        "REQUEST: $reqTrunc\n" +
                        "RESPONSE: $respTrunc")

                    if (!response.status.isSuccess()) {
                        val truncBody = if (respBody.length > 3000) respBody.take(3000) + "...[truncated]" else respBody
                        val respHeaders = response.headers.entries()
                            .joinToString(", ") { (k, v) -> "$k: ${v.joinToString()}" }
                        AppLogger.logToFileOnly("HTTP-RES", "$method $path -> $status | headers=[$respHeaders] | body=$truncBody")
                        val errorBody = try {
                            savedResp.response.body<ApiErrorResponse>()
                        } catch (_: Exception) {
                            null
                        }
                        val message = errorBody?.message
                            ?: "HTTP ${response.status.value}: ${response.status.description}"
                        val errorType = errorBody?.error

                        // If vendor account is suspended, force-clear tokens to trigger
                        // auto-logout (isLoggedIn → false → navigate to login screen)
                        if (status == 403 && errorType == "ACCOUNT_SUSPENDED") {
                            AppLogger.e("HTTP-RES", "ACCOUNT SUSPENDED — forcing logout")
                            try { tokenProvider.clearTokens() } catch (_: Exception) {}
                        }

                        throw ApiException(
                            statusCode = response.status.value,
                            errorMessage = message,
                            errorType = errorType
                        )
                    }
                }
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        val accessToken = tokenProvider.getAccessToken()
                        val refreshToken = tokenProvider.getRefreshToken()
                        if (accessToken != null) {
                            BearerTokens(accessToken, refreshToken ?: "")
                        } else {
                            null
                        }
                    }
                    refreshTokens {
                        AppLogger.i("Network", "Bearer token expired, refreshing...")
                        val refreshToken = tokenProvider.getRefreshToken()
                        if (refreshToken != null) {
                            val newTokens = tokenProvider.refreshTokens(refreshToken)
                            if (newTokens != null) {
                                AppLogger.i("Network", "Bearer token refreshed successfully")
                                BearerTokens(newTokens.accessToken, newTokens.refreshToken)
                            } else {
                                AppLogger.e("Network", "Token refresh returned null")
                                null
                            }
                        } else {
                            AppLogger.e("Network", "No refresh token available")
                            null
                        }
                    }
                    sendWithoutRequest { request ->
                        // Skip auth header for public endpoints
                        val path = request.url.buildString()
                        !path.contains("auth/login") &&
                            !path.contains("auth/refresh")
                    }
                }
            }
            defaultRequest {
                url(get<String>(qualifier = named("baseUrl")))
                contentType(ContentType.Application.Json)
                header("ngrok-skip-browser-warning", "true")
            }
        }.also { client ->
            // Interceptor for request/response body logging + HMAC signing
            // HttpSend fires after ContentNegotiation has serialized the body
            client.plugin(HttpSend).intercept { request ->
                val method = request.method.value
                val rawPath = request.url.encodedPath
                val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
                val isUpload = path.contains("/upload")

                // CRITICAL: Always override the Authorization header with the latest token
                // from persistent storage. The Ktor Auth bearer plugin caches tokens in memory
                // after loadTokens() is called once. When the user logs out and re-logs as a
                // different vendor, the plugin still sends the OLD vendor's JWT (stale cache).
                // This override ensures every request uses the freshest token from DataStore.
                val isPublicPath = path.contains("auth/login") || path.contains("auth/refresh")
                if (!isPublicPath) {
                    val latestToken = tokenProvider.getAccessToken()
                    if (latestToken != null) {
                        request.headers.remove("Authorization")
                        request.headers.append("Authorization", "Bearer $latestToken")
                    }
                }

                // Read the serialized body text from the rendered OutgoingContent
                val bodyText = when (val body = request.body) {
                    is TextContent -> body.text
                    is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                    is OutgoingContent.NoContent -> ""
                    else -> "<binary>"
                }

                // Log request with headers and body
                val reqHeaders = request.headers.build().entries()
                    .joinToString(", ") { (k, v) ->
                        if (k.equals("Authorization", ignoreCase = true)) "$k: [REDACTED]"
                        else "$k: ${v.joinToString()}"
                    }
                if (isUpload) {
                    AppLogger.i("HTTP-REQ", "$method $path | headers=[$reqHeaders] | body=<upload binary>")
                } else {
                    val truncReqBody = if (bodyText.length > 3000) bodyText.take(3000) + "...[truncated]" else bodyText
                    AppLogger.i("HTTP-REQ", "$method $path | headers=[$reqHeaders] | body=$truncReqBody")
                }

                // HMAC signing (only when secret is set and not upload)
                if (hmacSecret.isNotBlank() && !isUpload) {
                    AppLogger.d("HMAC", "Signing: $method $path")
                    val timestamp = kotlinx.datetime.Clock.System.now()
                        .toEpochMilliseconds().toString()
                    val nonce = Uuid.random().toString()
                    val bodyHash = HmacSigner.sha256(bodyText)
                    val payload = "$method\n$path\n$timestamp\n$nonce\n$bodyHash"
                    val signature = HmacSigner.hmacSha256(hmacSecret, payload)

                    request.headers.remove("X-Timestamp")
                    request.headers.remove("X-Nonce")
                    request.headers.remove("X-Signature")
                    request.headers.append("X-Timestamp", timestamp)
                    request.headers.append("X-Nonce", nonce)
                    request.headers.append("X-Signature", signature)
                } else if (isUpload && hmacSecret.isNotBlank()) {
                    AppLogger.d("HMAC", "Skipping HMAC for upload: $method $path")
                }

                val call = execute(request)

                // HMAC response verification (only when secret is set)
                if (hmacSecret.isNotBlank()) {
                    val respTimestamp = call.response.headers["X-Response-Timestamp"]
                    val respSignature = call.response.headers["X-Response-Signature"]
                    if (respTimestamp != null && respSignature != null) {
                        val savedCall = call.save()
                        val respBody = savedCall.response.bodyAsText()
                        val respBodyHash = HmacSigner.sha256(respBody)
                        val respPayload = "$respTimestamp\n$respBodyHash"
                        val expectedSig = HmacSigner.hmacSha256(hmacSecret, respPayload)
                        if (respSignature != expectedSig) {
                            AppLogger.e("HMAC", "Response TAMPERED: $method $path | expected=$expectedSig got=$respSignature")
                            throw ApiException(
                                statusCode = 0,
                                errorMessage = "Response signature verification failed",
                                errorType = "RESPONSE_TAMPERED"
                            )
                        }
                        AppLogger.d("HMAC", "Response verified: $method $path")
                        savedCall
                    } else {
                        call
                    }
                } else {
                    call
                }
            }
        }
    }

    single { WaselakApiClient(get()) }
    single { WorkerNetworkDataSource(get()) }
}
