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
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
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
            // Install HMAC signing interceptor
            if (hmacSecret.isNotBlank()) {
                client.plugin(HttpSend).intercept { request ->
                    val method = request.method.value
                    val path = request.url.encodedPath
                    val timestamp = kotlinx.datetime.Clock.System.now()
                        .toEpochMilliseconds().toString()
                    val nonce = Uuid.random().toString()

                    // Hash the request body
                    val bodyBytes = request.body.let { body ->
                        if (body is io.ktor.client.request.forms.FormDataContent ||
                            body is io.ktor.http.content.OutgoingContent.ByteArrayContent
                        ) {
                            (body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes()
                        } else if (body is io.ktor.http.content.OutgoingContent.ReadChannelContent) {
                            val channel = body.readFrom()
                            channel.readRemaining().readByteArray()
                        } else {
                            byteArrayOf()
                        }
                    }
                    val bodyHash = if (bodyBytes.isEmpty()) {
                        HmacSigner.sha256("")
                    } else {
                        HmacSigner.sha256(bodyBytes.decodeToString())
                    }

                    val payload = "$method\n$path\n$timestamp\n$nonce\n$bodyHash"
                    val signature = HmacSigner.hmacSha256(hmacSecret, payload)

                    request.header("X-Timestamp", timestamp)
                    request.header("X-Nonce", nonce)
                    request.header("X-Signature", signature)

                    execute(request)
                }
            }
        }
    }

    single { WaselakApiClient(get()) }
    single { WorkerNetworkDataSource(get()) }
}
