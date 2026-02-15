package net.marllex.waselak.feature.manager.orders.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.waselak.feature.manager.orders.OrdersScreen

const val MANAGER_ORDERS_ROUTE = "manager/orders"

fun NavGraphBuilder.managerOrdersScreen() {
    composable(MANAGER_ORDERS_ROUTE) {
        OrdersScreen()
    }
}

fun NavController.navigateToManagerOrders() {
    navigate(MANAGER_ORDERS_ROUTE)
}
