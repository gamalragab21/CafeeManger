package net.marllex.waselak.admin.session

import java.util.prefs.Preferences

class DesktopAdminSessionManager : AdminSessionManager {

    private val prefs = Preferences.userRoot().node("waselak-admin")

    override fun saveToken(token: String) {
        prefs.put(KEY_JWT_TOKEN, token)
        prefs.flush()
    }

    override fun getToken(): String? {
        return prefs.get(KEY_JWT_TOKEN, null)
    }

    override fun saveRefreshToken(token: String) {
        prefs.put(KEY_REFRESH_TOKEN, token)
        prefs.flush()
    }

    override fun getRefreshToken(): String? {
        return prefs.get(KEY_REFRESH_TOKEN, null)
    }

    override fun clearToken() {
        prefs.remove(KEY_JWT_TOKEN)
        prefs.remove(KEY_REFRESH_TOKEN)
        prefs.flush()
    }

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
