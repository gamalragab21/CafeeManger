package net.marllex.waselak.cashier

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import io.ktor.client.HttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.currentLanguageState
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import net.marllex.waselak.cashier.di.cashierDesktopKoinModules
import org.koin.core.context.startKoin
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import org.koin.java.KoinJavaComponent.getKoin

fun main() {
    CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "cashier", debug = BuildConfig.IS_DEBUG, platform = System.getProperty("os.name", "desktop").lowercase().let { os -> when { os.contains("mac") -> "macos"; os.contains("win") -> "windows"; os.contains("linux") -> "linux"; else -> "desktop" } })
    CrashReporter.setTag("app.version", BuildConfig.VERSION_NAME)
    CrashReporter.setTag("app.version_code", BuildConfig.VERSION_CODE.toString())
    CrashReporter.setTag("app.type", "cashier")
    CrashReporter.setExtra("build.debug", BuildConfig.IS_DEBUG.toString())
    CrashReporter.setExtra("build.base_url", BuildConfig.BASE_URL)
    startKoin {
        modules(cashierDesktopKoinModules())
    }
    AppLogger.initialize("cashier")

    // Configure Coil image loader with Ktor client (has ngrok header) and a
    // generous on-disk cache so reopening the cashier reuses menu thumbnails.
    SingletonImageLoader.setSafe {
        val cacheDir = File(System.getProperty("user.home"), ".waselak/image_cache").also { it.mkdirs() }
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = getKoin().get<HttpClient>()))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(128L * 1024L * 1024L) // 128 MB in RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.toOkioPath())
                    .maxSizeBytes(512L * 1024L * 1024L) // 512 MB on disk
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    // Restore persisted language before UI starts
    applyLanguage(getPersistedLanguage())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = if (net.marllex.waselak.config.BuildConfig.IS_DEBUG) "Waselak Cashier Debug" else "Waselak Cashier",
            icon = painterResource("icon.png"),
        ) {
            val currentLang by currentLanguageState
            val layoutDirection = if (currentLang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                CashierApp()
            }
        }
    }
}
