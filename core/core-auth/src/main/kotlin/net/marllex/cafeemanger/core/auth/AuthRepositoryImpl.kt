package net.marllex.cafeemanger.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.model.User
import net.marllex.cafeemanger.core.model.UserRole
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.LoginRequest
import net.marllex.cafeemanger.core.network.dto.RefreshTokenRequest
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
    private val tokenManager: TokenManager,
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser.asStateFlow()

    override val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn

    init {
        // Restore user from cache on app restart (so welcome message shows immediately)
        restoreUserFromCache()
    }

    private fun restoreUserFromCache() {
        val userId = tokenManager.getCachedUserId() ?: return
        val vendorId = tokenManager.getCachedVendorId() ?: return
        val roleName = tokenManager.getCachedUserRole() ?: return
        val name = tokenManager.getCachedUserName() ?: return
        val phone = tokenManager.getCachedUserPhone() ?: return
        val email = tokenManager.getCachedUserEmail()

        val role = try { UserRole.valueOf(roleName) } catch (_: Exception) { return }

        _currentUser.value = User(
            id = userId,
            vendorId = vendorId,
            role = role,
            name = name,
            phone = phone,
            email = email,
        )
    }

    override suspend fun login(phone: String, password: String): Result<User> = runCatching {
        val response = api.login(LoginRequest(phone, password))
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

        // Update cached user info from refresh response
        val user = response.user.toDomain()
        tokenManager.saveUserInfo(user.id, user.vendorId, user.role.name, user.name, user.phone, user.email)
        _currentUser.value = user
    }

    override fun getCurrentUserId(): String? =
        tokenManager.getCachedUserId() ?: tokenManager.getUserIdFromToken()

    override fun getCurrentVendorId(): String? =
        tokenManager.getCachedVendorId() ?: tokenManager.getVendorIdFromToken()
}
