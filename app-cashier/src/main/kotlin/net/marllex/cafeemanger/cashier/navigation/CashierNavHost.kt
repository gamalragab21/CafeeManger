package net.marllex.cafeemanger.cashier.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier.height(72.dp)
                    ) {
                        CashierTab.entries.forEach { tab ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val iconSize by animateDpAsState(if (isSelected) 28.dp else 24.dp)
                                    Icon(
                                        tab.icon,
                                        contentDescription = stringResource(tab.titleRes),
                                        modifier = Modifier.size(iconSize),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(tab.titleRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
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
                OrdersScreen(
                    onViewReceipt = { orderId ->
                        navController.navigateToReceipt(orderId)
                    }
                )
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
