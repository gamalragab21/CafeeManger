package net.marllex.waselak.delivery

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import net.marllex.waselak.delivery.di.deliveryDesktopKoinModules
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(deliveryDesktopKoinModules())
    }

    // Restore persisted language before UI starts
    applyLanguage(getPersistedLanguage())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Waselak Delivery"
        ) {
            DeliveryApp()
        }
    }
}
