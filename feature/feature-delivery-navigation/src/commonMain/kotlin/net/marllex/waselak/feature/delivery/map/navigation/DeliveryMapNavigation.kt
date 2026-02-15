package net.marllex.waselak.feature.delivery.map.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.waselak.feature.delivery.map.DeliveryMapScreen

const val DELIVERY_MAP_ROUTE = "delivery/map"

fun NavGraphBuilder.deliveryMapScreen(
    onNavigateToOrder: (String) -> Unit = {},
    onOpenGoogleMaps: (Double, Double) -> Unit = { _, _ -> },
) {
    composable(DELIVERY_MAP_ROUTE) {
        DeliveryMapScreen(
            onNavigateToOrder = onNavigateToOrder,
            onOpenGoogleMaps = onOpenGoogleMaps,
        )
    }
}

fun NavController.navigateToDeliveryMap() {
    navigate(DELIVERY_MAP_ROUTE)
}
