package net.marllex.waselak.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Same as the Android impl — JVM exception types. */
internal actual fun isPlatformNetworkError(t: Throwable): Boolean {
    return t is ConnectException ||
        t is UnknownHostException ||
        t is SocketTimeoutException ||
        t is IOException
}
