package net.marllex.waselak.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.model.User
import net.marllex.waselak.core.model.UserRole
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter
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
            val photoUrl = tokenManager.getCachedUserPhotoUrl()

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
                photoUrl = photoUrl,
            )
        }
    }

    override suspend fun login(phone: String, password: String, appType: String?): Result<User> {
        AppLogger.d("Auth", "Login attempt for phone=$phone, appType=$appType")
        return runCatching {
            val response = api.login(LoginRequest(phone, password, appType))
            tokenManager.saveTokens(response.accessToken, response.refreshToken)

            val user = response.user.toDomain()
            tokenManager.saveUserInfo(user.id, user.vendorId, user.role.name, user.name, user.phone, user.email, user.photoUrl)
            _currentUser.value = user
            AppLogger.i("Auth", "Login successful: userId=${user.id}, role=${user.role}")
            user
        }.onFailure { e ->
            AppLogger.e("Auth", "Login failed for phone=$phone", e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        AppLogger.i("Auth", "Logout initiated")
        return runCatching {
            try {
                api.logout()
                AppLogger.i("Auth", "Logout API call succeeded")
            } catch (e: Exception) {
                AppLogger.w("Auth", "Logout API call failed (best effort): ${e.message}")
            }
            tokenManager.clearTokens()
            _currentUser.value = null
            CrashReporter.clearUser()
            AppLogger.i("Auth", "Logout completed, tokens cleared")
        }.onFailure { e ->
            CrashReporter.captureException(e)
            AppLogger.e("Auth", "Logout failed", e)
        }
    }

    override suspend fun refreshToken(): Result<Unit> {
        AppLogger.d("Auth", "Token refresh started")
        return runCatching {
            val refreshToken = tokenManager.getRefreshToken()
                ?: throw IllegalStateException("No refresh token available")
            val response = api.refreshToken(RefreshTokenRequest(refreshToken))
            tokenManager.saveTokens(response.accessToken, response.refreshToken)

            val user = response.user.toDomain()
            tokenManager.saveUserInfo(user.id, user.vendorId, user.role.name, user.name, user.phone, user.email, user.photoUrl)
            _currentUser.value = user
            AppLogger.i("Auth", "Token refreshed successfully for userId=${user.id}")
        }.onFailure { e ->
            AppLogger.e("Auth", "Token refresh failed", e)
        }
    }

    override fun getCurrentUserId(): String? =
        kotlinx.coroutines.runBlocking { tokenManager.getCachedUserId() ?: tokenManager.getUserIdFromToken() }

    override fun getCurrentVendorId(): String? =
        kotlinx.coroutines.runBlocking { tokenManager.getCachedVendorId() ?: tokenManager.getVendorIdFromToken() }
}
