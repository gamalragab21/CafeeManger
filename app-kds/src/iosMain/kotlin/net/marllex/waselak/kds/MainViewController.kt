package net.marllex.waselak.kds

import androidx.compose.ui.window.ComposeUIViewController
import net.marllex.waselak.kds.di.kdsIosKoinModules
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(kdsIosKoinModules())
    }
}

fun MainViewController() = ComposeUIViewController { KdsApp() }
