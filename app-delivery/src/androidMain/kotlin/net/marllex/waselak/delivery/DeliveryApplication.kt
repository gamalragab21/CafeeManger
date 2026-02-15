package net.marllex.waselak.delivery

import android.app.Application
import net.marllex.waselak.delivery.di.deliveryKoinModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DeliveryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@DeliveryApplication)
            modules(deliveryKoinModules())
        }
    }
}
