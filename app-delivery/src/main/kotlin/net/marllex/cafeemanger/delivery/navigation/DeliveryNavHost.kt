package net.marllex.cafeemanger.delivery.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Map
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
import net.marllex.cafeemanger.feature.delivery.status.navigation.deliveryStatusScreen
import net.marllex.cafeemanger.feature.delivery.status.navigation.navigateToDeliveryStatus

enum class DeliveryTab(
    val route: String,
    val title: Int,
    val icon: ImageVector
) {
    ORDERS("delivery/orders", R.string.my_orders_menu, Icons.Filled.DeliveryDining),
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
                NavigationBar {
                    DeliveryTab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = stringResource(tab.title)
                                )
                            },
                            label = { Text(stringResource(tab.title)) },
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
                    navController.navigate(DeliveryTab.ORDERS.route) {
                        popUpTo(AUTH_ROUTE) { inclusive = true }
                    }
                },
                appType = "DELIVERY",
            )

            deliveryOrdersScreen(
                onNavigateToOrder = { orderId ->
                    navController.navigateToDeliveryStatus(orderId)
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
        }
    }
}
