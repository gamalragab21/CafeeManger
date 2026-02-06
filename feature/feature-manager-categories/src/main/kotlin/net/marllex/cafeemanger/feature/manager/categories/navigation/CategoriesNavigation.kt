package net.marllex.cafeemanger.feature.manager.categories.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.manager.categories.CategoriesScreen

const val CATEGORIES_ROUTE = "manager/categories"

fun NavGraphBuilder.categoriesScreen() {
    composable(CATEGORIES_ROUTE) {
        CategoriesScreen()
    }
}

fun NavController.navigateToCategories() {
    navigate(CATEGORIES_ROUTE)
}
