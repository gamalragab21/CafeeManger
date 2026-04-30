package net.marllex.waselak.core.network

/**
 * On iOS, Kotlin/Native doesn't surface URLSession failures as native
 * `Throwable` subtypes the way JVM does — Ktor wraps them in IOException
 * lookalikes whose class names vary across versions. So we don't try to
 * match exception types at all here; the shared classifier falls through
 * to its message-substring check, which catches "Failed to connect…",
 * "timeout", "Unable to resolve host" etc. — the same strings Ktor's
 * Darwin engine emits.
 */
internal actual fun isPlatformNetworkError(t: Throwable): Boolean = false
