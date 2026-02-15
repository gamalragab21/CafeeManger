package net.marllex.waselak.feature.manager.users.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.waselak.feature.manager.users.UsersScreen

const val USERS_ROUTE = "manager/users"

fun NavGraphBuilder.usersScreen() {
    composable(USERS_ROUTE) {
        UsersScreen()
    }
}

fun NavController.navigateToUsers() {
    navigate(USERS_ROUTE)
}
