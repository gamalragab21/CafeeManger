package net.marllex.waselak.manager.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.ui.components.LanguageSelector
import net.marllex.waselak.core.ui.components.QrCodeImage
import net.marllex.waselak.core.ui.components.SignOutButton
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import net.marllex.waselak.feature.auth.biometric.BiometricResult
import net.marllex.waselak.feature.auth.biometric.rememberBiometricAuthenticator
import net.marllex.waselak.feature.auth.navigation.AUTH_ROUTE
import net.marllex.waselak.feature.auth.navigation.authScreen
import net.marllex.waselak.feature.manager.analytics.AnalyticsScreen
import net.marllex.waselak.feature.manager.analytics.ExportScreen
import net.marllex.waselak.feature.manager.categories.CategoriesScreen
import net.marllex.waselak.feature.manager.customers.CustomersScreen
import net.marllex.waselak.feature.cashier.receipt.navigation.navigateToReceipt
import net.marllex.waselak.feature.cashier.receipt.navigation.receiptScreen
import net.marllex.waselak.feature.manager.chatbot.navigation.chatbotScreen
import net.marllex.waselak.feature.manager.dashboard.DashboardScreen
import net.marllex.waselak.feature.manager.items.ItemsScreen
import net.marllex.waselak.feature.manager.orders.OrdersScreen
import net.marllex.waselak.feature.manager.staff.StaffScreen
import net.marllex.waselak.feature.manager.staff.WorkerQrCodeScreen
import net.marllex.waselak.feature.manager.stock.StockScreen
import net.marllex.waselak.feature.manager.tables.TablesScreen
import net.marllex.waselak.feature.manager.users.UsersScreen
import net.marllex.waselak.manager.offline.OfflineSettingsScreen
import net.marllex.waselak.manager.taxplaces.TaxPlacesScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*

enum class ManagerTab(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("manager/dashboard", "Home", Icons.Filled.Dashboard),
    ORDERS("manager/orders", "Orders", Icons.AutoMirrored.Filled.FormatListBulleted),
    MENU("manager/menu", "Menu", Icons.Filled.Restaurant),
    USERS("manager/users", "Staff", Icons.Filled.People),
    PROFILE("manager/profile", "Profile", Icons.Filled.Person),
}

@Composable
private fun localizedTabTitle(tab: ManagerTab): String = when (tab) {
    ManagerTab.DASHBOARD -> stringResource(CoreRes.string.nav_home)
    ManagerTab.ORDERS -> stringResource(CoreRes.string.nav_orders)
    ManagerTab.MENU -> stringResource(CoreRes.string.nav_menu)
    ManagerTab.USERS -> stringResource(CoreRes.string.nav_staff)
    ManagerTab.PROFILE -> stringResource(CoreRes.string.nav_profile)
}

// --- Adaptive Bottom Bar (phone) ---
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
            val title = localizedTabTitle(tab)
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
                        contentDescription = title,
                    )
                },
                label = {
                    Text(
                        text = title,
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

// --- Navigation Rail (tablet) ---
@Composable
private fun ManagerNavRail(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ManagerTab.entries.forEach { tab ->
            val title = localizedTabTitle(tab)
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
                        contentDescription = title,
                    )
                },
                label = {
                    Text(
                        text = title,
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

// --- Main Nav Host ---
@Composable
fun ManagerNavHost(authRepository: AuthRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()
    val biometricAuth = rememberBiometricAuthenticator()

    // Force-navigate to login if session is invalidated (e.g. logged in on another device)
    val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = true)
    LaunchedEffect(Unit) {
        snapshotFlow { isLoggedIn }
            .drop(1) // skip initial value — NavHost graph may not be ready yet
            .collect { loggedIn ->
                if (!loggedIn) {
                    navController.navigate(AUTH_ROUTE) { popUpTo(0) { inclusive = true } }
                }
            }
    }

    // Sign-out with biometric verification (system prompt on Android/iOS)
    val onSignOut: () -> Unit = remember(scope) {
        {
            scope.launch {
                val canProceed = if (biometricAuth.isAvailable()) {
                    // Trigger system auth prompt (fingerprint / Face ID / PIN)
                    when (biometricAuth.authenticate("Sign out verification")) {
                        is BiometricResult.Success -> true
                        is BiometricResult.NotAvailable -> true
                        else -> false // Cancelled or Error — don't sign out
                    }
                } else {
                    true // Desktop — no system auth, proceed directly
                }
                if (canProceed) {
                    authRepository.logout()
                    navController.navigate(AUTH_ROUTE) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    val showNav = ManagerTab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

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
                    composable(ManagerTab.DASHBOARD.route) {
                        DashboardScreen(
                            onNavigateToChatbot = {
                                navController.navigate("chatbot")
                            }
                        )
                    }
                    composable(ManagerTab.ORDERS.route) {
                        OrdersScreen(
                            onViewReceipt = { orderId ->
                                navController.navigateToReceipt(orderId)
                            },
                        )
                    }
                    composable(ManagerTab.MENU.route) { MenuTabContent() }
                    composable(ManagerTab.USERS.route) {
                        StaffTabContent(
                            onNavigateToWorkerQrCode = { workerId ->
                                navController.navigate("worker_qr_code/$workerId")
                            }
                        )
                    }
                    composable(ManagerTab.PROFILE.route) { ProfileTabContent(onSignOut = onSignOut) }
                    composable(
                        route = "worker_qr_code/{workerId}",
                        arguments = listOf(navArgument("workerId") { type = NavType.StringType })
                    ) {
                        WorkerQrCodeScreen(
                            onNavigateBack = { navController.navigateUp() }
                        )
                    }
                    chatbotScreen(
                        onNavigateBack = { navController.navigateUp() }
                    )
                    composable("export") {
                        ExportScreen(
                            onNavigateBack = { navController.navigateUp() }
                        )
                    }
                    receiptScreen(
                        onBack = { navController.navigateUp() },
                    )
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
                    composable(ManagerTab.DASHBOARD.route) {
                        DashboardScreen(
                            onNavigateToChatbot = {
                                navController.navigate("chatbot")
                            }
                        )
                    }
                    composable(ManagerTab.ORDERS.route) {
                        OrdersScreen(
                            onViewReceipt = { orderId ->
                                navController.navigateToReceipt(orderId)
                            },
                        )
                    }
                    composable(ManagerTab.MENU.route) { MenuTabContent() }
                    composable(ManagerTab.USERS.route) {
                        StaffTabContent(
                            onNavigateToWorkerQrCode = { workerId ->
                                navController.navigate("worker_qr_code/$workerId")
                            }
                        )
                    }
                    composable(ManagerTab.PROFILE.route) { ProfileTabContent(onSignOut = onSignOut) }
                    composable(
                        route = "worker_qr_code/{workerId}",
                        arguments = listOf(navArgument("workerId") { type = NavType.StringType })
                    ) {
                        WorkerQrCodeScreen(
                            onNavigateBack = { navController.navigateUp() }
                        )
                    }
                    chatbotScreen(
                        onNavigateBack = { navController.navigateUp() }
                    )
                    composable("export") {
                        ExportScreen(
                            onNavigateBack = { navController.navigateUp() }
                        )
                    }
                    receiptScreen(
                        onBack = { navController.navigateUp() },
                    )
                }
            }
        }
    }
}

// --- Staff Sub-Tabs (Staff + Customers) ---
@Composable
private fun StaffTabContent(
    onNavigateToWorkerQrCode: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(CoreRes.string.nav_staff),
        stringResource(CoreRes.string.nav_customers),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
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
            },
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
                        )
                    },
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> StaffScreen(
                    onNavigateToWorkerQrCode = onNavigateToWorkerQrCode,
                )
                1 -> CustomersScreen()
            }
        }
    }
}

// --- Menu Sub-Tabs ---
@Composable
private fun MenuTabContent() {
    val profileVm: RestaurantProfileViewModel = org.koin.compose.viewmodel.koinViewModel()
    val profileState by profileVm.uiState.collectAsState()
    val vendor = profileState.vendor

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(CoreRes.string.tab_categories),
        stringResource(CoreRes.string.tab_items),
        stringResource(CoreRes.string.tab_stock),
        stringResource(CoreRes.string.tab_digital_menu),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
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

// --- Digital Menu QR + Link ---
@Composable
private fun DigitalMenuSection(vendorId: String?, customMenuUrl: String?) {
    val platformActions = rememberPlatformActions()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMsg = stringResource(CoreRes.string.link_copied)

    val menuUrl = remember(vendorId, customMenuUrl) {
        when {
            !customMenuUrl.isNullOrBlank() -> customMenuUrl
            !vendorId.isNullOrBlank() -> {
                val base = KoinPlatform.getKoin().get<String>(named("baseUrl")).trimEnd('/')
                "$base/menu/$vendorId"
            }
            else -> null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val screenWidth = maxWidth.value.toInt()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (screenWidth > 600) Arrangement.Center else Arrangement.Top,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 480.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!menuUrl.isNullOrBlank()) {
                        Icon(
                            Icons.Filled.QrCode2, null,
                            modifier = Modifier.size(if (screenWidth > 600) 48.dp else 32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(CoreRes.string.digital_menu),
                            style = if (screenWidth > 600) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(CoreRes.string.scan_to_view_menu),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(24.dp))

                        // QR Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Box(Modifier.padding(if (screenWidth > 600) 32.dp else 20.dp)) {
                                QrCodeImage(
                                    content = menuUrl,
                                    modifier = Modifier.size(if (screenWidth > 600) 280.dp else 220.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        // Link display
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(CoreRes.string.digital_menu_link),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    menuUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                platformActions.copyToClipboard(menuUrl)
                                scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(CoreRes.string.copy_link), style = MaterialTheme.typography.titleMedium)
                        }

                        TextButton(
                            onClick = {
                                platformActions.shareText(menuUrl, "Share Link")
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(CoreRes.string.share_with_customers))
                        }

                    } else {
                        // Empty State
                        EmptyMenuState()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMenuState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.QrCode2, null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(CoreRes.string.no_digital_menu),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Profile Sub-Tabs ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTabContent(onSignOut: () -> Unit) {
    val profileVm: RestaurantProfileViewModel = org.koin.compose.viewmodel.koinViewModel()
    val profileState by profileVm.uiState.collectAsState()
    val vendor = profileState.vendor

    var activeSubScreen by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val mainTabs = listOf(
        stringResource(CoreRes.string.tab_store),
        stringResource(CoreRes.string.tab_analytics),
        stringResource(CoreRes.string.tab_settings),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        when (activeSubScreen) {
            "tables" -> {
                TopAppBar(
                    title = { Text(stringResource(CoreRes.string.tables)) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                TablesScreen()
            }

            "users" -> {
                TopAppBar(
                    title = { Text(stringResource(CoreRes.string.roles_permissions)) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                UsersScreen()
            }

            "tax_places" -> {
                TopAppBar(
                    title = { Text(stringResource(CoreRes.string.tax_places)) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                TaxPlacesScreen()
            }

            "export" -> {
                TopAppBar(
                    title = { Text(stringResource(CoreRes.string.export_data)) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                ExportScreen(
                    onNavigateBack = { activeSubScreen = null }
                )
            }

            "store_configuration" -> {
                TopAppBar(
                    title = { Text(stringResource(CoreRes.string.store_configuration)) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                StoreConfigurationScreen()
            }

            "offline_mode" -> {
                TopAppBar(
                    title = { Text(stringResource(CoreRes.string.offline_mode_settings)) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                OfflineSettingsScreen()
            }

            else -> {
                TabRow(
                    selectedTabIndex = selectedTab,
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
                    },
                ) {
                    mainTabs.forEachIndexed { index, title ->
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
                            },
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> RestaurantProfileScreen()
                        1 -> AnalyticsScreen()
                        2 -> SettingsContent(
                            vendor = vendor,
                            onSignOut = onSignOut,
                            onNavigateToTables = { activeSubScreen = "tables" },
                            onNavigateToUsers = { activeSubScreen = "users" },
                            onNavigateToTaxPlaces = { activeSubScreen = "tax_places" },
                            onNavigateToConfiguration = { activeSubScreen = "store_configuration" },
                            onNavigateToOfflineSettings = { activeSubScreen = "offline_mode" },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    vendor: net.marllex.waselak.core.model.Vendor?,
    onSignOut: () -> Unit,
    onNavigateToTables: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToTaxPlaces: () -> Unit,
    onNavigateToConfiguration: () -> Unit,
    onNavigateToOfflineSettings: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 32.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(CoreRes.string.store_features),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (vendor?.enableTables != false) {
                SettingsNavigationCard(
                    icon = Icons.Filled.TableBar,
                    title = stringResource(CoreRes.string.tables),
                    onClick = onNavigateToTables,
                )
            }

            SettingsNavigationCard(
                icon = Icons.Filled.Security,
                title = stringResource(CoreRes.string.roles_permissions),
                onClick = onNavigateToUsers,
            )

            SettingsNavigationCard(
                icon = Icons.Filled.LocalShipping,
                title = stringResource(CoreRes.string.tax_places),
                onClick = onNavigateToTaxPlaces,
            )

            SettingsNavigationCard(
                icon = Icons.Filled.Settings,
                title = stringResource(CoreRes.string.store_configuration),
                onClick = onNavigateToConfiguration,
            )

            SettingsNavigationCard(
                icon = Icons.Filled.CloudOff,
                title = stringResource(CoreRes.string.offline_mode_settings),
                onClick = onNavigateToOfflineSettings,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(CoreRes.string.tab_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LanguageSelector(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(8.dp))
            SignOutButton(onSignOut = onSignOut)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsNavigationCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StoreConfigurationScreen() {
    val viewModel: RestaurantProfileViewModel = org.koin.compose.viewmodel.koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val vendor = state.vendor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(CoreRes.string.biometric_required),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(CoreRes.string.biometric_required_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = vendor?.biometricRequired == true,
                        onCheckedChange = { newValue ->
                            viewModel.updateStoreConfiguration(biometricRequired = newValue)
                        },
                    )
                }
            }
        }
    }
}
