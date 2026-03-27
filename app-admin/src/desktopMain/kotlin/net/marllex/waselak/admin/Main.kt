package net.marllex.waselak.admin

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.marllex.waselak.admin.di.adminModule
import net.marllex.waselak.admin.session.AdminSessionManager
import net.marllex.waselak.admin.session.DesktopAdminSessionManager
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import org.koin.core.context.startKoin
import org.koin.dsl.module

private val desktopAdminModule = module {
    single<AdminSessionManager> { DesktopAdminSessionManager() }
}

fun main() {
    CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "admin", platform = System.getProperty("os.name", "desktop").lowercase().let { os -> when { os.contains("mac") -> "macos"; os.contains("win") -> "windows"; os.contains("linux") -> "linux"; else -> "desktop" } })
    AppLogger.initialize("admin")
    applyLanguage(getPersistedLanguage())
    startKoin {
        modules(desktopAdminModule, adminModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Waselak Admin",
            icon = painterResource("icon.png"),
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            AdminApp()
        }
    }
}
