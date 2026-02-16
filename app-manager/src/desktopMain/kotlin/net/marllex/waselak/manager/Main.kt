package net.marllex.waselak.manager

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.marllex.waselak.core.ui.components.applyLanguage
import net.marllex.waselak.core.ui.components.getPersistedLanguage
import net.marllex.waselak.manager.di.managerDesktopKoinModules
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(managerDesktopKoinModules())
    }

    // Restore persisted language before UI starts
    applyLanguage(getPersistedLanguage())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Waslek Manager",
            icon = painterResource("icon.png"),
        ) {
            ManagerApp()
        }
    }
}
