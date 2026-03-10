package net.marllex.waselak.cashier

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import net.marllex.waselak.cashier.di.cashierDesktopKoinModules
import org.koin.core.context.startKoin
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import org.koin.java.KoinJavaComponent.getKoin

fun main() {
    CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "cashier")
    startKoin {
        modules(cashierDesktopKoinModules())
    }
    AppLogger.initialize("cashier")

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
            title = "Waslek Cashier",
            icon = painterResource("icon.png"),
        ) {
            CashierApp()
        }
    }
}
