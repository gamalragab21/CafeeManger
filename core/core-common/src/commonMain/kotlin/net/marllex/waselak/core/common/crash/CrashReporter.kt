package net.marllex.waselak.core.common.crash

/**
 * Centralized crash reporting wrapper.
 * Initialize once per app entry point with the DSN and app name.
 *
 * Usage:
 *   CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "manager")
 *   CrashReporter.captureException(exception)
 */
expect object CrashReporter {
    fun initialize(dsn: String, appName: String, debug: Boolean = false)
    fun captureException(throwable: Throwable)
    fun captureMessage(message: String)
    fun setUser(userId: String?, username: String? = null, role: String? = null, vendorId: String? = null)
    fun clearUser()
    fun addBreadcrumb(message: String, category: String = "navigation")
    fun setTag(key: String, value: String)
    fun setExtra(key: String, value: String)
}
