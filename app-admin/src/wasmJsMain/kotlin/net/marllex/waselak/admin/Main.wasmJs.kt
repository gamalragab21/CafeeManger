package net.marllex.waselak.admin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import net.marllex.waselak.admin.di.adminModule
import net.marllex.waselak.admin.session.AdminSessionManager
import net.marllex.waselak.admin.session.WasmJsAdminSessionManager
import org.koin.core.context.startKoin
import org.koin.dsl.module

private val wasmAdminModule = module {
    single<AdminSessionManager> { WasmJsAdminSessionManager() }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(wasmAdminModule, adminModule)
    }
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "Waselak Admin") {
        AdminApp()
    }
}
