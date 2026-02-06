package net.marllex.cafeemanger.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.auth.LoginScreen

const val AUTH_ROUTE = "auth"

/**
 * @param appType identifies the app: "MANAGER", "CASHIER", or "DELIVERY"
 */
fun NavGraphBuilder.authScreen(
    onLoginSuccess: () -> Unit,
    appType: String = "MANAGER",
) {
    composable(route = AUTH_ROUTE) {
        LoginScreen(
            onLoginSuccess = onLoginSuccess,
            appType = appType,
        )
    }
}

fun NavController.navigateToAuth(navOptions: NavOptions? = null) {
    navigate(AUTH_ROUTE, navOptions)
}
