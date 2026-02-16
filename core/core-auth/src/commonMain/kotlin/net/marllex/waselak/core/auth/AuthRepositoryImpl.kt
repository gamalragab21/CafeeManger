package net.marllex.waselak.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.model.User
import net.marllex.waselak.core.model.UserRole
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.LoginRequest
import net.marllex.waselak.core.network.dto.RefreshTokenRequest
import net.marllex.waselak.core.network.mapper.toDomain

class AuthRepositoryImpl(
    private val api: WaselakApiClient,
    private val tokenManager: TokenManager,
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser.asStateFlow()

    override val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn

    init {
        restoreUserFromCache()
    }

    private fun restoreUserFromCache() {
        kotlinx.coroutines.runBlocking {
            val userId = tokenManager.getCachedUserId() ?: return@runBlocking
            val vendorId = tokenManager.getCachedVendorId() ?: return@runBlocking
            val roleName = tokenManager.getCachedUserRole() ?: return@runBlocking
            val name = tokenManager.getCachedUserName() ?: return@runBlocking
            val phone = tokenManager.getCachedUserPhone() ?: return@runBlocking
            val email = tokenManager.getCachedUserEmail()

            val role = try {
                UserRole.valueOf(roleName)
            } catch (_: Exception) {
                return@runBlocking
            }

            _currentUser.value = User(
                id = userId,
                vendorId = vendorId,
                role = role,
                name = name,
                phone = phone,
                email = email,
            )
        }
    }

    override suspend fun login(phone: String, password: String, appType: String?): Result<User> = runCatching {
        val response = api.login(LoginRequest(phone, password, appType))
        tokenManager.saveTokens(response.accessToken, response.refreshToken)

        val user = response.user.toDomain()
        tokenManager.saveUserInfo(user.id, user.vendorId, user.role.name, user.name, user.phone, user.email)
        _currentUser.value = user
        user
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        try {
            api.logout()
        } catch (_: Exception) {
            // Best effort - clear local state regardless
        }
        tokenManager.clearTokens()
        _currentUser.value = null
    }

    override suspend fun refreshToken(): Result<Unit> = runCatching {
        val refreshToken = tokenManager.getRefreshToken()
            ?: throw IllegalStateException("No refresh token available")
        val response = api.refreshToken(RefreshTokenRequest(refreshToken))
        tokenManager.saveTokens(response.accessToken, response.refreshToken)

        val user = response.user.toDomain()
        tokenManager.saveUserInfo(user.id, user.vendorId, user.role.name, user.name, user.phone, user.email)
        _currentUser.value = user
    }

    override fun getCurrentUserId(): String? =
        kotlinx.coroutines.runBlocking { tokenManager.getCachedUserId() ?: tokenManager.getUserIdFromToken() }

    override fun getCurrentVendorId(): String? =
        kotlinx.coroutines.runBlocking { tokenManager.getCachedVendorId() ?: tokenManager.getVendorIdFromToken() }
}
