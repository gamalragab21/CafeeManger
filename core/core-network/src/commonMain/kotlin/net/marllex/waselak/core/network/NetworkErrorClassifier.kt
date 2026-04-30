package net.marllex.waselak.core.network

/**
 * Multiplatform network-error classifier. Replaces the JVM-only
 * `is java.net.ConnectException || is java.net.UnknownHostException || ...`
 * checks that used to live inline in [ApiException] and a few repositories.
 *
 * Each platform implements [isPlatformNetworkError] using its native
 * exception types (Java IOException + java.net.* on JVM, NSError-domain
 * lookups on iOS). The shared [Throwable.isNetworkError] extension
 * evaluates the chain (the immediate throwable plus its cause) so a
 * Ktor-wrapped network failure still classifies correctly.
 *
 * The fallback path is intentionally tolerant — when the platform check
 * doesn't recognise the type, we look at the message for the standard
 * connection-failure substrings. This catches Ktor's
 * `IOException("Failed to connect to ...")` strings without us needing
 * to depend on Ktor's exception class.
 */
internal expect fun isPlatformNetworkError(t: Throwable): Boolean

/**
 * `true` when this throwable looks like a transient network failure —
 * caller should treat it as "try again later" rather than a hard error
 * to surface to the user. Used by AuthInterceptor to decide whether to
 * mark the user as offline (vs forcing logout) and by repositories to
 * decide whether to fall back to cached data.
 */
fun Throwable.isNetworkError(): Boolean {
    if (isPlatformNetworkError(this)) return true
    cause?.let { if (isPlatformNetworkError(it)) return true }
    val msg = message?.lowercase() ?: cause?.message?.lowercase() ?: return false
    // Last-resort substring match. These are the messages Ktor and OkHttp
    // emit on every platform; keep the list small to avoid false positives
    // (we don't want a "could not parse" body to count as network error).
    return "unable to resolve host" in msg ||
        "failed to connect" in msg ||
        "connection refused" in msg ||
        "no internet" in msg ||
        "timeout" in msg ||
        "network is unreachable" in msg
}
