package net.marllex.waselak.core.common.crash

actual object CrashReporter {
    actual fun initialize(dsn: String, appName: String, debug: Boolean) {
        // No-op on wasmJs — Sentry KMP does not support browser target
    }

    actual fun captureException(throwable: Throwable) {
        println("CrashReporter ERROR: ${throwable.message}")
    }

    actual fun captureMessage(message: String) {
        println("CrashReporter: $message")
    }

    actual fun setUser(userId: String?, username: String?, role: String?, vendorId: String?) { }
    actual fun clearUser() { }
    actual fun addBreadcrumb(message: String, category: String) { }
    actual fun setTag(key: String, value: String) { }
    actual fun setExtra(key: String, value: String) { }
}
