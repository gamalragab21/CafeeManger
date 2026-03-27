package net.marllex.waselak.kds

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import net.marllex.waselak.kds.di.kdsKoinModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.get as getKoin
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import org.koin.core.context.startKoin

class KdsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "kds", platform = "android")
        startKoin {
            androidLogger()
            androidContext(this@KdsApplication)
            modules(kdsKoinModules())
        }
        AppLogger.initialize("kds")
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(KtorNetworkFetcherFactory(httpClient = getKoin().get<HttpClient>()))
                }
                .build()
        }
    }
}
