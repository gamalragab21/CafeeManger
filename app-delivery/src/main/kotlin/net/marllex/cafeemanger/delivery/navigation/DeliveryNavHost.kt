package net.marllex.cafeemanger.delivery.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
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
import net.marllex.cafeemanger.core.ui.components.LanguageSelector
import net.marllex.cafeemanger.delivery.R
import net.marllex.cafeemanger.feature.auth.navigation.AUTH_ROUTE
import net.marllex.cafeemanger.feature.auth.navigation.authScreen
import net.marllex.cafeemanger.feature.delivery.map.navigation.deliveryMapScreen
import net.marllex.cafeemanger.feature.delivery.orders.history.navigation.deliveryHistoryScreen
import net.marllex.cafeemanger.feature.delivery.orders.navigation.deliveryOrdersScreen
import net.marllex.cafeemanger.feature.delivery.orders.navigation.deliveryReceiptScreen
import net.marllex.cafeemanger.feature.delivery.orders.navigation.navigateToDeliveryReceipt
import net.marllex.cafeemanger.feature.delivery.status.navigation.deliveryStatusScreen
import net.marllex.cafeemanger.feature.delivery.status.navigation.navigateToDeliveryStatus

enum class DeliveryTab(
    val route: String,
    val title: Int,
    val icon: ImageVector,
) {
    ORDERS("delivery/orders", R.string.my_orders_menu, Icons.Filled.DeliveryDining),
    HISTORY("delivery/history", R.string.history, Icons.Filled.History),
    MAP("delivery/map", R.string.map, Icons.Filled.Map),
    SETTINGS("delivery/settings", R.string.settings, Icons.Filled.Settings),
}

// ─── Bottom Bar (phone) ──────────────────────────────────────────
@Composable
private fun DeliveryBottomBar(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        DeliveryTab.entries.forEach { tab ->
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
                        contentDescription = stringResource(tab.title),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.title),
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
private fun DeliveryNavRail(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DeliveryTab.entries.forEach { tab ->
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
                        contentDescription = stringResource(tab.title),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.title),
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
@Composable
fun DeliveryNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val showNav = DeliveryTab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    // Shared navigation graph builder
    val navGraphBuilder: androidx.navigation.NavGraphBuilder.() -> Unit = {
        authScreen(
            onLoginSuccess = {
                navController.navigate(DeliveryTab.ORDERS.route) {
                    popUpTo(AUTH_ROUTE) { inclusive = true }
                }
            },
            appType = "DELIVERY",
        )

        deliveryOrdersScreen(
            onNavigateToOrder = { orderId ->
                navController.navigateToDeliveryStatus(orderId)
            },
            onNavigateToReceipt = { orderId ->
                navController.navigateToDeliveryReceipt(orderId)
            }
        )

        deliveryHistoryScreen(
            onNavigateToReceipt = { orderId ->
                navController.navigateToDeliveryReceipt(orderId)
            }
        )

        deliveryMapScreen(
            onNavigateToOrder = { orderId ->
                navController.navigateToDeliveryStatus(orderId)
            },
            onOpenGoogleMaps = { lat, lng ->
                val uri = Uri.parse("google.navigation:q=$lat,$lng")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                context.startActivity(intent)
            },
        )

        composable(DeliveryTab.SETTINGS.route) {
            Column(modifier = Modifier.padding(16.dp)) {
                LanguageSelector(modifier = Modifier.fillMaxWidth())
            }
        }

        deliveryStatusScreen(
            onBack = { navController.popBackStack() },
            onNavigateToMap = { lat, lng ->
                if (lat != null && lng != null) {
                    val uri = Uri.parse("google.navigation:q=$lat,$lng")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    context.startActivity(intent)
                }
            },
        )

        deliveryReceiptScreen(
            onBack = { navController.popBackStack() }
        )
    }

    if (isTablet) {
        // Tablet: NavigationRail on the side
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNav) {
                DeliveryNavRail(navController, currentDestination)
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                )
            }
            NavHost(
                navController = navController,
                startDestination = AUTH_ROUTE,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                builder = navGraphBuilder,
            )
        }
    } else {
        // Phone: Bottom NavigationBar
        Scaffold(
            bottomBar = {
                if (showNav) DeliveryBottomBar(navController, currentDestination)
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AUTH_ROUTE,
                modifier = Modifier.padding(innerPadding),
                builder = navGraphBuilder,
            )
        }
    }
}
