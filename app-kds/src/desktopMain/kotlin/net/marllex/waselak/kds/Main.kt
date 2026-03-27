package net.marllex.waselak.kds

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.kds.di.kdsDesktopKoinModules
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

fun main() {
    CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "kds", platform = System.getProperty("os.name", "desktop").lowercase().let { os -> when { os.contains("mac") -> "macos"; os.contains("win") -> "windows"; os.contains("linux") -> "linux"; else -> "desktop" } })
    startKoin {
        modules(kdsDesktopKoinModules())
    }
    AppLogger.initialize("kds")

    // Configure Coil image loader with Ktor client (has ngrok header)
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = getKoin().get<HttpClient>()))
            }
            .build()
    }

    applyLanguage(getPersistedLanguage())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Waselak KDS",
        ) {
            KdsApp()
        }
    }
}
