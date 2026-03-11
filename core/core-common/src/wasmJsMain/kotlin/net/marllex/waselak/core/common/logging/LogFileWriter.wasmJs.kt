package net.marllex.waselak.core.common.logging

actual object LogFileWriter {
    actual fun getLogFilePath(appName: String): String = "waselak_${appName}.log"
    actual fun appendLine(path: String, line: String) {
        // On web, log to browser console via println (Kotlin/Wasm maps this to console.log)
        println(line)
    }
    actual fun readBytes(path: String): ByteArray = ByteArray(0)
    actual fun delete(path: String) { /* no-op on web */ }
    actual fun exists(path: String): Boolean = false
    actual fun lastModified(path: String): Long = 0L
}
