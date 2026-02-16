package net.marllex.waselak.core.auth.di

import java.io.File

actual fun dataStorePath(appName: String): String {
    val appDir = File(System.getProperty("user.home"), ".waselak/$appName")
    appDir.mkdirs()
    return File(appDir, authDataStoreFileName(appName)).absolutePath
}
