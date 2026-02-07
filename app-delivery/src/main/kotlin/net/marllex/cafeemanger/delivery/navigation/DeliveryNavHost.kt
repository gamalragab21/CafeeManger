package net.marllex.cafeemanger.delivery.navigation

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import net.marllex.cafeemanger.feature.delivery.orders.navigation.deliveryOrdersScreen
import net.marllex.cafeemanger.feature.delivery.orders.navigation.deliveryReceiptScreen
import net.marllex.cafeemanger.feature.delivery.orders.navigation.navigateToDeliveryReceipt
import net.marllex.cafeemanger.feature.delivery.orders.history.navigation.deliveryHistoryScreen
import net.marllex.cafeemanger.feature.delivery.orders.history.navigation.navigateToDeliveryHistory
import net.marllex.cafeemanger.feature.delivery.status.navigation.deliveryStatusScreen
import net.marllex.cafeemanger.feature.delivery.status.navigation.navigateToDeliveryStatus

enum class DeliveryTab(
    val route: String,
    val title: Int,
    val icon: ImageVector
) {
    ORDERS("delivery/orders", R.string.my_orders_menu, Icons.Filled.DeliveryDining),
    HISTORY("delivery/history", R.string.history, Icons.Filled.History),
    MAP("delivery/map", R.string.map, Icons.Filled.Map),
    SETTINGS("delivery/settings", R.string.settings, Icons.Filled.Settings),
}

@Composable
fun DeliveryNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    val showBottomBar = DeliveryTab.entries.any { tab ->
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
                        DeliveryTab.entries.forEach { tab ->
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
                                        contentDescription = stringResource(tab.title),
                                        modifier = Modifier.size(iconSize),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(tab.title),
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
    }
}
