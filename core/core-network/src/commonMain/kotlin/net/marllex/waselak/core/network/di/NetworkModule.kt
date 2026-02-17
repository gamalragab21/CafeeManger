package net.marllex.waselak.core.network.di

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
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
            install(ContentNegotiation) {
                json(get<Json>())
            }
            install(Logging) {
                level = LogLevel.BODY
            }
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) {
                        val errorBody = try {
                            response.body<ApiErrorResponse>()
                        } catch (_: Exception) {
                            null
                        }
                        val message = errorBody?.message
                            ?: "HTTP ${response.status.value}: ${response.status.description}"
                        throw ApiException(
                            statusCode = response.status.value,
                            errorMessage = message,
                            errorType = errorBody?.error
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
                        val refreshToken = tokenProvider.getRefreshToken()
                        if (refreshToken != null) {
                            val newTokens = tokenProvider.refreshTokens(refreshToken)
                            if (newTokens != null) {
                                BearerTokens(newTokens.accessToken, newTokens.refreshToken)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                    sendWithoutRequest { request ->
                        // Skip auth header for public endpoints
                        val path = request.url.buildString()
                        !path.contains("auth/login") &&
                            !path.contains("auth/register") &&
                            !path.contains("auth/refresh")
                    }
                }
            }
            defaultRequest {
                url(get<String>(qualifier = named("baseUrl")))
                contentType(ContentType.Application.Json)
            }
        }.also { client ->
            // HMAC signing interceptor — HttpSend fires after ContentNegotiation
            // has serialized the body to OutgoingContent (TextContent for JSON)
            if (hmacSecret.isNotBlank()) {
                client.plugin(HttpSend).intercept { request ->
                    val method = request.method.value
                    val rawPath = request.url.encodedPath
                    // Ensure leading slash for consistency with server's call.request.path()
                    val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
                    val timestamp = kotlinx.datetime.Clock.System.now()
                        .toEpochMilliseconds().toString()
                    val nonce = Uuid.random().toString()

                    // Read the serialized body text from the rendered OutgoingContent
                    val bodyText = when (val body = request.body) {
                        is TextContent -> body.text
                        is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                        is OutgoingContent.NoContent -> ""
                        else -> ""
                    }
                    val bodyHash = HmacSigner.sha256(bodyText)

                    val payload = "$method\n$path\n$timestamp\n$nonce\n$bodyHash"
                    val signature = HmacSigner.hmacSha256(hmacSecret, payload)

                    // Remove old HMAC headers (prevents duplicates on Auth retry)
                    request.headers.remove("X-Timestamp")
                    request.headers.remove("X-Nonce")
                    request.headers.remove("X-Signature")

                    request.headers.append("X-Timestamp", timestamp)
                    request.headers.append("X-Nonce", nonce)
                    request.headers.append("X-Signature", signature)

                    execute(request)
                }
            }
        }
    }

    single { WaselakApiClient(get()) }
    single { WorkerNetworkDataSource(get()) }
}
