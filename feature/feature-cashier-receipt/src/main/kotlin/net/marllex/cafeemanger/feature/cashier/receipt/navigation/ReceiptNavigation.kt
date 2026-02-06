package net.marllex.cafeemanger.feature.cashier.receipt.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.cashier.receipt.ReceiptScreen

const val RECEIPT_ROUTE = "cashier/receipt/{orderId}"

fun NavGraphBuilder.receiptScreen(onBack: () -> Unit = {}) {
    composable(RECEIPT_ROUTE) {
        ReceiptScreen(onBack = onBack)
    }
}

fun NavController.navigateToReceipt(orderId: String) {
    navigate("cashier/receipt/$orderId")
}
