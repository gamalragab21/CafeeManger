package net.marllex.cafeemanger.manager.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import net.marllex.cafeemanger.core.ui.components.LanguageSelector
import net.marllex.cafeemanger.feature.auth.navigation.AUTH_ROUTE
import net.marllex.cafeemanger.feature.auth.navigation.authScreen
import net.marllex.cafeemanger.feature.manager.analytics.AnalyticsScreen
import net.marllex.cafeemanger.feature.manager.categories.CategoriesScreen
import net.marllex.cafeemanger.feature.manager.dashboard.DashboardScreen
import net.marllex.cafeemanger.feature.manager.items.ItemsScreen
import net.marllex.cafeemanger.feature.manager.orders.OrdersScreen
import net.marllex.cafeemanger.feature.manager.stock.StockScreen
import net.marllex.cafeemanger.feature.manager.tables.TablesScreen
import net.marllex.cafeemanger.feature.manager.users.UsersScreen
import net.marllex.cafeemanger.manager.R
import net.marllex.cafeemanger.manager.taxplaces.TaxPlacesScreen

enum class ManagerTab(
    val route: String,
    val title: Int,
    val icon: ImageVector
) {
    DASHBOARD("manager/dashboard", R.string.home, Icons.Filled.Dashboard),
    ORDERS("manager/orders", R.string.orders, Icons.Filled.Receipt),
    MENU("manager/menu", R.string.menu, Icons.Filled.Category),
    USERS("manager/users", R.string.staff, Icons.Filled.People),
    PROFILE("manager/profile", R.string.profile, Icons.Filled.Person),
}

// ─── Adaptive Bottom Bar (phone) ─────────────────────────────────
@Composable
private fun ManagerBottomBar(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        ManagerTab.entries.forEach { tab ->
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
private fun ManagerNavRail(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ManagerTab.entries.forEach { tab ->
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
fun ManagerNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val showNav = ManagerTab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    if (isTablet) {
        // Tablet: NavigationRail on the side
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNav) {
                ManagerNavRail(navController, currentDestination)
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
                    .fillMaxHeight()
            ) {
                authScreen(
                    onLoginSuccess = {
                        navController.navigate(ManagerTab.DASHBOARD.route) {
                            popUpTo(AUTH_ROUTE) { inclusive = true }
                        }
                    },
                    appType = "MANAGER",
                )
                composable(ManagerTab.DASHBOARD.route) { DashboardScreen() }
                composable(ManagerTab.ORDERS.route) { OrdersScreen() }
                composable(ManagerTab.MENU.route) { MenuTabContent() }
                composable(ManagerTab.USERS.route) { UsersScreen() }
                composable(ManagerTab.PROFILE.route) { ProfileTabContent() }
            }
        }
    } else {
        // Phone: Bottom NavigationBar
        Scaffold(
            bottomBar = {
                if (showNav) ManagerBottomBar(navController, currentDestination)
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AUTH_ROUTE,
                modifier = Modifier.padding(innerPadding)
            ) {
                authScreen(
                    onLoginSuccess = {
                        navController.navigate(ManagerTab.DASHBOARD.route) {
                            popUpTo(AUTH_ROUTE) { inclusive = true }
                        }
                    },
                    appType = "MANAGER",
                )
                composable(ManagerTab.DASHBOARD.route) { DashboardScreen() }
                composable(ManagerTab.ORDERS.route) { OrdersScreen() }
                composable(ManagerTab.MENU.route) { MenuTabContent() }
                composable(ManagerTab.USERS.route) { UsersScreen() }
                composable(ManagerTab.PROFILE.route) { ProfileTabContent() }
            }
        }
    }
}

// ─── Menu Sub-Tabs ───────────────────────────────────────────────
@Composable
private fun MenuTabContent() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.categories),
        stringResource(R.string.items),
        stringResource(R.string.stock),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            },
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> CategoriesScreen()
                1 -> ItemsScreen()
                2 -> StockScreen()
            }
        }
    }
}

// ─── Profile Sub-Tabs ────────────────────────────────────────────
@Composable
private fun ProfileTabContent() {
    // Build tabs dynamically based on vendor feature flags
    val profileVm: RestaurantProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val profileState by profileVm.uiState.collectAsStateWithLifecycle()
    val vendor = profileState.vendor

    val tabEntries = remember(vendor?.enableTables) {
        buildList<Pair<String, @Composable () -> Unit>> {
            add("store" to { RestaurantProfileScreen() })
            if (vendor?.enableTables != false) add("tables" to { TablesScreen() })
            add("analytics" to { AnalyticsScreen() })
            add("tax_places" to { TaxPlacesScreen() })
            add("settings" to { SettingsContent() })
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    // Reset tab if current tab is out of bounds
    if (selectedTab >= tabEntries.size) selectedTab = 0

    val tabLabels = tabEntries.map { (key, _) ->
        when (key) {
            "store" -> stringResource(R.string.store)
            "tables" -> stringResource(R.string.tables)
            "analytics" -> stringResource(R.string.analytics)
            "tax_places" -> stringResource(R.string.tax_places)
            "settings" -> stringResource(R.string.settings)
            else -> key
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        ) {
            tabLabels.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            tabEntries.getOrNull(selectedTab)?.second?.invoke()
        }
    }
}

@Composable
private fun SettingsContent() {
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        LanguageSelector(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
