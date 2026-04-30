package net.marllex.waselak.manager.navigation

import net.marllex.waselak.core.common.format.kFormat
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableBar
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PointOfSale
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
import net.marllex.waselak.core.common.crash.CrashReporter
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
import net.marllex.waselak.feature.manager.offers.OffersScreen
import net.marllex.waselak.feature.manager.orders.OrdersScreen
import net.marllex.waselak.feature.manager.staff.StaffScreen
import net.marllex.waselak.feature.manager.staff.WorkerQrCodeScreen
import net.marllex.waselak.feature.manager.stock.StockScreen
import net.marllex.waselak.feature.manager.tables.TablesScreen
import net.marllex.waselak.feature.manager.users.UsersScreen
import net.marllex.waselak.manager.customercredit.CustomerCreditScreen
import net.marllex.waselak.manager.druginteractions.DrugInteractionsScreen
import net.marllex.waselak.manager.notifications.NotificationsScreen
import net.marllex.waselak.manager.offline.OfflineSettingsScreen
import net.marllex.waselak.manager.returns.ReturnsScreen
import net.marllex.waselak.manager.scheduledorders.ScheduledOrdersScreen
import net.marllex.waselak.manager.suppliers.SuppliersScreen
import net.marllex.waselak.manager.taxplaces.TaxPlacesScreen
import net.marllex.waselak.manager.cashdrawer.ManagerCashDrawerScreen
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.ui.components.AboutScreen
import net.marllex.waselak.core.ui.components.FeatureNotAvailableView
import net.marllex.waselak.core.ui.components.UploadLogsCard
import net.marllex.waselak.config.BuildConfig
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.material3.OutlinedTextField

enum class ManagerTab(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("manager/dashboard", "Home", Icons.Filled.Dashboard),
    ORDERS("manager/orders", "Orders", Icons.AutoMirrored.Filled.FormatListBulleted),
    TABLES("manager/tables", "Tables", Icons.Filled.TableBar),
    MENU("manager/menu", "Menu", Icons.Filled.Restaurant),
    OFFERS("manager/offers", "Offers", Icons.Filled.Star),
    MORE("manager/more", "More", Icons.Filled.MoreHoriz),
}

@Composable
private fun localizedTabTitle(tab: ManagerTab, businessType: String = "RESTAURANT"): String = when (tab) {
    ManagerTab.DASHBOARD -> stringResource(CoreRes.string.nav_home)
    ManagerTab.ORDERS -> when (businessType) {
        "PHARMACY" -> stringResource(CoreRes.string.nav_orders_prescriptions)
        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(CoreRes.string.nav_orders_invoices)
        else -> stringResource(CoreRes.string.nav_orders)
    }
    ManagerTab.TABLES -> stringResource(CoreRes.string.nav_tables)
    ManagerTab.MENU -> when (businessType) {
        "PHARMACY" -> stringResource(CoreRes.string.nav_menu_medicines)
        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(CoreRes.string.nav_menu_products)
        else -> stringResource(CoreRes.string.nav_menu)
    }
    ManagerTab.OFFERS -> stringResource(CoreRes.string.nav_offers)
    ManagerTab.MORE -> stringResource(CoreRes.string.nav_more)
}

/** Returns the right icon for the MENU tab based on business type */
private fun menuTabIcon(businessType: String?): ImageVector = when (businessType) {
    "PHARMACY" -> Icons.Filled.LocalPharmacy
    "SUPERMARKET", "GROCERY", "RETAIL" -> Icons.Filled.ShoppingCart
    "JUICE_BAR", "CAFE" -> Icons.Filled.LocalCafe
    else -> Icons.Filled.Restaurant // RESTAURANT, BAKERY, default
}

// --- Adaptive Bottom Bar (phone) ---
@Composable
private fun ManagerBottomBar(
    navController: NavController,
    currentDestination: NavDestination?,
    visibleTabs: List<ManagerTab> = ManagerTab.entries,
    businessType: String = "RESTAURANT",
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        visibleTabs.forEach { tab ->
            val title = localizedTabTitle(tab, businessType)
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
                        imageVector = if (tab == ManagerTab.MENU) menuTabIcon(businessType) else tab.icon,
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
    visibleTabs: List<ManagerTab> = ManagerTab.entries,
    businessType: String = "RESTAURANT",
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        visibleTabs.forEach { tab ->
            val title = localizedTabTitle(tab, businessType)
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
                        imageVector = if (tab == ManagerTab.MENU) menuTabIcon(businessType) else tab.icon,
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

    // Log navigation to Sentry
    LaunchedEffect(currentDestination?.route) {
        currentDestination?.route?.let { route ->
            CrashReporter.logNavigation("manager", route)
        }
    }
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
    val signOutVerificationText = stringResource(CoreRes.string.sign_out_verification)
    val onSignOut: () -> Unit = remember(scope, signOutVerificationText) {
        {
            scope.launch {
                val canProceed = if (biometricAuth.isAvailable()) {
                    // Trigger system auth prompt (fingerprint / Face ID / PIN)
                    when (biometricAuth.authenticate(signOutVerificationText)) {
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

    // Get vendor config for tables tab visibility (works offline via cached vendor)
    val profileVm: RestaurantProfileViewModel = org.koin.compose.viewmodel.koinViewModel()
    val profileState by profileVm.uiState.collectAsState()
    val vendor = profileState.vendor

    // Compute visible tabs using domain features (works offline via cached vendor)
    val domainFeatures = remember(vendor) {
        if (vendor != null) net.marllex.waselak.core.model.DomainFeatures.forVendor(vendor)
        else net.marllex.waselak.core.model.DomainFeatures.forType("RESTAURANT")
    }

    val visibleTabs = remember(vendor?.enableTables, domainFeatures) {
        ManagerTab.entries.filter { tab ->
            when (tab) {
                ManagerTab.TABLES -> vendor?.enableTables != false && domainFeatures.hasTables
                ManagerTab.OFFERS -> domainFeatures.hasOffers
                else -> true
            }
        }
    }

    val showNav = visibleTabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // Tablet: NavigationRail on the side
            Row(modifier = Modifier.fillMaxSize()) {
                if (showNav) {
                    ManagerNavRail(navController, currentDestination, visibleTabs, vendor?.businessType ?: "RESTAURANT")
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
                            businessType = vendor?.businessType,
                            enableReturns = vendor?.enableReturns == true,
                        )
                    }
                    composable(ManagerTab.MENU.route) { MenuTabContent() }
                    composable(ManagerTab.OFFERS.route) { OffersScreen() }
                    composable(ManagerTab.TABLES.route) {
                        if (vendor?.enableTables == false) {
                            FeatureNotAvailableView()
                        } else {
                            TablesScreen()
                        }
                    }
                    composable(ManagerTab.MORE.route) {
                        MoreTabContent(
                            onSignOut = onSignOut,
                            onNavigateToWorkerQrCode = { workerId ->
                                navController.navigate("worker_qr_code/$workerId")
                            },
                            onNavigateToExport = {
                                navController.navigate("export")
                            },
                        )
                    }
                    composable(
                        route = "worker_qr_code/{workerId}",
                        arguments = listOf(navArgument("workerId") { type = NavType.StringType })
                    ) {
                        if (vendor?.enableWorkerQrcode == false) {
                            FeatureNotAvailableView()
                        } else {
                            WorkerQrCodeScreen(
                                onNavigateBack = { navController.navigateUp() }
                            )
                        }
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
                    if (showNav) ManagerBottomBar(navController, currentDestination, visibleTabs, vendor?.businessType ?: "RESTAURANT")
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
                            businessType = vendor?.businessType,
                            enableReturns = vendor?.enableReturns == true,
                        )
                    }
                    composable(ManagerTab.MENU.route) { MenuTabContent() }
                    composable(ManagerTab.OFFERS.route) { OffersScreen() }
                    composable(ManagerTab.TABLES.route) {
                        if (vendor?.enableTables == false) {
                            FeatureNotAvailableView()
                        } else {
                            TablesScreen()
                        }
                    }
                    composable(ManagerTab.MORE.route) {
                        MoreTabContent(
                            onSignOut = onSignOut,
                            onNavigateToWorkerQrCode = { workerId ->
                                navController.navigate("worker_qr_code/$workerId")
                            },
                            onNavigateToExport = {
                                navController.navigate("export")
                            },
                        )
                    }
                    composable(
                        route = "worker_qr_code/{workerId}",
                        arguments = listOf(navArgument("workerId") { type = NavType.StringType })
                    ) {
                        if (vendor?.enableWorkerQrcode == false) {
                            FeatureNotAvailableView()
                        } else {
                            WorkerQrCodeScreen(
                                onNavigateBack = { navController.navigateUp() }
                            )
                        }
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

// --- Menu Sub-Tabs ---
@Composable
private fun MenuTabContent() {
    val profileVm: RestaurantProfileViewModel = org.koin.compose.viewmodel.koinViewModel()
    val profileState by profileVm.uiState.collectAsState()
    val vendor = profileState.vendor

    var selectedTab by remember { mutableIntStateOf(0) }
    data class MenuTab(val title: String, val key: String)
    val bt = vendor?.businessType ?: "RESTAURANT"
    val categoriesLabel = when (bt) {
        "PHARMACY" -> stringResource(CoreRes.string.tab_categories_sections)
        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(CoreRes.string.tab_categories_sections)
        else -> stringResource(CoreRes.string.tab_categories)
    }
    val itemsLabel = when (bt) {
        "PHARMACY" -> stringResource(CoreRes.string.tab_items_medicines)
        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(CoreRes.string.tab_items_products)
        else -> stringResource(CoreRes.string.tab_items)
    }
    val stockLabel = when (bt) {
        "SUPERMARKET", "GROCERY", "RETAIL", "PHARMACY" -> stringResource(CoreRes.string.tab_stock_inventory)
        else -> stringResource(CoreRes.string.tab_stock)
    }
    val allTabs = buildList {
        add(MenuTab(categoriesLabel, "categories"))
        add(MenuTab(itemsLabel, "items"))
        add(MenuTab(stockLabel, "stock"))
        if (vendor?.enableDigitalMenu != false) {
            add(MenuTab(stringResource(CoreRes.string.tab_digital_menu), "digital_menu"))
        }
    }
    val tabs = allTabs.map { it.title }

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
            when (allTabs.getOrNull(selectedTab)?.key) {
                "categories" -> CategoriesScreen()
                "items" -> ItemsScreen()
                "stock" -> {
                    if (vendor?.enableStock == false) {
                        FeatureNotAvailableView()
                    } else {
                        StockScreen(showRecipes = vendor?.enableRecipe != false)
                    }
                }
                "digital_menu" -> {
                    if (vendor?.enableDigitalMenu == false) {
                        FeatureNotAvailableView()
                    } else {
                        DigitalMenuSection(
                            vendorId = vendor?.id,
                            customMenuUrl = vendor?.digitalMenuUrl,
                        )
                    }
                }
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
    val shareLinkTitle = stringResource(CoreRes.string.share_link)

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
        containerColor = MaterialTheme.colorScheme.background
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
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.outlineVariant
                            ),
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
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                platformActions.shareText(menuUrl, shareLinkTitle)
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

// --- More Tab Content ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreTabContent(
    onSignOut: () -> Unit,
    onNavigateToWorkerQrCode: (String) -> Unit = {},
    onNavigateToExport: () -> Unit = {},
) {
    val profileVm: RestaurantProfileViewModel = org.koin.compose.viewmodel.koinViewModel()
    val profileState by profileVm.uiState.collectAsState()
    val vendor = profileState.vendor

    var activeSubScreen by remember { mutableStateOf<String?>(null) }
    var settingsSubScreen by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (activeSubScreen) {
            "analytics" -> {
                if (vendor?.enableAnalytics == false) {
                    TopAppBar(
                        title = { Text(stringResource(CoreRes.string.tab_analytics)) },
                        navigationIcon = {
                            IconButton(onClick = { activeSubScreen = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    FeatureNotAvailableView()
                } else {
                    // Derive real feature capabilities the same way the More menu does
                    // (DomainFeatures + vendor-level toggles). This prevents the Pharmacy
                    // tab from leaking into restaurants/cafes when a stray "PHARMACY"
                    // value ends up in businessType, and respects the owner's
                    // enable/disable toggles in Store Configuration.
                    val v = vendor
                    val analyticsFeatures = if (v != null) net.marllex.waselak.core.model.DomainFeatures.forVendor(v)
                        else net.marllex.waselak.core.model.DomainFeatures.forType("RESTAURANT")
                    val showPharmacyTab = (analyticsFeatures.hasDrugInteractions || analyticsFeatures.hasPrescriptions) &&
                        v?.enableDrugInteractions != false &&
                        v?.enablePrescriptions != false
                    val showInstallmentsTab = analyticsFeatures.hasInstallments &&
                        v?.enableInstallments != false
                    AnalyticsScreen(
                        onNavigateBack = { activeSubScreen = null },
                        businessType = v?.businessType,
                        hasPharmacyAnalytics = showPharmacyTab,
                        hasInstallmentsAnalytics = showInstallmentsTab,
                    )
                }
            }

            "staff" -> {
                StaffScreen(
                    onNavigateToWorkerQrCode = onNavigateToWorkerQrCode,
                    isAttendanceEnabled = vendor?.enableAttendance != false,
                    isSalaryEnabled = vendor?.enableSalary != false,
                    isOvertimeEnabled = vendor?.enableOvertime != false,
                    isDeliveryEnabled = vendor?.enableDelivery != false,
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "customers" -> {
                if (vendor?.enableCustomers == false) {
                    TopAppBar(
                        title = { Text(stringResource(CoreRes.string.nav_customers)) },
                        navigationIcon = {
                            IconButton(onClick = { activeSubScreen = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    FeatureNotAvailableView()
                } else {
                    CustomersScreen(
                        onNavigateBack = { activeSubScreen = null },
                    )
                }
            }

            "store_profile" -> {
                RestaurantProfileScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "my_account" -> {
                net.marllex.waselak.manager.myaccount.MyAccountScreen(
                    onNavigateBack = { activeSubScreen = null },
                    onSignOut = onSignOut,
                )
            }

            "loyalty_discounts" -> {
                LoyaltyDiscountsScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "users" -> {
                UsersScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "plans" -> {
                TopAppBar(
                    title = { Text(stringResource(CoreRes.string.subscription_plans)) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                PlansComparisonScreen()
            }

            "export" -> {
                ExportScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "suppliers" -> {
                SuppliersScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "returns" -> {
                ReturnsScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "scheduled_orders" -> {
                ScheduledOrdersScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "notifications" -> {
                NotificationsScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "drug_interactions" -> {
                DrugInteractionsScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "doctor_stats" -> {
                net.marllex.waselak.manager.doctorstats.DoctorStatsScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "customer_credit" -> {
                CustomerCreditScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "installments" -> {
                net.marllex.waselak.manager.installments.InstallmentsScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "cash_drawer" -> {
                ManagerCashDrawerScreen(
                    onNavigateBack = { activeSubScreen = null },
                )
            }

            "settings" -> {
                // Settings sub-level with its own nested screens
                when (settingsSubScreen) {
                    "tax_places" -> {
                        TaxPlacesScreen(
                            onNavigateBack = { settingsSubScreen = null },
                        )
                    }

                    "store_configuration" -> {
                        TopAppBar(
                            title = { Text(stringResource(CoreRes.string.store_configuration)) },
                            navigationIcon = {
                                IconButton(onClick = { settingsSubScreen = null }) {
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
                                IconButton(onClick = { settingsSubScreen = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                        OfflineSettingsScreen()
                    }

                    else -> {
                        // Settings main list
                        TopAppBar(
                            title = { Text(stringResource(CoreRes.string.tab_settings)) },
                            navigationIcon = {
                                IconButton(onClick = { activeSubScreen = null; settingsSubScreen = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isTablet = maxWidth >= 600.dp
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = if (isTablet) 32.dp else 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SettingsNavigationCard(
                                    icon = Icons.Filled.LocalShipping,
                                    title = stringResource(CoreRes.string.tax_places),
                                    onClick = { settingsSubScreen = "tax_places" },
                                )
                                SettingsNavigationCard(
                                    icon = Icons.Filled.Settings,
                                    title = stringResource(CoreRes.string.store_configuration),
                                    onClick = { settingsSubScreen = "store_configuration" },
                                )
                                SettingsNavigationCard(
                                    icon = Icons.Filled.CloudOff,
                                    title = stringResource(CoreRes.string.offline_mode_settings),
                                    onClick = { settingsSubScreen = "offline_mode" },
                                )
                                // Social links managed from Admin app settings

                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(4.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        LanguageSelector(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }

            "about" -> {
                val profileApi = profileVm
                AboutScreen(
                    appName = "Waselak Manager",
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    onCheckUpdate = {
                        val api = org.koin.mp.KoinPlatform.getKoin().get<net.marllex.waselak.core.network.WaselakApiClient>()
                        val resp = api.checkForUpdate("manager", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                        net.marllex.waselak.core.ui.components.UpdateInfo(
                            hasUpdate = resp.hasUpdate,
                            latestVersion = resp.latestVersion,
                            updateStatus = resp.updateStatus,
                            releaseNotes = resp.releaseNotesAr ?: resp.releaseNotes,
                            downloadUrl = resp.downloadUrl,
                            facebookUrl = resp.facebookUrl,
                            landingPageUrl = resp.landingPageUrl,
                            instagramUrl = resp.instagramUrl,
                            whatsappNumber = resp.whatsappNumber,
                        )
                    },
                    onDownload = { url, onProgress ->
                        val api = org.koin.mp.KoinPlatform.getKoin().get<net.marllex.waselak.core.network.WaselakApiClient>()
                        api.downloadFile(url, onProgress)
                    },
                    onNavigateBack = { activeSubScreen = null },
                    vendorName = vendor?.name,
                )
            }

            else -> {
                // More main screen with toolbar
                TopAppBar(
                    title = { Text(stringResource(CoreRes.string.nav_more)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isTablet = maxWidth >= 600.dp
                    val gridColumns = if (isTablet) 3 else 2
                    val horizontalPad = if (isTablet) 32.dp else 16.dp

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = horizontalPad, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // ── Store Features Grid ──
                        MoreSectionHeader(stringResource(CoreRes.string.store_features))

                        val features = if (vendor != null) net.marllex.waselak.core.model.DomainFeatures.forVendor(vendor) else net.marllex.waselak.core.model.DomainFeatures.forType("RESTAURANT")
                        // Build items with visibility flags based on business type
                        val allStoreEntries = listOf(
                            Triple(Triple(Icons.Filled.BarChart, stringResource(CoreRes.string.tab_analytics), Color(0xFF1565C0)), { activeSubScreen = "analytics" }, vendor?.enableAnalytics != false),
                            Triple(Triple(Icons.Filled.People, stringResource(CoreRes.string.nav_staff), Color(0xFF2E7D32)), { activeSubScreen = "staff" }, true),
                            Triple(Triple(Icons.Filled.Groups, stringResource(CoreRes.string.nav_customers), Color(0xFF6A1B9A)), { activeSubScreen = "customers" }, true),
                            Triple(Triple(Icons.Filled.Security, stringResource(CoreRes.string.roles_permissions), Color(0xFFE65100)), { activeSubScreen = "users" }, true),
                            Triple(Triple(Icons.Filled.CardGiftcard, stringResource(CoreRes.string.loyalty_and_discounts), Color(0xFFF57C00)), { activeSubScreen = "loyalty_discounts" }, features.hasOffers),
                            Triple(Triple(Icons.Filled.Inventory, stringResource(CoreRes.string.suppliers), Color(0xFF00695C)), { activeSubScreen = "suppliers" }, features.hasSuppliers && vendor?.enableSuppliers != false),
                            Triple(Triple(Icons.Filled.SwapHoriz, stringResource(CoreRes.string.returns_exchanges), Color(0xFFC62828)), { activeSubScreen = "returns" }, features.hasReturns && vendor?.enableReturns != false),
                            Triple(Triple(Icons.Filled.Schedule, stringResource(CoreRes.string.scheduled_orders), Color(0xFF4527A0)), { activeSubScreen = "scheduled_orders" }, features.hasPreOrders && vendor?.enableScheduledOrders != false),
                            Triple(Triple(Icons.Filled.Notifications, stringResource(CoreRes.string.notifications), Color(0xFF1565C0)), { activeSubScreen = "notifications" }, vendor?.enableAnnouncements != false),
                            Triple(Triple(Icons.Filled.LocalPharmacy, stringResource(CoreRes.string.drug_interactions), Color(0xFF2E7D32)), { activeSubScreen = "drug_interactions" }, features.hasDrugInteractions && vendor?.enableDrugInteractions != false),
                            Triple(Triple(Icons.Filled.Person, stringResource(CoreRes.string.doctor_stats), Color(0xFF1565C0)), { activeSubScreen = "doctor_stats" }, features.hasDrugInteractions && vendor?.enableDrugInteractions != false),
                            Triple(Triple(Icons.Filled.CreditCard, stringResource(CoreRes.string.customer_credit), Color(0xFF6A1B9A)), { activeSubScreen = "customer_credit" }, features.hasCustomerCredit && vendor?.enableCustomerCredit != false),
                            Triple(Triple(Icons.Filled.Schedule, stringResource(CoreRes.string.installments), Color(0xFF00695C)), { activeSubScreen = "installments" }, features.hasInstallments && vendor?.enableInstallments != false),
                            Triple(Triple(Icons.Filled.PointOfSale, stringResource(CoreRes.string.cash_drawer), Color(0xFF5D4037)), { activeSubScreen = "cash_drawer" }, vendor?.enableCashDrawer != false),
                        ).filter { it.third }
                        val storeItems = allStoreEntries.map { it.first }
                        val storeActions = allStoreEntries.map { it.second }
                        MoreGrid(storeItems, storeActions, gridColumns)

                        // ── Account Information Grid ──
                        MoreSectionHeader(stringResource(CoreRes.string.account_information))

                        val accountItems = listOf(
                            Triple(Icons.Filled.Person, "حسابي", Color(0xFF1B3A5C)),
                            Triple(Icons.Filled.Store, stringResource(CoreRes.string.tab_store), Color(0xFF00838F)),
                            Triple(Icons.Filled.Star, stringResource(CoreRes.string.subscription_plans), Color(0xFFF9A825)),
                        )
                        val accountActions = listOf<() -> Unit>(
                            { activeSubScreen = "my_account" },
                            { activeSubScreen = "store_profile" },
                            { activeSubScreen = "plans" },
                        )
                        MoreGrid(accountItems, accountActions, gridColumns)

                        // ── App Settings Grid ──
                        MoreSectionHeader(stringResource(CoreRes.string.app_settings))

                        val settingsItems = listOf(
                            Triple(Icons.Filled.Settings, stringResource(CoreRes.string.tab_settings), Color(0xFF546E7A)),
                            Triple(Icons.Filled.Info, stringResource(CoreRes.string.about_and_updates), Color(0xFF37474F)),
                        )
                        val settingsActions = listOf<() -> Unit>(
                            { activeSubScreen = "settings" },
                            { activeSubScreen = "about" },
                        )
                        MoreGrid(settingsItems, settingsActions, gridColumns)

                        // Language card (full width)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                LanguageSelector(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        // Upload Logs
                        val apiClient = koinInject<WaselakApiClient>()
                        val scope = rememberCoroutineScope()
                        var isUploadingLogs by remember { mutableStateOf(false) }
                        var logUploadMessage by remember { mutableStateOf<String?>(null) }
                        val settingsPlatformActions = rememberPlatformActions()

                        val noLogsMsg = stringResource(CoreRes.string.no_logs_available_msg)
                        val logsUploadedMsg = stringResource(CoreRes.string.logs_uploaded_success)
                        val logsUploadFailedMsg = stringResource(CoreRes.string.logs_upload_failed_msg)
                        val logsClearedMsg = stringResource(CoreRes.string.logs_cleared_msg)

                        UploadLogsCard(
                            isUploading = isUploadingLogs,
                            onUploadLogs = {
                                scope.launch {
                                    isUploadingLogs = true
                                    try {
                                        val bytes = AppLogger.readLogFileBytes()
                                        if (bytes.isEmpty()) {
                                            logUploadMessage = noLogsMsg
                                        } else {
                                            apiClient.uploadLogFile(bytes, AppLogger.getLogFileName())
                                            logUploadMessage = logsUploadedMsg
                                        }
                                    } catch (e: Exception) {
                                        logUploadMessage = kFormat(logsUploadFailedMsg, e.message ?: "")
                                    } finally {
                                        isUploadingLogs = false
                                    }
                                }
                            },
                            onShareLogs = {
                                val bytes = AppLogger.readLogFileBytes()
                                if (bytes.isNotEmpty()) {
                                    settingsPlatformActions.shareFile(bytes, AppLogger.getLogFileName(), "text/plain")
                                }
                            },
                            onClearLogs = {
                                AppLogger.clearLogs()
                                logUploadMessage = logsClearedMsg
                            },
                        )

                        logUploadMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (msg != noLogsMsg && msg != logsUploadedMsg && msg != logsClearedMsg)
                                    MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                            )
                            LaunchedEffect(msg) {
                                kotlinx.coroutines.delay(3000)
                                logUploadMessage = null
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(4.dp))

                        SignOutButton(onSignOut = onSignOut)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
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
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
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
private fun MoreSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun MoreGrid(
    items: List<Triple<ImageVector, String, Color>>,
    actions: List<() -> Unit>,
    columns: Int,
) {
    val rows = items.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEachIndexed { index, (icon, title, tint) ->
                    MoreGridCard(
                        icon = icon,
                        title = title,
                        iconTint = tint,
                        onClick = actions[items.indexOf(rowItems[index])],
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill remaining space if row is not full
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MoreGridCard(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = iconTint.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
            .background(MaterialTheme.colorScheme.background)
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
                            color = MaterialTheme.colorScheme.onSurface,
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

// Social links removed — now managed from Admin app Settings
