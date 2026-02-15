package net.marllex.waselak.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class TokenManager(
    private val dataStore: DataStore<Preferences>
) {
    private val _isLoggedIn = MutableStateFlow(
        runBlocking { getAccessToken() != null }
    )
    val isLoggedIn: Flow<Boolean> = _isLoggedIn.asStateFlow()

    suspend fun getAccessToken(): String? =
        dataStore.data.first()[KEY_ACCESS_TOKEN]

    fun getAccessTokenBlocking(): String? =
        runBlocking { getAccessToken() }

    suspend fun getRefreshToken(): String? =
        dataStore.data.first()[KEY_REFRESH_TOKEN]

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
        _isLoggedIn.value = true
    }

    suspend fun clearTokens() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_VENDOR_ID)
            prefs.remove(KEY_USER_ROLE)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_PHONE)
            prefs.remove(KEY_USER_EMAIL)
        }
        _isLoggedIn.value = false
    }

    suspend fun isAccessTokenExpired(): Boolean {
        val token = getAccessToken() ?: return true
        return JwtParser.isExpired(token)
    }

    suspend fun getUserIdFromToken(): String? {
        val token = getAccessToken() ?: return null
        return JwtParser.getSubject(token)
    }

    suspend fun getVendorIdFromToken(): String? {
        val token = getAccessToken() ?: return null
        return JwtParser.getClaim(token, "vendor_id")
    }

    suspend fun getRoleFromToken(): String? {
        val token = getAccessToken() ?: return null
        return JwtParser.getClaim(token, "role")
    }

    suspend fun saveUserInfo(
        userId: String,
        vendorId: String,
        role: String,
        name: String,
        phone: String,
        email: String? = null
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_VENDOR_ID] = vendorId
            prefs[KEY_USER_ROLE] = role
            prefs[KEY_USER_NAME] = name
            prefs[KEY_USER_PHONE] = phone
            email?.let { prefs[KEY_USER_EMAIL] = it }
        }
    }

    suspend fun getCachedUserId(): String? = dataStore.data.first()[KEY_USER_ID]
    suspend fun getCachedVendorId(): String? = dataStore.data.first()[KEY_VENDOR_ID]
    suspend fun getCachedUserRole(): String? = dataStore.data.first()[KEY_USER_ROLE]
    suspend fun getCachedUserName(): String? = dataStore.data.first()[KEY_USER_NAME]
    suspend fun getCachedUserPhone(): String? = dataStore.data.first()[KEY_USER_PHONE]
    suspend fun getCachedUserEmail(): String? = dataStore.data.first()[KEY_USER_EMAIL]

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_VENDOR_ID = stringPreferencesKey("vendor_id")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_PHONE = stringPreferencesKey("user_phone")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
    }
}
