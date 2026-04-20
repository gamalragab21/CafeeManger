package net.marllex.waselak.cashier

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.util.DebugLogger
import io.ktor.client.HttpClient
import net.marllex.waselak.cashier.di.cashierKoinModules
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.get as getKoin
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import org.koin.core.context.startKoin

class CashierApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "cashier", platform = "android", debug = BuildConfig.IS_DEBUG)
        CrashReporter.setTag("app.version", BuildConfig.VERSION_NAME)
        CrashReporter.setTag("app.version_code", BuildConfig.VERSION_CODE.toString())
        CrashReporter.setTag("app.type", "cashier")
        CrashReporter.setExtra("build.debug", BuildConfig.IS_DEBUG.toString())
        CrashReporter.setExtra("build.base_url", BuildConfig.BASE_URL)
        startKoin {
            androidLogger()
            androidContext(this@CashierApplication)
            modules(cashierKoinModules())
        }
        AppLogger.initialize("cashier")
        // Configure Coil to use the app's Ktor HttpClient (includes ngrok header)
        // and a generous memory + disk cache: the menu grid re-visits the same ~100
        // item thumbnails all shift, so re-decoding them is pure waste.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(KtorNetworkFetcherFactory(httpClient = getKoin().get<HttpClient>()))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)      // 25% of available app heap
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                        .maxSizeBytes(256L * 1024L * 1024L) // 256 MB on-disk — plenty for a menu
                        .build()
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .apply { if (BuildConfig.IS_DEBUG) logger(DebugLogger()) }
                .build()
        }
    }
}
