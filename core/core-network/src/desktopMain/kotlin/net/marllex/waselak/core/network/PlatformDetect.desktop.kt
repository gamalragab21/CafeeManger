package net.marllex.waselak.core.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File

internal actual fun currentPlatformName(): String {
    val osName = System.getProperty("os.name")?.lowercase() ?: ""
    return when {
        osName.contains("win") -> "windows"
        osName.contains("mac") -> "macos"
        osName.contains("linux") && !osName.contains("android") -> "linux"
        else -> "linux"
    }
}

internal actual suspend fun saveDownloadToFile(
    filename: String,
    channel: ByteReadChannel,
    contentLength: Long,
    onProgress: (Float) -> Unit,
): String? {
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
