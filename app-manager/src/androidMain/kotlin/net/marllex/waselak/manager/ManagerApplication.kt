package net.marllex.waselak.manager

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import net.marllex.waselak.manager.di.managerKoinModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.get as getKoin
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import org.koin.core.context.startKoin

class ManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "manager", platform = "android", debug = BuildConfig.IS_DEBUG)
        startKoin {
            androidLogger()
            androidContext(this@ManagerApplication)
            modules(managerKoinModules())
        }
        AppLogger.initialize("manager")
        // Configure Coil to use the app's Ktor HttpClient (includes ngrok header)
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(KtorNetworkFetcherFactory(httpClient = getKoin().get<HttpClient>()))
                }
                .build()
        }
    }
}
