package net.marllex.waselak.delivery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.currentLanguageState
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.delivery.di.deliveryDesktopKoinModules
import org.koin.core.context.startKoin

fun main() {
    CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "delivery", debug = BuildConfig.IS_DEBUG, platform = System.getProperty("os.name", "desktop").lowercase().let { os -> when { os.contains("mac") -> "macos"; os.contains("win") -> "windows"; os.contains("linux") -> "linux"; else -> "desktop" } })
    CrashReporter.setTag("app.version", BuildConfig.VERSION_NAME)
    CrashReporter.setTag("app.version_code", BuildConfig.VERSION_CODE.toString())
    CrashReporter.setTag("app.type", "delivery")
    CrashReporter.setExtra("build.debug", BuildConfig.IS_DEBUG.toString())
    CrashReporter.setExtra("build.base_url", BuildConfig.BASE_URL)
    startKoin {
        modules(deliveryDesktopKoinModules())
    }

    // Restore persisted language before UI starts
    applyLanguage(getPersistedLanguage())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = if (net.marllex.waselak.config.BuildConfig.IS_DEBUG) "Waselak Delivery Debug" else "Waselak Delivery",
            icon = painterResource("icon.png"),
        ) {
            val currentLang by currentLanguageState
            val layoutDirection = if (currentLang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                DeliveryApp()
            }
        }
    }
}
