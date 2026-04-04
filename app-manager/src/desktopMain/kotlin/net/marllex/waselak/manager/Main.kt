package net.marllex.waselak.manager

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.currentLanguageState
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import net.marllex.waselak.manager.di.managerDesktopKoinModules
import org.koin.core.context.startKoin
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import org.koin.java.KoinJavaComponent.getKoin

fun main() {
    CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "manager", debug = BuildConfig.IS_DEBUG, platform = System.getProperty("os.name", "desktop").lowercase().let { os -> when { os.contains("mac") -> "macos"; os.contains("win") -> "windows"; os.contains("linux") -> "linux"; else -> "desktop" } })
    CrashReporter.setTag("app.version", BuildConfig.VERSION_NAME)
    CrashReporter.setTag("app.version_code", BuildConfig.VERSION_CODE.toString())
    CrashReporter.setTag("app.type", "manager")
    CrashReporter.setExtra("build.debug", BuildConfig.IS_DEBUG.toString())
    CrashReporter.setExtra("build.base_url", BuildConfig.BASE_URL)
    startKoin {
        modules(managerDesktopKoinModules())
    }
    AppLogger.initialize("manager")

    // Configure Coil image loader with Ktor client (has ngrok header)
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = getKoin().get<HttpClient>()))
            }
            .build()
    }

    // Restore persisted language before UI starts
    applyLanguage(getPersistedLanguage())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = if (net.marllex.waselak.config.BuildConfig.IS_DEBUG) "Waselak Manager Debug" else "Waselak Manager",
            icon = painterResource("icon.png"),
        ) {
            val currentLang by currentLanguageState
            val layoutDirection = if (currentLang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                ManagerApp()
            }
        }
    }
}
