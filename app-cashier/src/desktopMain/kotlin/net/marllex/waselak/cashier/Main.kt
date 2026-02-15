package net.marllex.waselak.cashier

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.marllex.waselak.cashier.di.cashierDesktopKoinModules
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(cashierDesktopKoinModules())
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Waselak Cashier"
        ) {
            CashierApp()
        }
    }
}
