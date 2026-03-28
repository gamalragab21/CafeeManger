package net.marllex.waselak.core.common.logging

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.common.crash.CrashReporter

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

object AppLogger {
    private var appName: String = "app"
    private var logPath: String = ""
    private var initialized: Boolean = false

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    fun initialize(appName: String) {
        this.appName = appName
        this.logPath = LogFileWriter.getLogFilePath(appName)
        this.initialized = true
        cleanupIfNeeded()
        i("AppLogger", "Logger initialized for $appName")
    }

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
        CrashReporter.logInfo(tag, "[DEBUG] $message")
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
        CrashReporter.logInfo(tag, message)
    }

    fun w(tag: String, message: String) {
        log(LogLevel.WARN, tag, message)
        CrashReporter.logWarning(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message | ${throwable::class.simpleName}: ${throwable.message}"
        } else {
            message
        }
        log(LogLevel.ERROR, tag, fullMessage)
        CrashReporter.logError(tag, message, throwable)
    }

    /** Log to file only — no Sentry bridge (used for verbose HTTP logs to avoid quota) */
    fun logToFileOnly(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }

    fun readLogFileBytes(): ByteArray {
        if (!initialized || !LogFileWriter.exists(logPath)) return ByteArray(0)
        return try {
            LogFileWriter.readBytes(logPath)
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    fun getLogFileName(): String = "waselak_${appName}.log"

    fun clearLogs() {
        if (!initialized) return
        try {
            LogFileWriter.delete(logPath)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        if (!initialized) return
        try {
            val now = Clock.System.now()
            val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val timestamp = "${local.year}-${local.monthNumber.pad()}-${local.dayOfMonth.pad()} " +
                "${local.hour.pad()}:${local.minute.pad()}:${local.second.pad()}"
            val line = "[$timestamp] [${level.name}] [$tag] $message"
            LogFileWriter.appendLine(logPath, line)
        } catch (_: Exception) {
            // Never let logging crash the app
        }
    }

    private fun cleanupIfNeeded() {
        try {
            if (LogFileWriter.exists(logPath)) {
                val lastMod = LogFileWriter.lastModified(logPath)
                val now = Clock.System.now().toEpochMilliseconds()
                if (now - lastMod > WEEK_MS) {
                    LogFileWriter.delete(logPath)
                }
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun Int.pad(): String = toString().padStart(2, '0')
}
