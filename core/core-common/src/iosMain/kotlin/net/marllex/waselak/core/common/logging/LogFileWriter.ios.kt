package net.marllex.waselak.core.common.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.appendData
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
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
        // Read-modify-write append. The previous NSFileHandle-based version
        // hit Kotlin/Native binding churn — `seekToEndOfFile` / `closeFile`
        // were dropped in favour of error-throwing variants, and importing
        // the new ones as top-level extensions varies across cinterop
        // versions. Logs are tiny (one line per call), so loading the
        // existing bytes into NSMutableData and writing the whole file
        // back atomically is correct AND simpler than fighting the
        // bindings. If this ever becomes a hot path, switch to the modern
        // NSFileHandle.fileHandleForWritingToURL(url, error:) API.
        val newBytes = NSString.create(string = "$line\n").dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val mutable = NSMutableData()
        val existing = NSFileManager.defaultManager.contentsAtPath(path)
        if (existing != null) mutable.appendData(existing)
        mutable.appendData(newBytes)
        mutable.writeToFile(path, atomically = true)
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
