package net.marllex.cafeemanger.cashier.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.cashier.R
import net.marllex.cafeemanger.core.ui.R as CoreR
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.ui.components.LanguageSelector
import net.marllex.cafeemanger.core.ui.components.SignOutButton
import net.marllex.cafeemanger.feature.auth.navigation.AUTH_ROUTE
import net.marllex.cafeemanger.feature.auth.navigation.authScreen
import net.marllex.cafeemanger.feature.cashier.attendance.AttendanceScreen
import net.marllex.cafeemanger.feature.cashier.payment.navigation.navigateToPayment
import net.marllex.cafeemanger.feature.cashier.payment.navigation.paymentScreen
import net.marllex.cafeemanger.feature.cashier.pos.navigation.navigateToPos
import net.marllex.cafeemanger.feature.cashier.pos.navigation.posScreen
import net.marllex.cafeemanger.feature.cashier.receipt.navigation.navigateToReceipt
import net.marllex.cafeemanger.feature.cashier.receipt.navigation.receiptScreen
import net.marllex.cafeemanger.feature.manager.orders.OrdersScreen
import net.marllex.cafeemanger.feature.manager.staff.AnnouncementsScreen
import net.marllex.cafeemanger.feature.manager.staff.DeliveryDashboardScreen
import net.marllex.cafeemanger.feature.manager.tables.TablesScreen

enum class CashierTab(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    POS("cashier/pos", titleRes = R.string.cashier_new_order, Icons.Filled.PointOfSale),
    ORDERS(
        route = "cashier/orders",
        titleRes = R.string.cashier_orders,
        icon = Icons.Filled.History,
    ),
    TABLES(
        route = "cashier/tables",
        titleRes = R.string.cashier_tables,
        icon = Icons.Filled.TableBar,
    ),
    DELIVERY(
        route = "cashier/delivery",
        titleRes = R.string.cashier_delivery,
        icon = Icons.Filled.DeliveryDining,
    ),
    ANNOUNCEMENTS(
        route = "cashier/announcements",
        titleRes = R.string.cashier_announcements,
        icon = Icons.Filled.Campaign,
    ),
    ATTENDANCE(
        route = "cashier/attendance",
        titleRes = R.string.cashier_attendance,
        icon = Icons.Filled.Fingerprint,
    ),
    SETTINGS(
        route = "cashier/settings",
        titleRes = R.string.cashier_settings,
        icon = Icons.Filled.Settings,
    ),
}

// ─── Bottom Bar (phone) ──────────────────────────────────────────
@Composable
private fun CashierBottomBar(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        CashierTab.entries.forEach { tab ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == tab.route } == true

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.titleRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }
}

// ─── Navigation Rail (tablet) ────────────────────────────────────
@Composable
private fun CashierNavRail(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CashierTab.entries.forEach { tab ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == tab.route } == true

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.titleRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.titleRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }
}

// ─── Main Nav Host ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierNavHost(authRepository: AuthRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val scope = rememberCoroutineScope()
    val currentUser by authRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)

    val onSignOut: () -> Unit = remember(navController, scope) {
        {
            scope.launch {
                authRepository.logout()
                navController.navigate(AUTH_ROUTE) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val showNav = CashierTab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    if (isTablet) {
        // Tablet: NavigationRail on the side
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNav) {
                CashierNavRail(navController, currentDestination)
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                )
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (showNav) {
                    currentUser?.name?.let { name ->
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(CoreR.string.welcome_message, name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }
            NavHost(
                navController = navController,
                startDestination = AUTH_ROUTE,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
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
                composable(CashierTab.ORDERS.route) {
                    OrdersScreen(
                        onViewReceipt = { orderId ->
                            navController.navigateToReceipt(orderId)
                        }
                    )
                }
                composable(CashierTab.TABLES.route) { TablesScreen(readOnly = true) }
                composable(CashierTab.DELIVERY.route) { DeliveryDashboardScreen() }
                composable(CashierTab.ANNOUNCEMENTS.route) { AnnouncementsScreen() }
                composable(CashierTab.ATTENDANCE.route) { AttendanceScreen() }
                composable(CashierTab.SETTINGS.route) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LanguageSelector(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(24.dp))
                        SignOutButton(onSignOut = onSignOut)
                    }
                }
                paymentScreen(
                    onPaymentDone = { navController.navigateToPos() },
                    onNavigateToReceipt = { orderId ->
                        navController.navigateToReceipt(orderId)
                    },
                )
                receiptScreen(
                    onBack = { navController.navigateToPos() },
                )
            }
            } // Column
        }
    } else {
        // Phone: Bottom NavigationBar
        Scaffold(
            topBar = {
                if (showNav) {
                    currentUser?.name?.let { name ->
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(CoreR.string.welcome_message, name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }
            },
            bottomBar = {
                if (showNav) CashierBottomBar(navController, currentDestination)
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
                composable(CashierTab.ORDERS.route) {
                    OrdersScreen(
                        onViewReceipt = { orderId ->
                            navController.navigateToReceipt(orderId)
                        }
                    )
                }
                composable(CashierTab.TABLES.route) { TablesScreen(readOnly = true) }
                composable(CashierTab.DELIVERY.route) { DeliveryDashboardScreen() }
                composable(CashierTab.ANNOUNCEMENTS.route) { AnnouncementsScreen() }
                composable(CashierTab.ATTENDANCE.route) { AttendanceScreen() }
                composable(CashierTab.SETTINGS.route) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LanguageSelector(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(24.dp))
                        SignOutButton(onSignOut = onSignOut)
                    }
                }
                paymentScreen(
                    onPaymentDone = { navController.navigateToPos() },
                    onNavigateToReceipt = { orderId ->
                        navController.navigateToReceipt(orderId)
                    },
                )
                receiptScreen(
                    onBack = { navController.navigateToPos() },
                )
            }
        }
    }
}
