package net.marllex.waselak.manager

import androidx.compose.ui.window.ComposeUIViewController
import net.marllex.waselak.manager.di.managerIosKoinModules
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(managerIosKoinModules())
    }
}

fun MainViewController() = ComposeUIViewController { ManagerApp() }
