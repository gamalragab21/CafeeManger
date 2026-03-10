package net.marllex.waselak.cashier

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import net.marllex.waselak.cashier.di.cashierKoinModules
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
        CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "cashier")
        startKoin {
            androidLogger()
            androidContext(this@CashierApplication)
            modules(cashierKoinModules())
        }
        AppLogger.initialize("cashier")
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
