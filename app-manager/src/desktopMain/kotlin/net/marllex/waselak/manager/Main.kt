package net.marllex.waselak.manager

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.marllex.waselak.manager.di.managerDesktopKoinModules
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(managerDesktopKoinModules())
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Waselak Manager"
        ) {
            ManagerApp()
        }
    }
}
