package net.marllex.cafeemanger.feature.manager.stock.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.manager.stock.StockScreen

const val STOCK_ROUTE = "manager/stock"

fun NavGraphBuilder.stockScreen() {
    composable(STOCK_ROUTE) {
        StockScreen()
    }
}

fun NavController.navigateToStock() {
    navigate(STOCK_ROUTE)
}
