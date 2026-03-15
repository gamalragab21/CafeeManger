package net.marllex.waselak.cashier.navigation

import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TableBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import net.marllex.waselak.core.ui.components.waslekLogoPainter
import net.marllex.waselak.core.ui.components.ProfileAvatar
import kotlinx.coroutines.launch
import net.marllex.waselak.core.data.offline.OfflineModeManager
import net.marllex.waselak.core.data.sync.SyncScheduler
import net.marllex.waselak.core.data.sync.SyncService
import net.marllex.waselak.core.data.sync.SyncState
import net.marllex.waselak.core.database.Pending_sync
import net.marllex.waselak.core.database.dao.PendingSyncDao
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import org.koin.compose.koinInject
import net.marllex.waselak.core.model.UserRole
import net.marllex.waselak.core.model.PaymentTiming
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.ui.components.LanguageSelector
import net.marllex.waselak.core.ui.components.SignOutButton
import net.marllex.waselak.core.ui.components.UploadLogsCard
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import net.marllex.waselak.core.ui.components.FeatureNotAvailableView
import net.marllex.waselak.core.ui.components.FeatureNotAvailableBottomSheet
import net.marllex.waselak.core.ui.components.WaslekLogo
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.model.Worker
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.*
import net.marllex.waselak.feature.auth.biometric.BiometricResult
import net.marllex.waselak.feature.auth.biometric.rememberBiometricAuthenticator
import net.marllex.waselak.feature.auth.navigation.AUTH_ROUTE
import net.marllex.waselak.feature.auth.navigation.authScreen
import net.marllex.waselak.feature.cashier.attendance.AttendanceScreen
import net.marllex.waselak.feature.cashier.payment.navigation.navigateToPayment
import net.marllex.waselak.feature.cashier.payment.navigation.paymentScreen
import net.marllex.waselak.feature.cashier.pos.navigation.navigateToPos
import net.marllex.waselak.feature.cashier.pos.navigation.navigateToPosWithReservation
import net.marllex.waselak.feature.cashier.pos.navigation.posScreen
import net.marllex.waselak.feature.cashier.receipt.navigation.navigateToReceipt
import net.marllex.waselak.feature.cashier.receipt.navigation.receiptScreen
import net.marllex.waselak.feature.manager.orders.OrdersScreen
import net.marllex.waselak.feature.manager.staff.AnnouncementsScreen
import net.marllex.waselak.feature.manager.staff.DeliveryDashboardScreen
import net.marllex.waselak.feature.manager.tables.TablesScreen
import net.marllex.waselak.cashier.cashdrawer.CashDrawerScreen
import net.marllex.waselak.cashier.customercredit.CashierCustomerCreditScreen
import net.marllex.waselak.cashier.kds.KdsScreen
import net.marllex.waselak.cashier.notifications.CashierNotificationsScreen
import net.marllex.waselak.cashier.scheduledorders.ScheduledOrdersScreen
import net.marllex.waselak.cashier.prescriptions.PrescriptionsScreen
import net.marllex.waselak.cashier.splitpayment.SplitPaymentScreen
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Restaurant

// Tabs in bottom navigation (most-used)
enum class CashierTab(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    POS("cashier/pos", title = "New Order", Icons.Filled.PointOfSale),
    ORDERS("cashier/orders", title = "Orders", icon = Icons.Filled.History),
    TABLES("cashier/tables", title = "Tables", icon = Icons.Filled.TableBar),
}

// Items in drawer (less-used)
enum class CashierDrawerItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    DELIVERY("cashier/delivery", "Delivery", Icons.Filled.DeliveryDining),
    ANNOUNCEMENTS("cashier/announcements", "Alerts", Icons.Filled.Notifications),
    ATTENDANCE("cashier/attendance", "Attendance", Icons.Filled.Fingerprint),
    OVERTIME("cashier/overtime", "Overtime", Icons.Filled.Timer),
    KDS("cashier/kds", "Kitchen Display", Icons.Filled.Restaurant),
    CASH_DRAWER("cashier/cash_drawer", "Cash Drawer", Icons.Filled.Store),
    SCHEDULED_ORDERS("cashier/scheduled_orders", "Scheduled Orders", Icons.Filled.Schedule),
    NOTIFICATIONS("cashier/notifications", "Notifications", Icons.Filled.Notifications),
    PRESCRIPTIONS("cashier/prescriptions", "Prescriptions", Icons.Filled.LocalPharmacy),
    SPLIT_PAYMENT("cashier/split_payment", "Split Payment", Icons.Filled.Payment),
    CUSTOMER_CREDIT("cashier/customer_credit", "Customer Credit", Icons.Filled.CreditCard),
    PROFILE("cashier/profile", "Profile", Icons.Filled.Person),
}

private val allRoutes = CashierTab.entries.map { it.route } + CashierDrawerItem.entries.map { it.route }

@Composable
private fun localizedTabTitle(tab: CashierTab, businessType: String = "RESTAURANT"): String = when (tab) {
    CashierTab.POS -> when (businessType) {
        "PHARMACY" -> stringResource(CoreRes.string.nav_new_prescription)
        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(CoreRes.string.nav_new_invoice)
        else -> stringResource(CoreRes.string.nav_new_order)
    }
    CashierTab.ORDERS -> when (businessType) {
        "PHARMACY" -> stringResource(CoreRes.string.nav_orders_prescriptions)
        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(CoreRes.string.nav_orders_invoices)
        else -> stringResource(CoreRes.string.nav_orders)
    }
    CashierTab.TABLES -> stringResource(CoreRes.string.nav_tables)
}

@Composable
private fun localizedDrawerTitle(item: CashierDrawerItem): String = when (item) {
    CashierDrawerItem.DELIVERY -> stringResource(CoreRes.string.nav_delivery)
    CashierDrawerItem.ANNOUNCEMENTS -> stringResource(CoreRes.string.nav_alerts)
    CashierDrawerItem.ATTENDANCE -> stringResource(CoreRes.string.nav_attendance)
    CashierDrawerItem.OVERTIME -> stringResource(CoreRes.string.nav_overtime)
    CashierDrawerItem.KDS -> stringResource(CoreRes.string.kitchen_display)
    CashierDrawerItem.CASH_DRAWER -> stringResource(CoreRes.string.cash_drawer)
    CashierDrawerItem.SCHEDULED_ORDERS -> stringResource(CoreRes.string.scheduled_orders)
    CashierDrawerItem.NOTIFICATIONS -> stringResource(CoreRes.string.notifications)
    CashierDrawerItem.PRESCRIPTIONS -> stringResource(CoreRes.string.prescriptions)
    CashierDrawerItem.SPLIT_PAYMENT -> stringResource(CoreRes.string.split_payment)
    CashierDrawerItem.CUSTOMER_CREDIT -> stringResource(CoreRes.string.customer_credit)
    CashierDrawerItem.PROFILE -> stringResource(CoreRes.string.nav_profile)
}

@Composable
private fun localizedRoleLabel(role: UserRole?): String = when (role) {
    UserRole.MANAGER -> stringResource(CoreRes.string.role_manager)
    UserRole.CASHIER -> stringResource(CoreRes.string.role_cashier)
    UserRole.DELIVERY -> stringResource(CoreRes.string.role_delivery)
    UserRole.KITCHEN -> stringResource(CoreRes.string.role_kitchen)
    null -> ""
}

// ─── Bottom Bar (phone) ──────────────────────────────────────────
@Composable
private fun CashierBottomBar(
    navController: NavController,
    currentDestination: NavDestination?,
    visibleTabs: List<CashierTab> = CashierTab.entries,
    businessType: String = "RESTAURANT",
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        visibleTabs.forEach { tab ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == tab.route } == true
            val title = localizedTabTitle(tab, businessType)

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(CashierTab.POS.route) {
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

// ─── Navigation Rail (tablet) ────────────────────────────────────
@Composable
private fun CashierNavRail(
    navController: NavController,
    currentDestination: NavDestination?,
    visibleTabs: List<CashierTab> = CashierTab.entries,
    businessType: String = "RESTAURANT",
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        visibleTabs.forEach { tab ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == tab.route } == true
            val title = localizedTabTitle(tab, businessType)

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(CashierTab.POS.route) {
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

// ─── Drawer Content ──────────────────────────────────────────────
@Composable
private fun CashierDrawerContent(
    navController: NavController,
    currentDestination: NavDestination?,
    userName: String?,
    userRole: String?,
    vendor: Vendor?,
    onItemClick: () -> Unit,
    visibleDrawerItems: List<CashierDrawerItem> = CashierDrawerItem.entries,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            // Store logo
            val drawerLogoPainter = waslekLogoPainter()
            if (!vendor?.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = vendor?.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = drawerLogoPainter,
                    error = drawerLogoPainter,
                )
            } else {
                WaslekLogo(
                    modifier = Modifier
                        .size(56.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            Spacer(Modifier.height(12.dp))
            // Store name
            Text(
                text = vendor?.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            // User name + role
            Text(
                text = userName ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        visibleDrawerItems.forEach { item ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == item.route } == true

            NavigationDrawerItem(
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(localizedDrawerTitle(item)) },
                selected = isSelected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(CashierTab.POS.route) {
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

// ─── Overtime Screen (Cashier can add entries — hours only, no rate) ─────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashierOvertimeScreen() {
    val workerRepository = koinInject<net.marllex.waselak.core.domain.repository.WorkerRepository>()
    val tokenManager: net.marllex.waselak.core.auth.TokenManager = koinInject()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<net.marllex.waselak.core.model.Overtime>>(emptyList()) }
    var workers by remember { mutableStateOf<List<Worker>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showFeatureNotAvailable by remember { mutableStateOf(false) }
    var featureNotAvailableMessage by remember { mutableStateOf("") }

    // Add form state — default date to today
    val todayDate = remember { net.marllex.waselak.core.common.extensions.todayDateString() }
    var overtimeDate by remember { mutableStateOf(todayDate) }
    var overtimeHours by remember { mutableStateOf("") }
    var overtimeNote by remember { mutableStateOf("") }
    var selectedWorkerId by remember { mutableStateOf("") }
    var workerDropdownExpanded by remember { mutableStateOf(false) }

    fun refresh() {
        isLoading = true
        scope.launch {
            try {
                val result = workerRepository.refreshOvertime().getOrThrow()
                entries = result.sortedByDescending { it.date }
            } catch (e: Exception) {
                if (e.isFeatureNotAvailableOrOffline()) {
                    showFeatureNotAvailable = true
                    featureNotAvailableMessage = e.message ?: ""
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        // Load workers list and default to authenticated user's worker
        try {
            val loadedWorkers = workerRepository.getActiveWorkers().first()
            workers = loadedWorkers
            val userId = tokenManager.getCachedUserId()
            val myWorker = loadedWorkers.find { it.userId == userId }
            if (myWorker != null) selectedWorkerId = myWorker.id
            else if (loadedWorkers.isNotEmpty()) selectedWorkerId = loadedWorkers.first().id
        } catch (_: Exception) { }
    }

    val totalAmount = entries.sumOf { it.amount }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    overtimeDate = todayDate
                    overtimeHours = ""
                    overtimeNote = ""
                    // Default to authenticated user's worker
                    val userId = kotlinx.coroutines.runBlocking { tokenManager.getCachedUserId() }
                    val myWorker = workers.find { it.userId == userId }
                    selectedWorkerId = myWorker?.id ?: workers.firstOrNull()?.id ?: ""
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(CoreRes.string.nav_overtime)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showFeatureNotAvailable) {
                FeatureNotAvailableView(message = featureNotAvailableMessage)
            } else if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(CoreRes.string.nav_overtime),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // Total summary card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(CoreRes.string.nav_overtime),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${totalAmount.toInt()} EGP",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "${entries.size} records",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        val isPendingRate = entry.ratePerHour <= 0.0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPendingRate)
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = entry.date,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            if (isPendingRate) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.error,
                                                    shape = RoundedCornerShape(4.dp),
                                                ) {
                                                    Text(
                                                        text = "⏳",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onError,
                                                    )
                                                }
                                            }
                                            Surface(
                                                color = if (entry.paid)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    text = if (entry.paid) "Paid" else "Unpaid",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (entry.paid)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        if (isPendingRate) {
                                            Text(
                                                text = "${entry.hours} hrs",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            Text(
                                                text = "${entry.hours} hrs @ ${entry.ratePerHour.toInt()} EGP/hr",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (!isPendingRate) {
                                        Text(
                                            text = "${entry.amount.toInt()} EGP",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                val noteText = entry.note
                                if (!noteText.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = noteText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ─── Add Overtime Dialog (Cashier: worker selector + hours + date + note, NO rate) ───
    if (showAddDialog) {
        val selectedWorker = workers.find { it.id == selectedWorkerId }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            icon = { Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(CoreRes.string.nav_overtime))
                    if (selectedWorker != null) {
                        Text(
                            text = selectedWorker.fullName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Worker selector dropdown
                    if (workers.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = workerDropdownExpanded,
                            onExpandedChange = { workerDropdownExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedWorker?.fullName ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(CoreRes.string.worker)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = workerDropdownExpanded)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                singleLine = true,
                            )
                            ExposedDropdownMenu(
                                expanded = workerDropdownExpanded,
                                onDismissRequest = { workerDropdownExpanded = false },
                            ) {
                                workers.forEach { worker ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = worker.fullName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                Text(
                                                    text = worker.role,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedWorkerId = worker.id
                                            workerDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = overtimeDate,
                        onValueChange = { overtimeDate = it },
                        label = { Text(stringResource(CoreRes.string.date)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(CoreRes.string.date_placeholder)) },
                    )
                    OutlinedTextField(
                        value = overtimeHours,
                        onValueChange = { overtimeHours = it },
                        label = { Text(stringResource(CoreRes.string.hours)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        placeholder = { Text(stringResource(CoreRes.string.hours_placeholder)) },
                    )
                    OutlinedTextField(
                        value = overtimeNote,
                        onValueChange = { overtimeNote = it },
                        label = { Text(stringResource(CoreRes.string.note_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hours = overtimeHours.toDoubleOrNull() ?: return@Button
                        if (hours <= 0 || overtimeDate.isBlank()) return@Button
                        isSaving = true
                        scope.launch {
                            workerRepository.createOvertime(
                                workerId = selectedWorkerId,
                                date = overtimeDate,
                                hours = hours,
                                ratePerHour = 0.0, // Cashier doesn't set rate
                                note = overtimeNote.ifBlank { null },
                            ).onSuccess {
                                isSaving = false
                                showAddDialog = false
                                refresh()
                            }.onFailure {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving &&
                        overtimeDate.isNotBlank() &&
                        selectedWorkerId.isNotBlank() &&
                        (overtimeHours.toDoubleOrNull() ?: 0.0) > 0,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(CoreRes.string.save))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(CoreRes.string.cancel))
                }
            },
        )
    }
}

// ─── Profile Screen ──────────────────────────────────────────────
@Composable
private fun CashierProfileScreen(
    userName: String?,
    userPhone: String?,
    userEmail: String?,
    userRole: String?,
    userPhotoUrl: String? = null,
    vendor: Vendor?,
    onSignOut: () -> Unit,
    onViewShiftSummary: () -> Unit = {},
    pendingSyncItems: List<Pending_sync> = emptyList(),
    isSyncing: Boolean = false,
    lastSyncResult: String? = null,
    onSyncNow: () -> Unit = {},
    onRetrySyncItem: (String) -> Unit = {},
    onDeleteSyncItem: (String) -> Unit = {},
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
            // ── Card 1: Vendor / Store Information ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Vendor header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val settingsLogoPainter = waslekLogoPainter()
                            if (!vendor?.logoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = vendor?.logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentScale = ContentScale.Crop,
                                    placeholder = settingsLogoPainter,
                                    error = settingsLogoPainter,
                                )
                            } else {
                                WaslekLogo(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(CoreRes.string.store_information),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = vendor?.name ?: "",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (vendor != null) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            ProfileInfoRow(
                                label = stringResource(CoreRes.string.address),
                                value = vendor.address,
                            )
                            ProfileInfoRow(
                                label = stringResource(CoreRes.string.contact_phone),
                                value = vendor.contactPhone,
                            )
                            vendor.walletPhone?.let { walletPhone ->
                                ProfileInfoRow(
                                    label = stringResource(CoreRes.string.wallet_phone),
                                    value = walletPhone,
                                )
                            }
                        }
                    }
                }
            }

            // ── Card 2: Cashier Information ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Cashier header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(
                                photoUrl = userPhotoUrl,
                                size = 56.dp,
                                contentDescription = userName,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(CoreRes.string.account_information),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = userName ?: "N/A",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(20.dp),
                                        ),
                                ) {
                                    Text(
                                        text = userRole ?: "N/A",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(12.dp))
                        ProfileInfoRow(
                            label = stringResource(CoreRes.string.contact_phone),
                            value = userPhone ?: "N/A",
                        )
                        ProfileInfoRow(
                            label = stringResource(CoreRes.string.email),
                            value = userEmail ?: "N/A",
                        )
                    }
                }
            }

            // App Settings section
            item {
                Text(
                    text = stringResource(CoreRes.string.app_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                            text = stringResource(CoreRes.string.language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LanguageSelector(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Sync & Offline section
            item {
                Text(
                    text = stringResource(CoreRes.string.sync_and_offline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Pending count
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(CoreRes.string.pending_items),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Badge(
                                containerColor = if (pendingSyncItems.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text("${pendingSyncItems.size}")
                            }
                        }

                        // Last sync result
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(CoreRes.string.last_sync),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = lastSyncResult
                                    ?: stringResource(CoreRes.string.no_sync_yet),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Sync Now button
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onSyncNow,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSyncing,
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(CoreRes.string.syncing))
                            } else {
                                Icon(
                                    Icons.Filled.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(CoreRes.string.sync_now))
                            }
                        }
                    }
                }
            }

            // Failed sync items
            val failedItems = pendingSyncItems.filter { (it.retry_count ?: 0) >= 3 }
            if (failedItems.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(CoreRes.string.failed_items),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                failedItems.forEach { item ->
                    item(key = "failed-${item.id}") {
                        SyncItemCard(
                            syncItem = item,
                            isFailed = true,
                            onRetry = { onRetrySyncItem(item.id) },
                            onDelete = { onDeleteSyncItem(item.id) },
                        )
                    }
                }
            }

            // Active pending sync items
            val activeItems = pendingSyncItems.filter { (it.retry_count ?: 0) < 3 }
            if (activeItems.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(CoreRes.string.pending_records),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                activeItems.forEach { item ->
                    item(key = "pending-${item.id}") {
                        SyncItemCard(
                            syncItem = item,
                            isFailed = false,
                            onRetry = null,
                            onDelete = null,
                        )
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
                        Icons.Filled.PointOfSale,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(CoreRes.string.shift_summary),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Sign Out
            item {
                Spacer(Modifier.height(8.dp))
                SignOutButton(onSignOut = onSignOut)
                Spacer(Modifier.height(16.dp))
            }

            // App Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(CoreRes.string.app_cashier),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(CoreRes.string.version_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
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
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun formatRoleLabel(role: UserRole?): String = localizedRoleLabel(role)


// ─── Offline Banner ──────────────────────────────────────────────
@Composable
private fun OfflineBanner(pendingCount: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(CoreRes.string.offline_mode),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (pendingCount > 0) {
            Text(
                text = "$pendingCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SyncingBanner(pendingCount: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3CD))   // amber/yellow background
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF856404),
        )
        Text(
            text = stringResource(CoreRes.string.syncing),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF856404),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (pendingCount > 0) {
            Text(
                text = "$pendingCount",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF856404),
            )
        }
    }
}

// ─── Sync Item Card ─────────────────────────────────────────────
@Composable
private fun SyncItemCard(
    syncItem: Pending_sync,
    isFailed: Boolean,
    onRetry: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val typeLabel = when (syncItem.type) {
        "ORDER" -> stringResource(CoreRes.string.order_sync)
        "PAYMENT_UPDATE" -> stringResource(CoreRes.string.payment_update_sync)
        "ORDER_STATUS_UPDATE" -> stringResource(CoreRes.string.order_status_sync)
        "CHECK_IN" -> stringResource(CoreRes.string.check_in_sync)
        "CHECK_OUT" -> stringResource(CoreRes.string.check_out_sync)
        "REFUND" -> stringResource(CoreRes.string.refund_sync)
        else -> syncItem.type
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFailed)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isFailed) Icons.Filled.Error else Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isFailed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (isFailed && syncItem.last_error != null) {
                    Text(
                        text = syncItem.last_error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                    )
                }
                Text(
                    text = "Retry: ${syncItem.retry_count ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRetry != null) {
                IconButton(onClick = onRetry) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(CoreRes.string.retry_sync),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(CoreRes.string.delete_failed),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// ─── Main Nav Host ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierNavHost(authRepository: AuthRepository, vendorRepository: VendorRepository, offlineModeManager: OfflineModeManager) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()
    val currentUser by authRepository.currentUser.collectAsState(initial = null)
    val vendor by vendorRepository.getMyVendor().collectAsState(initial = null)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val biometricAuth = rememberBiometricAuthenticator()
    val isOnline by offlineModeManager.isOnline.collectAsState()
    val offlineModeEnabled by offlineModeManager.offlineModeEnabled.collectAsState()
    val isOffline by offlineModeManager.isOfflineActive.collectAsState()
    val pendingCount by offlineModeManager.pendingCount.collectAsState()
    // Show blocking dialog when offline and offline mode is NOT enabled
    val showNoConnectionDialog = !isOnline && !offlineModeEnabled

    // Sync state
    val syncScheduler: SyncScheduler = koinInject()
    val syncService: SyncService = koinInject()
    val pendingSyncDao: PendingSyncDao = koinInject()
    val pendingSyncItems by pendingSyncDao.getAllPending().collectAsState(initial = emptyList())
    val syncState by syncService.syncState.collectAsState()
    val lastSyncResult by syncScheduler.lastSyncResult.collectAsState()
    val isSyncing = syncState == SyncState.SYNCING

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
    var showShiftSummary by remember { mutableStateOf(false) }
    var shiftSummaryWithSignOut by remember { mutableStateOf(false) }
    var shiftSummaryData by remember { mutableStateOf<net.marllex.waselak.core.ui.components.ShiftSummaryUiModel?>(null) }
    var shiftSummaryLoading by remember { mutableStateOf(false) }
    var shiftSummaryError by remember { mutableStateOf<String?>(null) }
    var shiftSummaryFeatureNotAvailable by remember { mutableStateOf(false) }
    var shiftSummaryFeatureMessage by remember { mutableStateOf("") }

    val failedLoadShiftSummaryText = stringResource(CoreRes.string.failed_load_shift_summary)
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
                    )
                } catch (e: Exception) {
                    if (e.isFeatureNotAvailableOrOffline()) {
                        shiftSummaryFeatureNotAvailable = true
                        shiftSummaryFeatureMessage = e.message ?: ""
                    } else {
                        shiftSummaryError = e.message ?: failedLoadShiftSummaryText
                    }
                }
                shiftSummaryLoading = false
            }
        }
    }

    // Sign-out: show shift summary with sign-out button
    val onSignOut: () -> Unit = remember(scope) {
        {
            shiftSummaryWithSignOut = true
            showShiftSummary = true
            fetchShiftSummary()
        }
    }

    // View-only: show shift summary without sign-out button
    val onViewShiftSummary: () -> Unit = remember(scope) {
        {
            shiftSummaryWithSignOut = false
            showShiftSummary = true
            fetchShiftSummary()
        }
    }

    if (showShiftSummary) {
        val signOutVerificationText = stringResource(CoreRes.string.sign_out_verification)
        net.marllex.waselak.core.ui.components.ShiftSummaryBottomSheet(
            shiftSummary = shiftSummaryData,
            isLoading = shiftSummaryLoading,
            error = shiftSummaryError,
            onRetry = fetchShiftSummary,
            onSignOut = if (shiftSummaryWithSignOut) {
                {
                    scope.launch {
                        val canProceed = if (biometricAuth.isAvailable()) {
                            when (biometricAuth.authenticate(signOutVerificationText)) {
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
            } else null,
            onDismiss = {
                showShiftSummary = false
                shiftSummaryData = null
                shiftSummaryError = null
            },
        )
    }

    if (shiftSummaryFeatureNotAvailable) {
        FeatureNotAvailableBottomSheet(
            message = shiftSummaryFeatureMessage,
            onDismiss = {
                shiftSummaryFeatureNotAvailable = false
                shiftSummaryFeatureMessage = ""
                showShiftSummary = false
            },
        )
    }

    // Compute visible tabs/drawer using domain features (works offline via cached vendor)
    val domainFeatures = remember(vendor?.businessType) {
        net.marllex.waselak.core.model.DomainFeatures.forType(vendor?.businessType ?: "RESTAURANT")
    }

    val visibleTabs = remember(vendor?.enableTables, domainFeatures) {
        CashierTab.entries.filter { tab ->
            when (tab) {
                CashierTab.TABLES -> vendor?.enableTables != false && domainFeatures.hasTables
                else -> true
            }
        }
    }

    val visibleDrawerItems = remember(domainFeatures, vendor?.enableKds) {
        CashierDrawerItem.entries.filter { item ->
            when (item) {
                CashierDrawerItem.KDS -> vendor?.enableKds != false && domainFeatures.hasKDS
                CashierDrawerItem.SCHEDULED_ORDERS -> domainFeatures.hasPreOrders
                CashierDrawerItem.PRESCRIPTIONS -> domainFeatures.hasPrescriptions
                CashierDrawerItem.SPLIT_PAYMENT -> domainFeatures.hasSplitPayments
                CashierDrawerItem.CUSTOMER_CREDIT -> domainFeatures.hasCustomerCredit
                else -> true
            }
        }
    }

    val showNav = allRoutes.any { route ->
        currentDestination?.hierarchy?.any { dest ->
            dest.route == route || dest.route?.startsWith("$route?") == true
        } == true
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
                if (order.paymentTiming == PaymentTiming.PAY_NOW) {
                    navController.navigateToPayment(order.id)
                }
                // PAY_LATER: stay on POS, no navigation to payment screen
            },
        )
        composable(CashierTab.ORDERS.route) {
            OrdersScreen(
                onViewReceipt = { orderId ->
                    navController.navigateToReceipt(orderId)
                },
                onSplitPayment = { orderId ->
                    navController.navigate("${CashierDrawerItem.SPLIT_PAYMENT.route}/$orderId")
                },
            )
        }
        composable(CashierTab.TABLES.route) {
            TablesScreen(
                readOnly = true,
                onStartOrder = { tableId, reservationId, clientName, clientPhone ->
                    navController.navigateToPosWithReservation(tableId, reservationId, clientName, clientPhone)
                },
            )
        }
        composable(CashierDrawerItem.DELIVERY.route) { DeliveryDashboardScreen() }
        composable(CashierDrawerItem.ANNOUNCEMENTS.route) { AnnouncementsScreen() }
        composable(CashierDrawerItem.ATTENDANCE.route) { AttendanceScreen() }
        composable(CashierDrawerItem.OVERTIME.route) {
            CashierOvertimeScreen()
        }
        composable(CashierDrawerItem.KDS.route) {
            KdsScreen()
        }
        composable(CashierDrawerItem.CASH_DRAWER.route) {
            CashDrawerScreen()
        }
        composable(CashierDrawerItem.SCHEDULED_ORDERS.route) {
            ScheduledOrdersScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(CashierDrawerItem.NOTIFICATIONS.route) {
            CashierNotificationsScreen()
        }
        composable(CashierDrawerItem.PRESCRIPTIONS.route) {
            PrescriptionsScreen()
        }
        composable(CashierDrawerItem.SPLIT_PAYMENT.route) {
            SplitPaymentScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable("${CashierDrawerItem.SPLIT_PAYMENT.route}/{orderId}") {
            SplitPaymentScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(CashierDrawerItem.CUSTOMER_CREDIT.route) {
            CashierCustomerCreditScreen()
        }
        composable(CashierDrawerItem.PROFILE.route) {
            CashierProfileScreen(
                userName = currentUser?.name,
                userPhone = currentUser?.phone,
                userEmail = currentUser?.email,
                userRole = roleLabel,
                userPhotoUrl = currentUser?.photoUrl,
                vendor = vendor,
                onSignOut = onSignOut,
                onViewShiftSummary = onViewShiftSummary,
                pendingSyncItems = pendingSyncItems,
                isSyncing = isSyncing,
                lastSyncResult = lastSyncResult,
                onSyncNow = {
                    scope.launch { syncScheduler.triggerManualSync() }
                },
                onRetrySyncItem = { id ->
                    scope.launch { pendingSyncDao.updateRetry(id, 0, null) }
                },
                onDeleteSyncItem = { id ->
                    scope.launch { pendingSyncDao.deletePending(id) }
                },
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

    // Blocking dialog: offline and offline mode not enabled by manager
    if (showNoConnectionDialog) {
        AlertDialog(
            onDismissRequest = { /* non-dismissible */ },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(CoreRes.string.no_connection_title)) },
            text = { Text(stringResource(CoreRes.string.no_connection_message)) },
            confirmButton = {
                Button(onClick = { /* connectivity auto-updates via NetworkMonitor */ }) {
                    Text(stringResource(CoreRes.string.retry_connection))
                }
            },
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= 600.dp

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
                        vendor = vendor,
                        onItemClick = { scope.launch { drawerState.close() } },
                        visibleDrawerItems = visibleDrawerItems,
                    )
                },
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (showNav) {
                        CashierNavRail(navController, currentDestination, visibleTabs, vendor?.businessType ?: "RESTAURANT")
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                    }
                    Column(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()) {
                        if (showNav) {
                            TopAppBar(
                                title = {
                                    currentUser?.name?.let { name ->
                                        Text(
                                            text = stringResource(CoreRes.string.welcome_name, name),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, contentDescription = stringResource(CoreRes.string.menu))
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                        AnimatedVisibility(
                            visible = isOffline,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            OfflineBanner(pendingCount = pendingCount)
                        }
                        AnimatedVisibility(
                            visible = isSyncing && !isOffline,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            SyncingBanner(pendingCount = pendingCount)
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
                        vendor = vendor,
                        onItemClick = { scope.launch { drawerState.close() } },
                        visibleDrawerItems = visibleDrawerItems,
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
                                            text = stringResource(CoreRes.string.welcome_name, name),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, contentDescription = stringResource(CoreRes.string.menu))
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    },
                    bottomBar = {
                        if (showNav) CashierBottomBar(navController, currentDestination, visibleTabs, vendor?.businessType ?: "RESTAURANT")
                    },
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        AnimatedVisibility(
                            visible = isOffline,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            OfflineBanner(pendingCount = pendingCount)
                        }
                        AnimatedVisibility(
                            visible = isSyncing && !isOffline,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            SyncingBanner(pendingCount = pendingCount)
                        }
                        NavHost(
                            navController = navController,
                            startDestination = AUTH_ROUTE,
                            modifier = Modifier.weight(1f),
                            builder = navGraphBuilder,
                        )
                    }
                }
            }
        }
    }
}
