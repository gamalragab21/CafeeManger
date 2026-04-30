package net.marllex.waselak.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * JVM-side classifier — checks Java's standard network-failure exception
 * types. Covers the cases ApiException used to inline.
 */
internal actual fun isPlatformNetworkError(t: Throwable): Boolean {
    return t is ConnectException ||
        t is UnknownHostException ||
        t is SocketTimeoutException ||
        t is IOException
}
