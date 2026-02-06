package net.marllex.cafeemanger.feature.cashier.pos.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.feature.cashier.pos.PosScreen

const val POS_ROUTE = "cashier/pos"

fun NavGraphBuilder.posScreen(onOrderCreated: (Order) -> Unit = {}) {
    composable(POS_ROUTE) {
        PosScreen(onOrderCreated = onOrderCreated)
    }
}

fun NavController.navigateToPos() {
    navigate(POS_ROUTE)
}
