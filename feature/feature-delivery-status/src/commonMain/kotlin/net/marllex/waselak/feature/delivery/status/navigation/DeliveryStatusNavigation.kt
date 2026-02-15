package net.marllex.waselak.feature.delivery.status.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.waselak.feature.delivery.status.DeliveryStatusScreen

const val DELIVERY_STATUS_ROUTE = "delivery/status/{orderId}"

fun NavGraphBuilder.deliveryStatusScreen(
    onBack: () -> Unit = {},
    onNavigateToMap: (Double?, Double?) -> Unit = { _, _ -> },
) {
    composable(DELIVERY_STATUS_ROUTE) {
        DeliveryStatusScreen(
            onBack = onBack,
            onNavigateToMap = onNavigateToMap,
        )
    }
}

fun NavController.navigateToDeliveryStatus(orderId: String) {
    navigate("delivery/status/$orderId")
}
