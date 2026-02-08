package net.marllex.cafeemanger.manager.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
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
    val profileVm: RestaurantProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val profileState by profileVm.uiState.collectAsStateWithLifecycle()
    val vendor = profileState.vendor

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.categories),
        stringResource(R.string.items),
        stringResource(R.string.stock),
        stringResource(R.string.digital_menu),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                3 -> DigitalMenuSection(
                    vendorId = vendor?.id,
                    customMenuUrl = vendor?.digitalMenuUrl,
                )
            }
        }
    }
}

// ─── Digital Menu QR + Link ──────────────────────────────────────
@Composable
private fun DigitalMenuSection(vendorId: String?, customMenuUrl: String?) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMsg = stringResource(R.string.link_copied)

    // Auto-construct URL from BASE_URL + /menu/{vendorId}, or use custom URL
    val menuUrl = remember(vendorId, customMenuUrl) {
        when {
            !customMenuUrl.isNullOrBlank() -> customMenuUrl
            !vendorId.isNullOrBlank() -> {
                val base = net.marllex.cafeemanger.core.network.BuildConfig.BASE_URL.trimEnd('/')
                "$base/menu/$vendorId"
            }
            else -> null
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!menuUrl.isNullOrBlank()) {
                val qrBitmap = remember(menuUrl) { generateQrBitmap(menuUrl, 512) }

                Icon(
                    Icons.Filled.QrCode2, null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.digital_menu),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.scan_to_view_menu),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Box(Modifier.padding(20.dp)) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(220.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Link display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.digital_menu_link),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            menuUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Menu URL", menuUrl))
                        scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy_link))
                }
            } else {
                // No vendor loaded yet
                Icon(
                    Icons.Filled.QrCode2, null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.no_digital_menu),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { idx ->
        if (bitMatrix[idx % size, idx / size]) Color.Black.toArgb() else Color.White.toArgb()
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
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
