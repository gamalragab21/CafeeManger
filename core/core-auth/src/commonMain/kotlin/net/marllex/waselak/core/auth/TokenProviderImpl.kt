package net.marllex.waselak.core.auth

import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.auth.TokenPair
import net.marllex.waselak.core.network.auth.TokenProvider
import net.marllex.waselak.core.network.dto.RefreshTokenRequest

class TokenProviderImpl(
    private val tokenManager: TokenManager,
    private val api: WaselakApiClient,
) : TokenProvider {

    override suspend fun getAccessToken(): String? =
        tokenManager.getAccessToken()

    override suspend fun getRefreshToken(): String? =
        tokenManager.getRefreshToken()

    override suspend fun refreshTokens(refreshToken: String): TokenPair? {
        return try {
            val response = api.refreshToken(RefreshTokenRequest(refreshToken))
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
            TokenPair(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken
            )
        } catch (_: Exception) {
            tokenManager.clearTokens()
            null
        }
    }
}
