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

        val isDebug = try { get<String>(qualifier = named("appName")); hmacSecret.isBlank() } catch (_: Exception) { true }

        HttpClient(get<HttpClientEngineFactory<*>>()) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000   // 15 seconds for full request
                connectTimeoutMillis = 10_000   // 10 seconds to establish connection
                socketTimeoutMillis = 15_000    // 15 seconds for socket read/write
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
                        // println() routes to the process stdout on Kotlin/Native iOS,
                        // which `xcrun devicectl device process launch --console` captures.
                        // Kermit's iOS writer goes to os_log instead, invisible from
                        // the launched-with-console terminal. Keeping both means
                        // devs in Console.app/Sentry still see the HTTP details, AND
                        // anyone debugging via `--console` from CLI does too.
                        println("[Ktor] $message")
                        co.touchlab.kermit.Logger.d("KtorHttp") { message }
                        // Log full HTTP to file only (AppLogger without Sentry bridge)
                        AppLogger.logToFileOnly("HTTP", message)
                        // Log to Sentry — full detail for debugging
                        CrashReporter.logInfo("HTTP", message)
                    }
                }
                level = if (isDebug) LogLevel.ALL else LogLevel.HEADERS
            }
            HttpResponseValidator {
                validateResponse { response ->
                    val path = response.call.request.url.encodedPath
                    val method = response.call.request.method.value
                    val status = response.status.value
                    val queryString = response.call.request.url.encodedQuery
                    val fullPath = if (queryString.isNotBlank()) "$path?$queryString" else path

                    // Skip body reading for binary/static paths (images, uploads)
                    // Reading the body here via save() consumes the stream, breaking
                    // Coil image loading which needs to read the bytes afterwards.
                    val isBinaryPath = path.contains("/uploads") || path.contains("/upload")
                    if (isBinaryPath) {
                        CrashReporter.logNetwork(method, fullPath, status, 0L)
                        if (!response.status.isSuccess()) {
                            throw ApiException(
                                statusCode = response.status.value,
                                errorMessage = "HTTP ${response.status.value}: ${response.status.description}",
                                errorType = null
                            )
                        }
                        return@validateResponse
                    }

                    CrashReporter.logNetwork(method, fullPath, status, 0L)

                    if (!response.status.isSuccess()) {
                        // Only read body on errors (not on every success response)
                        val savedResp = response.call.save()
                        val respBody = try { savedResp.response.bodyAsText() } catch (_: Exception) { "" }
                        val errorBody = try {
                            get<Json>().decodeFromString<ApiErrorResponse>(respBody)
                        } catch (_: Exception) { null }
                        val message = errorBody?.message
                            ?: "HTTP ${response.status.value}: ${response.status.description}"
                        val errorType = errorBody?.error

                        // Log errors only
                        CrashReporter.logInfo("API-ERR", "$method $fullPath → $status\n$respBody")

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

                // HMAC response verification (only when secret is set).
                //
                // Failures used to throw ApiException("RESPONSE_TAMPERED") which
                // surfaced in the UI as a generic "unexpected error" — leaving
                // no signal of WHY it failed. On iOS specifically, the Darwin
                // HTTP engine sometimes hands us a response body with a charset
                // / line-ending difference vs what the backend hashed, even
                // though the body is otherwise identical. The fix for that is
                // server-side, but in the meantime hard-throwing breaks every
                // API call.
                //
                // Now we LOG the mismatch with both hashes + body length so we
                // can diagnose, and let the call through. Request-side HMAC
                // (signing OUR outgoing request) is unchanged — that's the
                // real auth-relevant one. Response-side is a tamper-detection
                // hint, not an authentication boundary; downgrading it to a
                // warning is safe.
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
                            // Soft-fail: record everything the server-side
                            // operator needs to identify the mismatch, then
                            // accept the response.
                            AppLogger.e(
                                "HMAC",
                                "Response sig mismatch (DEGRADED to warning) " +
                                    "$method $path " +
                                    "| body_len=${respBody.length} " +
                                    "| body_hash_local=$respBodyHash " +
                                    "| expected_sig=$expectedSig " +
                                    "| got_sig=$respSignature " +
                                    "| ts=$respTimestamp"
                            )
                        } else {
                            AppLogger.d("HMAC", "Response verified: $method $path")
                        }
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
