package net.marllex.waselak.core.network

import io.ktor.utils.io.ByteReadChannel

/**
 * Returns "android", "ios", "macos", "windows", or "linux".
 * Used by WaselakApiClient to label app-update polls so the server can
 * return the right binary.
 */
internal expect fun currentPlatformName(): String

/**
 * Saves a streamed download to local storage. Returns the absolute path of
 * the saved file, or null on platforms that don't expose user-writable
 * storage at this layer (iOS — App Store handles updates there).
 *
 * Gets called from WaselakApiClient.downloadFile. The progress callback
 * receives values in [0f, 1f]; implementations should fire it at least once
 * with 1f when the download completes.
 */
internal expect suspend fun saveDownloadToFile(
    filename: String,
    channel: ByteReadChannel,
    contentLength: Long,
    onProgress: (Float) -> Unit,
): String?
