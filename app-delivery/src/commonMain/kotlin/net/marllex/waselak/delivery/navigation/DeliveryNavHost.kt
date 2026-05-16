package net.marllex.waselak.delivery.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.LaunchedEffect
import net.marllex.waselak.core.common.crash.CrashReporter
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import net.marllex.waselak.core.ui.components.waslekLogoPainter
import net.marllex.waselak.core.ui.components.ProfileAvatar
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.UserRole
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.ui.components.LanguageSelector
import net.marllex.waselak.core.ui.components.SignOutButton
import net.marllex.waselak.core.ui.components.UploadLogsCard
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.WaselakApiClient
import org.koin.compose.koinInject
import net.marllex.waselak.core.ui.components.WaslekLogo
import net.marllex.waselak.core.ui.platform.PlatformActions
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*
import net.marllex.waselak.feature.auth.biometric.BiometricResult
import net.marllex.waselak.feature.auth.biometric.rememberBiometricAuthenticator
import net.marllex.waselak.feature.auth.navigation.AUTH_ROUTE
import net.marllex.waselak.feature.auth.navigation.authScreen
import net.marllex.waselak.feature.delivery.map.navigation.deliveryMapScreen
import net.marllex.waselak.feature.delivery.orders.history.navigation.deliveryHistoryScreen
import net.marllex.waselak.feature.delivery.orders.navigation.deliveryOrdersScreen
import net.marllex.waselak.feature.delivery.orders.navigation.deliveryReceiptScreen
import net.marllex.waselak.feature.delivery.orders.navigation.navigateToDeliveryReceipt
import net.marllex.waselak.feature.delivery.status.navigation.deliveryStatusScreen
import net.marllex.waselak.feature.delivery.status.navigation.navigateToDeliveryStatus
import net.marllex.waselak.delivery.notifications.DeliveryNotificationsScreen
import net.marllex.waselak.feature.manager.staff.AnnouncementsScreen

enum class DeliveryTab(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    ORDERS("delivery/orders", "My Orders", Icons.Filled.DeliveryDining),
    HISTORY("delivery/history", "History", Icons.Filled.History),
    ANNOUNCEMENTS("delivery/announcements", "Alerts", Icons.Filled.Notifications),
//    MAP("delivery/map", "Map", Icons.Filled.Map),
    PROFILE("delivery/profile", "Profile", Icons.Filled.Person),
}

@Composable
private fun localizedTabTitle(tab: DeliveryTab): String = when (tab) {
    DeliveryTab.ORDERS -> stringResource(CoreRes.string.nav_my_orders)
    DeliveryTab.HISTORY -> stringResource(CoreRes.string.nav_history)
    DeliveryTab.ANNOUNCEMENTS -> stringResource(CoreRes.string.nav_alerts)
    DeliveryTab.PROFILE -> stringResource(CoreRes.string.nav_profile)
}

@Composable
private fun localizedRoleLabel(role: UserRole?): String = when (role) {
    UserRole.MANAGER -> stringResource(CoreRes.string.role_manager)
    UserRole.CASHIER -> stringResource(CoreRes.string.role_cashier)
    UserRole.DELIVERY -> stringResource(CoreRes.string.role_delivery)
    UserRole.KITCHEN -> stringResource(CoreRes.string.role_kitchen)
    null -> ""
}

// --- Bottom Bar (phone) --------------------------------------------------
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
            val title = localizedTabTitle(tab)

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
                ),
            )
        }
    }
}

// --- Navigation Rail (tablet) --------------------------------------------
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
            val title = localizedTabTitle(tab)

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
                ),
            )
        }
    }
}

// --- Profile Screen ------------------------------------------------------
@Composable
private fun DeliveryProfileScreen(
    userName: String?,
    userPhone: String?,
    userEmail: String?,
    userRole: String?,
    userPhotoUrl: String? = null,
    vendor: Vendor?,
    onViewShiftSummary: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = if (isTablet) 48.dp else 16.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Profile header card with store logo
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val logoPainter = waslekLogoPainter()
                        if (!vendor?.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = vendor?.logoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentScale = ContentScale.Crop,
                                placeholder = logoPainter,
                                error = logoPainter,
                            )
                        } else {
                            WaslekLogo(
                                modifier = Modifier
                                    .size(80.dp)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = vendor?.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        ProfileAvatar(
                            photoUrl = userPhotoUrl,
                            size = 56.dp,
                            contentDescription = userName,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = userName ?: "N/A",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = userRole ?: "N/A",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Store Info section
            if (vendor != null) {
                item {
                    Text(
                        text = stringResource(CoreRes.string.store_information),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            ProfileInfoRow(
                                label = stringResource(CoreRes.string.store_name),
                                value = vendor.name,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            ProfileInfoRow(
                                label = stringResource(CoreRes.string.address),
                                value = vendor.address,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            ProfileInfoRow(
                                label = stringResource(CoreRes.string.contact_phone),
                                value = vendor.contactPhone,
                            )
                            vendor.walletPhone?.let { walletPhone ->
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                ProfileInfoRow(
                                    label = stringResource(CoreRes.string.wallet_phone),
                                    value = walletPhone,
                                )
                            }
                        }
                    }
                }
            }

            // Account Info section
            item {
                Text(
                    text = stringResource(CoreRes.string.account_information),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        ProfileInfoRow(
                            label = stringResource(CoreRes.string.name),
                            value = userName ?: "N/A",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        ProfileInfoRow(
                            label = stringResource(CoreRes.string.contact_phone),
                            value = userPhone ?: "N/A",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        ProfileInfoRow(
                            label = stringResource(CoreRes.string.email),
                            value = userEmail ?: "N/A",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        ProfileInfoRow(
                            label = stringResource(CoreRes.string.role),
                            value = userRole ?: "N/A",
                        )
                    }
                }
            }

            // App Settings
            item {
                Text(
                    text = stringResource(CoreRes.string.app_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(CoreRes.string.language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LanguageSelector(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Upload Logs
            item {
                val apiClient = koinInject<WaselakApiClient>()
                val logScope = rememberCoroutineScope()
                var isUploadingLogs by remember { mutableStateOf(false) }
                val logPlatformActions = rememberPlatformActions()

                UploadLogsCard(
                    isUploading = isUploadingLogs,
                    onUploadLogs = {
                        logScope.launch {
                            isUploadingLogs = true
                            try {
                                val bytes = AppLogger.readLogFileBytes()
                                if (bytes.isNotEmpty()) {
                                    apiClient.uploadLogFile(bytes, AppLogger.getLogFileName())
                                }
                            } catch (_: Exception) {
                            } finally {
                                isUploadingLogs = false
                            }
                        }
                    },
                    onShareLogs = {
                        val bytes = AppLogger.readLogFileBytes()
                        if (bytes.isNotEmpty()) {
                            logPlatformActions.shareFile(bytes, AppLogger.getLogFileName(), "text/plain")
                        }
                    },
                    onClearLogs = {
                        AppLogger.clearLogs()
                    },
                )
            }

            // Shift Summary
            item {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = onViewShiftSummary,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        Icons.Filled.DeliveryDining,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(CoreRes.string.shift_summary))
                }
            }

            // About & Updates
            item {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = onNavigateToAbout,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Store,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(CoreRes.string.about_and_updates))
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
private fun formatRoleLabel(role: UserRole?): String = localizedRoleLabel(role)

// --- Main Nav Host -------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryNavHost(
    authRepository: AuthRepository,
    vendorRepository: VendorRepository,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Log navigation to Sentry
    LaunchedEffect(currentDestination?.route) {
        currentDestination?.route?.let { route ->
            CrashReporter.logNavigation("delivery", route)
        }
    }
    val platformActions = rememberPlatformActions()
    val scope = rememberCoroutineScope()
    val currentUser by authRepository.currentUser.collectAsState(initial = null)
    val vendor by vendorRepository.getMyVendor().collectAsState(initial = null)
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

    // Shift summary state
    val apiClient: WaselakApiClient = koinInject()
    val tokenManager: net.marllex.waselak.core.auth.TokenManager = koinInject()

    // Suspension watchdog: proactive ping every 60s. See CashierNavHost for the
    // full rationale — in short, without this a delivery user whose account got
    // suspended would keep working on cached screens until they happened to hit
    // the backend. The ping lets the global suspension interceptor fire reliably.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            if (isLoggedIn) {
                runCatching { apiClient.pingAuth() }
            }
        }
    }

    var showShiftSummary by remember { mutableStateOf(false) }
    var shiftSummaryWithSignOut by remember { mutableStateOf(true) }
    var shiftSummaryData by remember { mutableStateOf<net.marllex.waselak.core.ui.components.ShiftSummaryUiModel?>(null) }
    var shiftSummaryLoading by remember { mutableStateOf(false) }
    var shiftSummaryError by remember { mutableStateOf<String?>(null) }

    val fetchShiftSummary: () -> Unit = remember(scope) {
        {
            scope.launch {
                shiftSummaryLoading = true
                shiftSummaryError = null
                try {
                    val from = tokenManager.getLoginTimestamp()
                    val response = apiClient.getMyShiftSummary(from = from)
                    shiftSummaryData = net.marllex.waselak.core.ui.components.ShiftSummaryUiModel(
                        totalRevenue = response.totalRevenue,
                        totalOrders = response.totalOrders,
                        cashRevenue = response.cashRevenue,
                        walletRevenue = response.walletRevenue,
                        cardRevenue = response.cardRevenue,
                        cashOrders = response.cashOrders,
                        walletOrders = response.walletOrders,
                        cardOrders = response.cardOrders,
                        cancelledTotal = response.cancelledTotal,
                        cancelledCount = response.cancelledCount,
                        refundedTotal = response.refundedTotal,
                        refundedCount = response.refundedCount,
                        installmentPayments = response.installmentPayments,
                        installmentPaymentCount = response.installmentPaymentCount,
                    )
                } catch (e: Exception) {
                    shiftSummaryError = e.message ?: "Failed to load shift summary"
                }
                shiftSummaryLoading = false
            }
        }
    }

    // Sign-out: show shift summary bottom sheet first
    val onSignOut: () -> Unit = remember(scope) {
        {
            shiftSummaryWithSignOut = true
            showShiftSummary = true
            fetchShiftSummary()
        }
    }

    // View-only shift summary (from profile screen)
    val onViewShiftSummary: () -> Unit = remember(scope) {
        {
            shiftSummaryWithSignOut = false
            showShiftSummary = true
            fetchShiftSummary()
        }
    }

    if (showShiftSummary) {
        net.marllex.waselak.core.ui.components.ShiftSummaryBottomSheet(
            shiftSummary = shiftSummaryData,
            isLoading = shiftSummaryLoading,
            error = shiftSummaryError,
            onRetry = fetchShiftSummary,
            onSignOut = if (shiftSummaryWithSignOut) {
                {
                    scope.launch {
                        val canProceed = if (biometricAuth.isAvailable()) {
                            when (biometricAuth.authenticate("Sign out verification")) {
                                is BiometricResult.Success -> true
                                is BiometricResult.NotAvailable -> true
                                else -> false
                            }
                        } else {
                            true
                        }
                        if (canProceed) {
                            showShiftSummary = false
                            authRepository.logout()
                            navController.navigate(AUTH_ROUTE) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
            } else {
                null
            },
            onDismiss = {
                showShiftSummary = false
                shiftSummaryData = null
                shiftSummaryError = null
            },
        )
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
                platformActions.openMap(null, lat, lng)
            },
        )

        composable(DeliveryTab.ANNOUNCEMENTS.route) {
            DeliveryNotificationsScreen(
                onNavigateBack = {
                    navController.navigate(DeliveryTab.ORDERS.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        composable(DeliveryTab.PROFILE.route) {
            DeliveryProfileScreen(
                userName = currentUser?.name,
                userPhone = currentUser?.phone,
                userEmail = currentUser?.email,
                userRole = roleLabel,
                userPhotoUrl = currentUser?.photoUrl,
                vendor = vendor,
                onViewShiftSummary = onViewShiftSummary,
                onSignOut = onSignOut,
                onNavigateToAbout = {
                    navController.navigate("delivery/about")
                },
            )
        }

        composable("delivery/about") {
            val deliveryApi = org.koin.java.KoinJavaComponent.getKoin().get<net.marllex.waselak.core.network.WaselakApiClient>()
            net.marllex.waselak.core.ui.components.AboutScreen(
                appName = "Waselak Delivery",
                versionName = net.marllex.waselak.config.BuildConfig.VERSION_NAME,
                versionCode = net.marllex.waselak.config.BuildConfig.VERSION_CODE,
                onCheckUpdate = {
                    val resp = deliveryApi.checkForUpdate(
                        app = "delivery",
                        version = net.marllex.waselak.config.BuildConfig.VERSION_NAME,
                        versionCode = net.marllex.waselak.config.BuildConfig.VERSION_CODE,
                        variant = if (net.marllex.waselak.config.BuildConfig.IS_DEBUG) "debug" else "release",
                    )
                    net.marllex.waselak.core.ui.components.UpdateInfo(
                        hasUpdate = resp.hasUpdate,
                        latestVersion = resp.latestVersion,
                        updateStatus = resp.updateStatus,
                        releaseNotes = resp.releaseNotesAr ?: resp.releaseNotes,
                        downloadUrl = resp.downloadUrl,
                        downloadFilename = resp.downloadFilename,
                        baseUrl = net.marllex.waselak.config.BuildConfig.BASE_URL,
                        facebookUrl = resp.facebookUrl,
                        landingPageUrl = resp.landingPageUrl,
                        instagramUrl = resp.instagramUrl,
                        whatsappNumber = resp.whatsappNumber,
                    )
                },
                onDownload = { url, onProgress -> deliveryApi.downloadFile(url, onProgress) },
            )
        }

        deliveryStatusScreen(
            onBack = { navController.popBackStack() },
            onNavigateToMap = { lat, lng ->
                if (lat != null && lng != null) {
                    platformActions.openMap(null, lat, lng)
                }
            },
        )

        deliveryReceiptScreen(
            onBack = { navController.popBackStack() },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

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
                                        text = stringResource(CoreRes.string.welcome_name, name),
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
                                        text = stringResource(CoreRes.string.welcome_name, name),
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
}
