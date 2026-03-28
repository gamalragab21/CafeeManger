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
    private var appVersion: String = "unknown"

    fun initialize(
        dsn: String,
        appName: String,
        platform: String = "unknown",
        debug: Boolean = false,
        version: String = "unknown"
    ) {
        if (initialized) return
        if (dsn.isBlank()) return
        appVersion = version
        try {
            Sentry.init { options ->
                options.dsn = dsn
                options.environment = if (debug) "development" else "production"
                options.tracesSampleRate = 1.0
                options.beforeSend = { event ->
                    event.setTag("app", appName)
                    event.setTag("platform", platform)
                    event.setTag("app_platform", "$appName-$platform")
                    event.setTag("release", "$appName@$version")
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

    fun logInfo(tag: String, message: String) {
        if (!initialized) return
        try {
            val breadcrumb = Breadcrumb().apply {
                this.message = message
                this.category = tag
                this.level = SentryLevel.INFO
            }
            Sentry.addBreadcrumb(breadcrumb)
        } catch (_: Exception) { }
    }

    fun logWarning(tag: String, message: String) {
        if (!initialized) return
        try {
            val breadcrumb = Breadcrumb().apply {
                this.message = message
                this.category = tag
                this.level = SentryLevel.WARNING
            }
            Sentry.addBreadcrumb(breadcrumb)
        } catch (_: Exception) { }
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (!initialized) return
        try {
            val breadcrumb = Breadcrumb().apply {
                this.message = message
                this.category = tag
                this.level = SentryLevel.ERROR
            }
            Sentry.addBreadcrumb(breadcrumb)
            throwable?.let { Sentry.captureException(it) }
        } catch (_: Exception) { }
    }

    fun logNetwork(method: String, url: String, statusCode: Int, duration: Long) {
        if (!initialized) return
        try {
            val breadcrumb = Breadcrumb().apply {
                this.type = "http"
                this.category = "http"
                this.message = "$method $url"
                this.level = if (statusCode in 200..399) SentryLevel.INFO else SentryLevel.ERROR
                this.setData("method", method)
                this.setData("url", url)
                this.setData("status_code", statusCode.toString())
                this.setData("duration_ms", duration.toString())
            }
            Sentry.addBreadcrumb(breadcrumb)
        } catch (_: Exception) { }
    }

    fun logNavigation(from: String, to: String) {
        if (!initialized) return
        try {
            val breadcrumb = Breadcrumb().apply {
                this.type = "navigation"
                this.category = "navigation"
                this.message = "Navigated from $from to $to"
                this.level = SentryLevel.INFO
                this.setData("from", from)
                this.setData("to", to)
            }
            Sentry.addBreadcrumb(breadcrumb)
        } catch (_: Exception) { }
    }

    fun logUserAction(action: String, screen: String, data: Map<String, String> = emptyMap()) {
        if (!initialized) return
        try {
            val breadcrumb = Breadcrumb().apply {
                this.type = "user"
                this.category = "user.action"
                this.message = "$action on $screen"
                this.level = SentryLevel.INFO
                this.setData("action", action)
                this.setData("screen", screen)
                data.forEach { (key, value) -> this.setData(key, value) }
            }
            Sentry.addBreadcrumb(breadcrumb)
        } catch (_: Exception) { }
    }

    fun logTransaction(name: String, operation: String) {
        if (!initialized) return
        try {
            Sentry.startTransaction(name, operation)
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
