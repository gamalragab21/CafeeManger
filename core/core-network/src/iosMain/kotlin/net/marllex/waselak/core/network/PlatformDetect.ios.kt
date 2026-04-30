package net.marllex.waselak.core.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

internal actual fun currentPlatformName(): String = "ios"

/**
 * iOS doesn't get app updates this way — they ship through the App Store.
 * The auto-update flow that calls downloadFile() is no-op on iOS:
 * we drain the channel (so the connection cleanly closes) and return null
 * to signal "downloads aren't supported on this platform".
 */
internal actual suspend fun saveDownloadToFile(
    filename: String,
    channel: ByteReadChannel,
    contentLength: Long,
    onProgress: (Float) -> Unit,
): String? {
    // Drain quickly so the HTTP connection gets released back to the pool.
    val sink = ByteArray(8192)
    while (!channel.isClosedForRead) {
        if (channel.readAvailable(sink) <= 0) break
    }
    onProgress(1f)
    return null
}
