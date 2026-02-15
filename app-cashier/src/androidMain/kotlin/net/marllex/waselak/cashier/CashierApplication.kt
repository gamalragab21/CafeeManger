package net.marllex.waselak.cashier

import android.app.Application
import net.marllex.waselak.cashier.di.cashierKoinModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CashierApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@CashierApplication)
            modules(cashierKoinModules())
        }
    }
}
