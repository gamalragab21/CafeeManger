package net.marllex.waselak.feature.cashier.pos.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.feature.cashier.pos.PosScreen

const val POS_ROUTE = "cashier/pos"

fun NavGraphBuilder.posScreen(onOrderCreated: (Order) -> Unit = {}) {
    composable(
        route = "$POS_ROUTE?tableId={tableId}&reservationId={reservationId}&clientName={clientName}&clientPhone={clientPhone}",
        arguments = listOf(
            navArgument("tableId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("reservationId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("clientName") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("clientPhone") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) {
        PosScreen(onOrderCreated = onOrderCreated)
    }
}

fun NavController.navigateToPos() {
    navigate(POS_ROUTE)
}

fun NavController.navigateToPosWithReservation(
    tableId: String,
    reservationId: String,
    clientName: String,
    clientPhone: String?,
) {
    val route = buildString {
        append(POS_ROUTE)
        append("?tableId=$tableId")
        append("&reservationId=$reservationId")
        append("&clientName=$clientName")
        if (!clientPhone.isNullOrBlank()) append("&clientPhone=$clientPhone")
    }
    navigate(route)
}
