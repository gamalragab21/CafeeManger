package net.marllex.waselak.core.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File

internal actual fun currentPlatformName(): String = "android"

internal actual suspend fun saveDownloadToFile(
    filename: String,
    channel: ByteReadChannel,
    contentLength: Long,
    onProgress: (Float) -> Unit,
): String? {
    // Android writes to the app's external Downloads — but we don't have a
    // Context here. The auto-update flow that calls this only runs on
    // desktop/admin builds; on phones we route through the OS app store
    // anyway. Falling back to System.getProperty("user.home") makes this
    // work on JVM and stay safe (returns null) on Android phones where
    // user.home points at /data which the app can't actually write to.
    val home = System.getProperty("user.home") ?: return null
    val dir = File(home, "Downloads")
    if (!dir.exists() && !dir.mkdirs()) return null
    val file = File(dir, filename)
    var totalBytesRead = 0L
    file.outputStream().use { output ->
        val buffer = ByteArray(8192)
        while (!channel.isClosedForRead) {
            val bytesRead = channel.readAvailable(buffer)
            if (bytesRead <= 0) break
            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            if (contentLength > 0) {
                onProgress(totalBytesRead.toFloat() / contentLength.toFloat())
            }
        }
    }
    onProgress(1f)
    return file.absolutePath
}
