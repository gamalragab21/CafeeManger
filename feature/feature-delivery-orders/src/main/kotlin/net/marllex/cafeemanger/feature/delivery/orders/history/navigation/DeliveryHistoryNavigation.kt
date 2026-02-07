package net.marllex.cafeemanger.feature.delivery.orders.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.delivery.orders.history.DeliveryHistoryScreen

const val DELIVERY_HISTORY_ROUTE = "delivery/history"

fun NavGraphBuilder.deliveryHistoryScreen(
    onNavigateToReceipt: (String) -> Unit,
) {
    composable(DELIVERY_HISTORY_ROUTE) {
        DeliveryHistoryScreen(onViewReceipt = onNavigateToReceipt)
    }
}

fun NavController.navigateToDeliveryHistory() {
    navigate(DELIVERY_HISTORY_ROUTE)
}
