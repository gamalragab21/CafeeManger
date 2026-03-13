package net.marllex.waselak.kds.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.drop
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.feature.auth.navigation.AUTH_ROUTE
import net.marllex.waselak.feature.auth.navigation.authScreen
import net.marllex.waselak.kds.display.KdsDisplayScreen

private const val KDS_DISPLAY_ROUTE = "kds_display"

@Composable
fun KdsNavHost(
    authRepository: AuthRepository,
) {
    val navController = rememberNavController()
    val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = true)

    // Navigate to login if session is invalidated
    LaunchedEffect(Unit) {
        snapshotFlow { isLoggedIn }
            .drop(1)
            .collect { loggedIn ->
                if (!loggedIn) {
                    navController.navigate(AUTH_ROUTE) { popUpTo(0) { inclusive = true } }
                }
            }
    }

    NavHost(
        navController = navController,
        startDestination = AUTH_ROUTE,
        modifier = Modifier.fillMaxSize(),
    ) {
        authScreen(
            onLoginSuccess = {
                navController.navigate(KDS_DISPLAY_ROUTE) {
                    popUpTo(AUTH_ROUTE) { inclusive = true }
                }
            },
            appType = "KDS",
        )

        composable(KDS_DISPLAY_ROUTE) {
            KdsDisplayScreen(
                onLogout = {
                    navController.navigate(AUTH_ROUTE) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
