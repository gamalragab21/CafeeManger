package net.marllex.waselak.core.common.logging

import java.io.File

actual object LogFileWriter {
    actual fun getLogFilePath(appName: String): String {
        val logsDir = File(System.getProperty("user.home"), ".waselak/$appName/logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        return File(logsDir, "waselak_$appName.log").absolutePath
    }

    actual fun appendLine(path: String, line: String) {
        synchronized(this) {
            File(path).appendText("$line\n")
        }
    }

    actual fun readBytes(path: String): ByteArray {
        val file = File(path)
        return if (file.exists()) file.readBytes() else ByteArray(0)
    }

    actual fun delete(path: String) {
        File(path).delete()
    }

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun lastModified(path: String): Long = File(path).lastModified()
}
