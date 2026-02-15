package net.marllex.waselak.feature.manager.tables.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.waselak.feature.manager.tables.TablesScreen

const val TABLES_ROUTE = "manager/tables"

fun NavGraphBuilder.tablesScreen() {
    composable(TABLES_ROUTE) {
        TablesScreen()
    }
}

fun NavController.navigateToTables() {
    navigate(TABLES_ROUTE)
}
