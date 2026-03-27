package net.marllex.waselak.admin

import android.app.Application
import net.marllex.waselak.admin.di.adminKoinModules
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.common.crash.CrashReporter
import net.marllex.waselak.core.common.logging.AppLogger
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class AdminApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize(dsn = BuildConfig.SENTRY_DSN, appName = "admin", platform = "android")
        startKoin {
            androidLogger()
            androidContext(this@AdminApplication)
            modules(adminKoinModules())
        }
        AppLogger.initialize("admin")
    }
}
