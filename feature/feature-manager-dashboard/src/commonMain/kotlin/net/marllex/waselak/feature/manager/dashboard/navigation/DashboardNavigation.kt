package net.marllex.waselak.feature.manager.dashboard.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.waselak.feature.manager.dashboard.DashboardScreen

const val DASHBOARD_ROUTE = "manager/dashboard"

fun NavGraphBuilder.dashboardScreen() {
    composable(DASHBOARD_ROUTE) {
        DashboardScreen()
    }
}

fun NavController.navigateToDashboard() {
    navigate(DASHBOARD_ROUTE)
}
