package net.marllex.cafeemanger.core.auth

import android.content.SharedPreferences
import com.auth0.android.jwt.JWT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val encryptedPrefs: SharedPreferences
) {
    private val _isLoggedIn = MutableStateFlow(getAccessToken() != null)
    val isLoggedIn: Flow<Boolean> = _isLoggedIn.asStateFlow()

    fun getAccessToken(): String? =
        encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? =
        encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
        _isLoggedIn.value = true
    }

    fun clearTokens() {
        encryptedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_VENDOR_ID)
            .remove(KEY_USER_ROLE)
            .apply()
        _isLoggedIn.value = false
    }

    fun isAccessTokenExpired(): Boolean {
        val token = getAccessToken() ?: return true
        return try {
            val jwt = JWT(token)
            jwt.isExpired(10) // 10-second leeway
        } catch (e: Exception) {
            true
        }
    }

    fun getUserIdFromToken(): String? {
        val token = getAccessToken() ?: return null
        return try {
            JWT(token).subject
        } catch (e: Exception) {
            null
        }
    }

    fun getVendorIdFromToken(): String? {
        val token = getAccessToken() ?: return null
        return try {
            JWT(token).getClaim("vendor_id").asString()
        } catch (e: Exception) {
            null
        }
    }

    fun getRoleFromToken(): String? {
        val token = getAccessToken() ?: return null
        return try {
            JWT(token).getClaim("role").asString()
        } catch (e: Exception) {
            null
        }
    }

    // Cache user info locally for quick access
    fun saveUserInfo(userId: String, vendorId: String, role: String) {
        encryptedPrefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_VENDOR_ID, vendorId)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun getCachedUserId(): String? = encryptedPrefs.getString(KEY_USER_ID, null)
    fun getCachedVendorId(): String? = encryptedPrefs.getString(KEY_VENDOR_ID, null)
    fun getCachedUserRole(): String? = encryptedPrefs.getString(KEY_USER_ROLE, null)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_VENDOR_ID = "vendor_id"
        private const val KEY_USER_ROLE = "user_role"
    }
}
