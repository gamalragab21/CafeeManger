package net.marllex.waselak.core.common.logging

expect object LogFileWriter {
    fun getLogFilePath(appName: String): String
    fun appendLine(path: String, line: String)
    fun readBytes(path: String): ByteArray
    fun delete(path: String)
    fun exists(path: String): Boolean
    fun lastModified(path: String): Long
}
