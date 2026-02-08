package net.marllex.cafeemanger.feature.cashier.attendance.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.cashier.attendance.AttendanceScreen

const val ATTENDANCE_ROUTE = "cashier/attendance"

fun NavGraphBuilder.attendanceScreen() {
    composable(ATTENDANCE_ROUTE) {
        AttendanceScreen()
    }
}

fun NavController.navigateToAttendance() {
    navigate(ATTENDANCE_ROUTE)
}
