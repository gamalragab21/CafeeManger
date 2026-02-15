package net.marllex.waselak.manager

import androidx.compose.runtime.Composable
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.ui.theme.WaselakTheme
import net.marllex.waselak.manager.navigation.ManagerNavHost
import org.koin.compose.koinInject

@Composable
fun ManagerApp() {
    val authRepository: AuthRepository = koinInject()
    WaselakTheme {
        ManagerNavHost(authRepository = authRepository)
    }
}
