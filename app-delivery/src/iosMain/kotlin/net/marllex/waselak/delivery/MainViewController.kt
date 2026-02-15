package net.marllex.waselak.delivery

import androidx.compose.ui.window.ComposeUIViewController
import net.marllex.waselak.delivery.di.deliveryIosKoinModules
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(deliveryIosKoinModules())
    }
}

fun MainViewController() = ComposeUIViewController { DeliveryApp() }
