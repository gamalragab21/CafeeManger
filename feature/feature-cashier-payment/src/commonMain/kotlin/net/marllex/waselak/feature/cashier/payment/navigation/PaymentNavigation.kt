package net.marllex.waselak.feature.cashier.payment.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.waselak.feature.cashier.payment.PaymentScreen

const val PAYMENT_ROUTE = "cashier/payment/{orderId}"

fun NavGraphBuilder.paymentScreen(
    onPaymentDone: () -> Unit = {},
    onNavigateToReceipt: (String) -> Unit = {},
) {
    composable(PAYMENT_ROUTE) {
        PaymentScreen(
            onPaymentDone = onPaymentDone,
            onNavigateToReceipt = onNavigateToReceipt,
        )
    }
}

fun NavController.navigateToPayment(orderId: String) {
    navigate("cashier/payment/$orderId")
}
