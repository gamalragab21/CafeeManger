package net.marllex.waselak.manager

import android.app.Application
import net.marllex.waselak.manager.di.managerKoinModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@ManagerApplication)
            modules(managerKoinModules())
        }
    }
}
