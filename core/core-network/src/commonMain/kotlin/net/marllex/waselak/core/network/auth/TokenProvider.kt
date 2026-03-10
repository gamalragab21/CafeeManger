package net.marllex.waselak.core.network.auth

/**
 * Abstraction for providing authentication tokens to the HTTP client.
 * Implemented by core-auth module to avoid circular dependencies.
 */
interface TokenProvider {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun refreshTokens(refreshToken: String): TokenPair?
    /** Force-clear all tokens (e.g. when vendor account is suspended). */
    suspend fun clearTokens()
}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String
)
