package net.marllex.waselak.core.common.crash

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User

/**
 * Centralized crash reporting wrapper.
 * Initialize once per app entry point with the DSN and app name.
 *
 * Usage:
 *   CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "manager")
 *   CrashReporter.captureException(exception)
 */
object CrashReporter {
    private var initialized: Boolean = false

    fun initialize(dsn: String, appName: String, debug: Boolean = false) {
        if (initialized) return
        if (dsn.isBlank()) return
        try {
            Sentry.init { options ->
                options.dsn = dsn
                options.environment = if (debug) "development" else "production"
                options.beforeSend = { event ->
                    event.setTag("app", appName)
                    event
                }
            }
            initialized = true
        } catch (_: Exception) { }
    }

    fun captureException(throwable: Throwable) {
        if (!initialized) return
        try { Sentry.captureException(throwable) } catch (_: Exception) { }
    }

    fun captureMessage(message: String) {
        if (!initialized) return
        try { Sentry.captureMessage(message) } catch (_: Exception) { }
    }

    fun setUser(userId: String?, username: String? = null, role: String? = null, vendorId: String? = null) {
        if (!initialized) return
        try {
            if (userId == null) { Sentry.setUser(null); return }
            val user = User().apply { id = userId; this.username = username }
            Sentry.setUser(user)
            Sentry.configureScope { scope ->
                role?.let { scope.setTag("user.role", it) }
                vendorId?.let { scope.setTag("vendor.id", it) }
            }
        } catch (_: Exception) { }
    }

    fun clearUser() {
        if (!initialized) return
        try { Sentry.setUser(null) } catch (_: Exception) { }
    }

    fun addBreadcrumb(message: String, category: String = "navigation") {
        if (!initialized) return
        try {
            val breadcrumb = Breadcrumb().apply {
                this.message = message
                this.category = category
                this.level = SentryLevel.INFO
            }
            Sentry.addBreadcrumb(breadcrumb)
        } catch (_: Exception) { }
    }

    fun setTag(key: String, value: String) {
        if (!initialized) return
        try { Sentry.configureScope { scope -> scope.setTag(key, value) } } catch (_: Exception) { }
    }

    fun setExtra(key: String, value: String) {
        if (!initialized) return
        try { Sentry.configureScope { scope -> scope.setExtra(key, value) } } catch (_: Exception) { }
    }
}
