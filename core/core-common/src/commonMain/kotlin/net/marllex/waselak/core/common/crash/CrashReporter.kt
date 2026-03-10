package net.marllex.waselak.core.common.crash

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User

/**
 * Centralized crash reporting wrapper around Sentry.
 * Initialize once per app entry point with the DSN and app name.
 *
 * Usage:
 *   CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "manager")
 *   CrashReporter.captureException(exception)
 */
object CrashReporter {
    private var initialized: Boolean = false

    /**
     * Initialize Sentry crash reporting.
     * Call this early in the app lifecycle (Application.onCreate or main()).
     *
     * @param dsn The Sentry DSN (Data Source Name)
     * @param appName The app identifier (manager, cashier, delivery, admin)
     * @param debug Enable debug mode for development (default: false)
     */
    fun initialize(dsn: String, appName: String, debug: Boolean = false) {
        if (initialized) return
        if (dsn.isBlank()) return // Skip if DSN not configured

        try {
            Sentry.init { options ->
                options.dsn = dsn
                options.environment = if (debug) "development" else "production"
                options.beforeSend = { event ->
                    // Tag every event with the app name
                    event.setTag("app", appName)
                    event
                }
            }
            initialized = true
        } catch (_: Exception) {
            // Never let crash reporting initialization crash the app
        }
    }

    /**
     * Report an exception to Sentry.
     */
    fun captureException(throwable: Throwable) {
        if (!initialized) return
        try {
            Sentry.captureException(throwable)
        } catch (_: Exception) {
            // Silently ignore
        }
    }

    /**
     * Send a text message to Sentry.
     */
    fun captureMessage(message: String) {
        if (!initialized) return
        try {
            Sentry.captureMessage(message)
        } catch (_: Exception) {
            // Silently ignore
        }
    }

    /**
     * Set the current user context (vendor worker info).
     *
     * @param userId The worker/user ID
     * @param username The worker name
     * @param role The worker role (MANAGER, CASHIER, DELIVERY, ADMIN)
     * @param vendorId Optional vendor ID
     */
    fun setUser(userId: String?, username: String? = null, role: String? = null, vendorId: String? = null) {
        if (!initialized) return
        try {
            if (userId == null) {
                Sentry.setUser(null)
                return
            }
            val user = User().apply {
                id = userId
                this.username = username
            }
            Sentry.setUser(user)
            Sentry.configureScope { scope ->
                role?.let { scope.setTag("user.role", it) }
                vendorId?.let { scope.setTag("vendor.id", it) }
            }
        } catch (_: Exception) {
            // Silently ignore
        }
    }

    /**
     * Clear user context (e.g., on sign out).
     */
    fun clearUser() {
        if (!initialized) return
        try {
            Sentry.setUser(null)
        } catch (_: Exception) {
            // Silently ignore
        }
    }

    /**
     * Add a navigation breadcrumb (screen transitions, user actions).
     */
    fun addBreadcrumb(message: String, category: String = "navigation") {
        if (!initialized) return
        try {
            val breadcrumb = Breadcrumb().apply {
                this.message = message
                this.category = category
                this.level = SentryLevel.INFO
            }
            Sentry.addBreadcrumb(breadcrumb)
        } catch (_: Exception) {
            // Silently ignore
        }
    }

    /**
     * Set a custom tag on all future events.
     */
    fun setTag(key: String, value: String) {
        if (!initialized) return
        try {
            Sentry.configureScope { scope ->
                scope.setTag(key, value)
            }
        } catch (_: Exception) {
            // Silently ignore
        }
    }

    /**
     * Set extra context data on all future events.
     */
    fun setExtra(key: String, value: String) {
        if (!initialized) return
        try {
            Sentry.configureScope { scope ->
                scope.setExtra(key, value)
            }
        } catch (_: Exception) {
            // Silently ignore
        }
    }
}
