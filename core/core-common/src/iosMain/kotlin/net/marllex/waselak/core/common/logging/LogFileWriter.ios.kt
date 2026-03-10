package net.marllex.waselak.core.common.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual object LogFileWriter {
    actual fun getLogFilePath(appName: String): String {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null
        )!!
        val logsDir = "${directory.path}/logs"
        NSFileManager.defaultManager.createDirectoryAtPath(
            logsDir, withIntermediateDirectories = true, attributes = null, error = null
        )
        return "$logsDir/waselak_$appName.log"
    }

    actual fun appendLine(path: String, line: String) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createFileAtPath(path, contents = null, attributes = null)
        }
        val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: return
        handle.seekToEndOfFile()
        val data = NSString.create(string = "$line\n").dataUsingEncoding(NSUTF8StringEncoding) ?: return
        handle.writeData(data)
        handle.closeFile()
    }

    actual fun readBytes(path: String): ByteArray {
        val data: NSData = NSFileManager.defaultManager.contentsAtPath(path) ?: return ByteArray(0)
        val size = data.length.toInt()
        if (size == 0) return ByteArray(0)
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return bytes
    }

    actual fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    actual fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun lastModified(path: String): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
            ?: return 0L
        val date = attrs[NSFileModificationDate] as? platform.Foundation.NSDate ?: return 0L
        return (date.timeIntervalSince1970 * 1000).toLong()
    }
}
