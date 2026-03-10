package net.marllex.waselak.admin.session

import android.content.Context

class AndroidAdminSessionManager(context: Context) : AdminSessionManager {

    private val prefs = context.getSharedPreferences("admin_session", Context.MODE_PRIVATE)

    override fun saveToken(token: String) {
        prefs.edit().putString(KEY_JWT_TOKEN, token).apply()
    }

    override fun getToken(): String? {
        return prefs.getString(KEY_JWT_TOKEN, null)
    }

    override fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    override fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    override fun clearToken() {
        prefs.edit().remove(KEY_JWT_TOKEN).remove(KEY_REFRESH_TOKEN).apply()
    }

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
