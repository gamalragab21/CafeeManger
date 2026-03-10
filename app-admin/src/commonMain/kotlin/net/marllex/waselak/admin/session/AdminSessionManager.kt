package net.marllex.waselak.admin.session

/**
 * Persists admin JWT tokens across app restarts.
 * Platform-specific implementations handle the actual storage mechanism.
 */
interface AdminSessionManager {
    fun saveToken(token: String)
    fun getToken(): String?
    fun saveRefreshToken(token: String)
    fun getRefreshToken(): String?
    fun clearToken()
}
