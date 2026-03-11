package net.marllex.waselak.admin.session

import kotlinx.browser.localStorage

class WasmJsAdminSessionManager : AdminSessionManager {
    override fun saveToken(token: String) {
        localStorage.setItem("waselak_admin_jwt", token)
    }

    override fun getToken(): String? =
        localStorage.getItem("waselak_admin_jwt")

    override fun saveRefreshToken(token: String) {
        localStorage.setItem("waselak_admin_refresh", token)
    }

    override fun getRefreshToken(): String? =
        localStorage.getItem("waselak_admin_refresh")

    override fun clearToken() {
        localStorage.removeItem("waselak_admin_jwt")
        localStorage.removeItem("waselak_admin_refresh")
    }
}
