package net.marllex.waselak.cashier

import androidx.compose.ui.window.ComposeUIViewController
import net.marllex.waselak.cashier.di.cashierIosKoinModules
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(cashierIosKoinModules())
    }
}

fun MainViewController() = ComposeUIViewController { CashierApp() }
