package net.marllex.waselak.feature.manager.analytics.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.waselak.feature.manager.analytics.AnalyticsScreen

const val ANALYTICS_ROUTE = "manager/analytics"

fun NavGraphBuilder.analyticsScreen() {
    composable(ANALYTICS_ROUTE) {
        AnalyticsScreen()
    }
}

fun NavController.navigateToAnalytics() {
    navigate(ANALYTICS_ROUTE)
}
