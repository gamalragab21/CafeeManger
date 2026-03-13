package net.marllex.waselak.kds

import androidx.compose.runtime.Composable
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.ui.theme.WaselakTheme
import net.marllex.waselak.kds.navigation.KdsNavHost
import org.koin.compose.koinInject

@Composable
fun KdsApp() {
    val authRepository: AuthRepository = koinInject()
    WaselakTheme {
        KdsNavHost(authRepository = authRepository)
    }
}
