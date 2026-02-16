package net.marllex.waselak.core.network.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.auth.TokenProvider
import net.marllex.waselak.core.network.datasource.WorkerNetworkDataSource
import org.koin.core.qualifier.named
import org.koin.dsl.module

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

        HttpClient(get<HttpClientEngineFactory<*>>()) {
            install(ContentNegotiation) {
                json(get<Json>())
            }
            install(Logging) {
                level = LogLevel.BODY
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
        }
    }

    single { WaselakApiClient(get()) }
    single { WorkerNetworkDataSource(get()) }
}
