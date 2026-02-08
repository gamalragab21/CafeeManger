package net.marllex.cafeemanger.feature.manager.staff.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.manager.staff.StaffScreen

const val STAFF_ROUTE = "manager/staff"

fun NavGraphBuilder.staffScreen() {
    composable(STAFF_ROUTE) {
        StaffScreen()
    }
}

fun NavController.navigateToStaff() {
    navigate(STAFF_ROUTE)
}
