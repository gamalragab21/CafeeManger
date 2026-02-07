package net.marllex.cafeemanger.feature.delivery.orders.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import net.marllex.cafeemanger.feature.delivery.orders.DeliveryOrdersScreen
import net.marllex.cafeemanger.feature.delivery.orders.DeliveryReceiptScreen

const val DELIVERY_ORDERS_ROUTE = "delivery/orders"
const val DELIVERY_RECEIPT_ROUTE = "delivery/receipt/{orderId}"

fun NavGraphBuilder.deliveryOrdersScreen(
    onNavigateToOrder: (String) -> Unit = {},
    onNavigateToReceipt: (String) -> Unit = {}
) {
    composable(DELIVERY_ORDERS_ROUTE) {
        DeliveryOrdersScreen(
            onNavigateToOrder = onNavigateToOrder,
            onNavigateToReceipt = onNavigateToReceipt
        )
    }
}

fun NavGraphBuilder.deliveryReceiptScreen(onBack: () -> Unit = {}) {
    composable(
        DELIVERY_RECEIPT_ROUTE,
        arguments = listOf(navArgument("orderId") { type = NavType.StringType })
    ) {
        DeliveryReceiptScreen(onBack = onBack)
    }
}

fun NavController.navigateToDeliveryOrders() {
    navigate(DELIVERY_ORDERS_ROUTE)
}

fun NavController.navigateToDeliveryReceipt(orderId: String) {
    navigate("delivery/receipt/$orderId")
}
