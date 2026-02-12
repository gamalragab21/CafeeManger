package net.marllex.cafeemanger.delivery.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.model.UserRole
import net.marllex.cafeemanger.core.ui.components.LanguageSelector
import net.marllex.cafeemanger.core.ui.components.SignOutButton
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
import net.marllex.cafeemanger.feature.manager.staff.AnnouncementsScreen
import net.marllex.cafeemanger.core.ui.R as CoreR

enum class DeliveryTab(
    val route: String,
    val title: Int,
    val icon: ImageVector,
) {
    ORDERS("delivery/orders", R.string.my_orders_menu, Icons.Filled.DeliveryDining),
    HISTORY("delivery/history", R.string.history, Icons.Filled.History),
    ANNOUNCEMENTS("delivery/announcements", R.string.announcements_menu, Icons.Filled.Campaign),
    MAP("delivery/map", R.string.map, Icons.Filled.Map),
    PROFILE("delivery/profile", R.string.delivery_profile, Icons.Filled.Person),
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
                ),
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
                ),
            )
        }
    }
}

// ─── Profile Screen ──────────────────────────────────────────────
@Composable
private fun DeliveryProfileScreen(
    userName: String?,
    userPhone: String?,
    userEmail: String?,
    userRole: String?,
    onSignOut: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = if (isTablet) 48.dp else 16.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Profile header card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = userName ?: stringResource(R.string.not_available),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = userRole ?: stringResource(R.string.not_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Account Info
        item {
            Text(
                text = stringResource(R.string.account_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    ProfileInfoRow(
                        label = stringResource(R.string.your_name),
                        value = userName ?: stringResource(R.string.not_available),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProfileInfoRow(
                        label = stringResource(R.string.your_phone),
                        value = userPhone ?: stringResource(R.string.not_available),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProfileInfoRow(
                        label = stringResource(R.string.your_email),
                        value = userEmail ?: stringResource(R.string.not_available),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProfileInfoRow(
                        label = stringResource(R.string.your_role),
                        value = userRole ?: stringResource(R.string.not_available),
                    )
                }
            }
        }

        // App Settings
        item {
            Text(
                text = stringResource(R.string.app_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.language),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LanguageSelector(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Sign Out
        item {
            Spacer(Modifier.height(8.dp))
            SignOutButton(onSignOut = onSignOut)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun formatRoleLabel(role: UserRole?): String {
    return when (role) {
        UserRole.MANAGER -> stringResource(CoreR.string.role_manager)
        UserRole.CASHIER -> stringResource(CoreR.string.role_cashier)
        UserRole.DELIVERY -> stringResource(CoreR.string.role_delivery)
        null -> ""
    }
}

// ─── Main Nav Host ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryNavHost(authRepository: AuthRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
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

    val showNav = DeliveryTab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    val roleLabel = formatRoleLabel(currentUser?.role)

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
            },
        )

        deliveryHistoryScreen(
            onNavigateToReceipt = { orderId ->
                navController.navigateToDeliveryReceipt(orderId)
            },
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

        composable(DeliveryTab.ANNOUNCEMENTS.route) { AnnouncementsScreen() }

        composable(DeliveryTab.PROFILE.route) {
            DeliveryProfileScreen(
                userName = currentUser?.name,
                userPhone = currentUser?.phone,
                userEmail = currentUser?.email,
                userRole = roleLabel,
                onSignOut = onSignOut,
            )
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
            onBack = { navController.popBackStack() },
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
            Column(modifier = Modifier
                .weight(1f)
                .fillMaxHeight()) {
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
                        .fillMaxHeight(),
                    builder = navGraphBuilder,
                )
            }
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
                if (showNav) DeliveryBottomBar(navController, currentDestination)
            },
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
