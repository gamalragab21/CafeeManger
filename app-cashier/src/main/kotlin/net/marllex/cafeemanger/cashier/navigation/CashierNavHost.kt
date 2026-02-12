package net.marllex.cafeemanger.cashier.navigation

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.TableBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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
import net.marllex.cafeemanger.cashier.R
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.model.UserRole
import net.marllex.cafeemanger.core.ui.R as CoreR
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

// Tabs in bottom navigation (most-used)
enum class CashierTab(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    POS("cashier/pos", titleRes = R.string.cashier_new_order, Icons.Filled.PointOfSale),
    ORDERS("cashier/orders", titleRes = R.string.cashier_orders, icon = Icons.Filled.History),
    TABLES("cashier/tables", titleRes = R.string.cashier_tables, icon = Icons.Filled.TableBar),
}

// Items in drawer (less-used)
enum class CashierDrawerItem(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    DELIVERY("cashier/delivery", R.string.cashier_delivery, Icons.Filled.DeliveryDining),
    ANNOUNCEMENTS("cashier/announcements", R.string.cashier_announcements, Icons.Filled.Campaign),
    ATTENDANCE("cashier/attendance", R.string.cashier_attendance, Icons.Filled.Fingerprint),
    PROFILE("cashier/profile", R.string.cashier_profile, Icons.Filled.Person),
}

private val allRoutes = CashierTab.entries.map { it.route } + CashierDrawerItem.entries.map { it.route }

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
                ),
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
                ),
            )
        }
    }
}

// ─── Drawer Content ──────────────────────────────────────────────
@Composable
private fun CashierDrawerContent(
    navController: NavController,
    currentDestination: NavDestination?,
    userName: String?,
    userRole: String?,
    onItemClick: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = userName ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (userRole != null) {
                Text(
                    text = userRole,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        CashierDrawerItem.entries.forEach { item ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == item.route } == true

            NavigationDrawerItem(
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.titleRes)) },
                selected = isSelected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                    onItemClick()
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

// ─── Profile Screen ──────────────────────────────────────────────
@Composable
private fun CashierProfileScreen(
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

        // Account Info section
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

        // App Settings section
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
fun CashierNavHost(authRepository: AuthRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val scope = rememberCoroutineScope()
    val currentUser by authRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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

    val showNav = allRoutes.any { route ->
        currentDestination?.hierarchy?.any { it.route == route } == true
    }

    val roleLabel = formatRoleLabel(currentUser?.role)

    val navGraphBuilder: androidx.navigation.NavGraphBuilder.() -> Unit = {
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
            },
        )
        composable(CashierTab.ORDERS.route) {
            OrdersScreen(
                onViewReceipt = { orderId ->
                    navController.navigateToReceipt(orderId)
                },
            )
        }
        composable(CashierTab.TABLES.route) { TablesScreen(readOnly = true) }
        composable(CashierDrawerItem.DELIVERY.route) { DeliveryDashboardScreen() }
        composable(CashierDrawerItem.ANNOUNCEMENTS.route) { AnnouncementsScreen() }
        composable(CashierDrawerItem.ATTENDANCE.route) { AttendanceScreen() }
        composable(CashierDrawerItem.PROFILE.route) {
            CashierProfileScreen(
                userName = currentUser?.name,
                userPhone = currentUser?.phone,
                userEmail = currentUser?.email,
                userRole = roleLabel,
                onSignOut = onSignOut,
            )
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

    if (isTablet) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showNav,
            drawerContent = {
                CashierDrawerContent(
                    navController = navController,
                    currentDestination = currentDestination,
                    userName = currentUser?.name,
                    userRole = roleLabel,
                    onItemClick = { scope.launch { drawerState.close() } },
                )
            },
        ) {
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
                        TopAppBar(
                            title = {
                                currentUser?.name?.let { name ->
                                    Text(
                                        text = stringResource(CoreR.string.welcome_message, name),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
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
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showNav,
            drawerContent = {
                CashierDrawerContent(
                    navController = navController,
                    currentDestination = currentDestination,
                    userName = currentUser?.name,
                    userRole = roleLabel,
                    onItemClick = { scope.launch { drawerState.close() } },
                )
            },
        ) {
            Scaffold(
                topBar = {
                    if (showNav) {
                        TopAppBar(
                            title = {
                                currentUser?.name?.let { name ->
                                    Text(
                                        text = stringResource(CoreR.string.welcome_message, name),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                },
                bottomBar = {
                    if (showNav) CashierBottomBar(navController, currentDestination)
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
}
