package net.marllex.waselak.cashier

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import net.marllex.waselak.cashier.di.cashierDesktopKoinModules
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(cashierDesktopKoinModules())
    }

    // Restore persisted language before UI starts
    applyLanguage(getPersistedLanguage())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Waslek Cashier",
            icon = painterResource("icon.png"),
        ) {
            CashierApp()
        }
    }
}
