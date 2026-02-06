package net.marllex.cafeemanger.cashier.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.marllex.cafeemanger.feature.auth.navigation.AUTH_ROUTE
import net.marllex.cafeemanger.feature.auth.navigation.authScreen
import net.marllex.cafeemanger.feature.cashier.payment.navigation.paymentScreen
import net.marllex.cafeemanger.feature.cashier.payment.navigation.navigateToPayment
import net.marllex.cafeemanger.feature.cashier.pos.navigation.posScreen
import net.marllex.cafeemanger.feature.cashier.pos.navigation.navigateToPos
import net.marllex.cafeemanger.feature.cashier.receipt.navigation.receiptScreen
import net.marllex.cafeemanger.feature.cashier.receipt.navigation.navigateToReceipt
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.cashier.R
import net.marllex.cafeemanger.feature.manager.orders.OrdersScreen
import net.marllex.cafeemanger.core.ui.components.LanguageSelector

enum class CashierTab(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    POS("cashier/pos",  titleRes = R.string.cashier_new_order, Icons.Filled.PointOfSale),
    ORDERS(
        route = "cashier/orders",
        titleRes = R.string.cashier_orders,
        icon = Icons.Filled.History
    ),
    SETTINGS(
        route = "cashier/settings",
        titleRes = R.string.cashier_settings,
        icon = Icons.Filled.Settings
    ),
}

@Composable
fun CashierNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Show bottom bar only on main tabs (not on payment/receipt screens)
    val showBottomBar = CashierTab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    CashierTab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.titleRes)) },
                            label = { Text(stringResource( tab.titleRes)) },
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AUTH_ROUTE,
            modifier = Modifier.padding(innerPadding)
        ) {
            authScreen(
                onLoginSuccess = {
                    navController.navigate(CashierTab.POS.route) {
                        popUpTo(AUTH_ROUTE) { inclusive = true }
                    }
                },
                appType = "CASHIER",
            )

            posScreen(
                onOrderCreated = { order ->
                    navController.navigateToPayment(order.id)
                }
            )

            // Order history using the same Orders composable
            composable(CashierTab.ORDERS.route) {
                OrdersScreen()
            }

            composable(CashierTab.SETTINGS.route) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LanguageSelector(modifier = Modifier.fillMaxWidth())
                }
            }

            paymentScreen(
                onPaymentDone = {
                    navController.navigateToPos()
                },
                onNavigateToReceipt = { orderId ->
                    navController.navigateToReceipt(orderId)
                },
            )

            receiptScreen(
                onBack = {
                    navController.navigateToPos()
                },
            )
        }
    }
}
