package net.marllex.cafeemanger.manager.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import net.marllex.cafeemanger.feature.manager.tables.TablesScreen
import net.marllex.cafeemanger.feature.manager.users.UsersScreen
import net.marllex.cafeemanger.manager.R

enum class ManagerTab(
    val route: String,
    val title: Int,
    val icon: ImageVector
) {
    DASHBOARD("manager/dashboard", R.string.home, Icons.Filled.Dashboard),
    ORDERS("manager/orders", R.string.orders, Icons.Filled.Receipt),
    MENU("manager/menu", R.string.menu, Icons.Filled.Category),
    USERS("manager/users", R.string.staff, Icons.Filled.People),
    PROFILE("manager/profile", R.string.profile, Icons.Filled.Store),
}

@Composable
fun CafeeBottomBar(
    navController: NavController,
    currentDestination: NavDestination?
) {
    // Wrap in Surface to create a "Floating" or custom elevation look
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), // Curved top edges
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            containerColor = Color.Transparent, // Let Surface handle the color
            windowInsets = WindowInsets.navigationBars,
            modifier = Modifier.height(height = 80.dp)
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
                        // Creative touch: Animate icon size/color
                        val iconSize by animateDpAsState(targetValue = if (isSelected) 28.dp else 24.dp)
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = stringResource(tab.title),
                            modifier = Modifier.size(iconSize),
                            tint = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(tab.title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    alwaysShowLabel = false, // Clean look: only show label for selected item
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }
        }
    }
}

@Composable
fun ManagerNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = ManagerTab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) CafeeBottomBar(navController, currentDestination)
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

            composable(ManagerTab.DASHBOARD.route) {
                DashboardScreen()
            }

            composable(ManagerTab.ORDERS.route) {
                OrdersScreen()
            }

            composable(ManagerTab.MENU.route) {
                MenuTabContent()
            }

            composable(ManagerTab.USERS.route) {
                UsersScreen()
            }

            composable(ManagerTab.PROFILE.route) {
                ProfileTabContent()
            }
        }
    }
}

@Composable
private fun MenuTabContent() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.categories), stringResource(R.string.items))

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            // 1. Match your theme's surface color
            containerColor = MaterialTheme.colorScheme.surface,
            // 2. The divider color between tabs and content
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            },
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary // Brand orange/brown
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    // 3. Set specific colors for Selected vs Unselected text
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            // Optional: make selected text slightly bolder
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Content area
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> CategoriesScreen()
                1 -> ItemsScreen()
            }
        }
    }
}

@Composable
private fun ProfileTabContent() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.restaurant),
        stringResource(R.string.tables),
        stringResource(R.string.analytics),
        stringResource(R.string.settings)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Use ScrollableTabRow instead of TabRow to prevent squishing
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp, // Adds padding at the start so the first tab isn't glued to the edge
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) },
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
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
                            // Prevent text wrapping to a second line
                            maxLines = 1
                        )
                    }
                )
            }
        }

        // Screen Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> RestaurantProfileScreen()
                1 -> TablesScreen()
                2 -> AnalyticsScreen()
                3 -> SettingsContent()
            }
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
