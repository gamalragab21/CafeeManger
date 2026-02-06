package net.marllex.cafeemanger.feature.delivery.orders.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.delivery.orders.DeliveryOrdersScreen

const val DELIVERY_ORDERS_ROUTE = "delivery/orders"

fun NavGraphBuilder.deliveryOrdersScreen(onNavigateToOrder: (String) -> Unit = {}) {
    composable(DELIVERY_ORDERS_ROUTE) {
        DeliveryOrdersScreen(onNavigateToOrder = onNavigateToOrder)
    }
}

fun NavController.navigateToDeliveryOrders() {
    navigate(DELIVERY_ORDERS_ROUTE)
}
