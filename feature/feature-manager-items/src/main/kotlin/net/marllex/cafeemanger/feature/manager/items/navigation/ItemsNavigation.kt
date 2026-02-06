package net.marllex.cafeemanger.feature.manager.items.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.manager.items.ItemsScreen

const val ITEMS_ROUTE = "manager/items"

fun NavGraphBuilder.itemsScreen() {
    composable(ITEMS_ROUTE) {
        ItemsScreen()
    }
}

fun NavController.navigateToItems() {
    navigate(ITEMS_ROUTE)
}
