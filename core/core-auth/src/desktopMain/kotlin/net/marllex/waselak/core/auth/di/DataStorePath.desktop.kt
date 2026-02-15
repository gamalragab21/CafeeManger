package net.marllex.waselak.core.auth.di

import java.io.File

actual fun dataStorePath(): String {
    val appDir = File(System.getProperty("user.home"), ".waselak")
    appDir.mkdirs()
    return File(appDir, AUTH_DATASTORE_FILE).absolutePath
}
