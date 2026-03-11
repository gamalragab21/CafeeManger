package net.marllex.waselak.core.common.crash

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User

actual object CrashReporter {
    private var initialized: Boolean = false

    actual fun initialize(dsn: String, appName: String, debug: Boolean) {
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

    actual fun captureException(throwable: Throwable) {
        if (!initialized) return
        try { Sentry.captureException(throwable) } catch (_: Exception) { }
    }

    actual fun captureMessage(message: String) {
        if (!initialized) return
        try { Sentry.captureMessage(message) } catch (_: Exception) { }
    }

    actual fun setUser(userId: String?, username: String?, role: String?, vendorId: String?) {
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

    actual fun clearUser() {
        if (!initialized) return
        try { Sentry.setUser(null) } catch (_: Exception) { }
    }

    actual fun addBreadcrumb(message: String, category: String) {
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

    actual fun setTag(key: String, value: String) {
        if (!initialized) return
        try { Sentry.configureScope { scope -> scope.setTag(key, value) } } catch (_: Exception) { }
    }

    actual fun setExtra(key: String, value: String) {
        if (!initialized) return
        try { Sentry.configureScope { scope -> scope.setExtra(key, value) } } catch (_: Exception) { }
    }
}
