package net.marllex.cafeemanger.core.auth

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.RefreshTokenRequest
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    private val mutex = Mutex()

    // Lazy provider for API to avoid circular dependency
    @Volatile
    var apiProvider: (() -> CafeeMangerApi)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for login/refresh endpoints
        val path = originalRequest.url.encodedPath
        if (path.contains("/auth/login") || path.contains("/auth/refresh")) {
            return chain.proceed(originalRequest)
        }

        val accessToken = tokenManager.getAccessToken()
            ?: return chain.proceed(originalRequest)

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

        val response = chain.proceed(authenticatedRequest)

        // Handle 401 - Token expired
        if (response.code == 401) {
            response.close()

            return runBlocking {
                mutex.withLock {
                    // Double-check if token was already refreshed by another thread
                    if (tokenManager.isAccessTokenExpired()) {
                        attemptTokenRefresh()
                    }

                    val newToken = tokenManager.getAccessToken()
                    if (newToken != null && !tokenManager.isAccessTokenExpired()) {
                        chain.proceed(
                            originalRequest.newBuilder()
                                .header("Authorization", "Bearer $newToken")
                                .build()
                        )
                    } else {
                        // Token refresh failed, clear and return original 401
                        tokenManager.clearTokens()
                        chain.proceed(originalRequest)
                    }
                }
            }
        }

        return response
    }

    private suspend fun attemptTokenRefresh() {
        val refreshToken = tokenManager.getRefreshToken() ?: return
        val api = apiProvider?.invoke() ?: return
        try {
            val response = api.refreshToken(RefreshTokenRequest(refreshToken))
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
        } catch (e: Exception) {
            tokenManager.clearTokens()
        }
    }
}
