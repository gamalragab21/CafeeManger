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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
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
import net.marllex.waselak.core.ui.components.WaslekLogo
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
import net.marllex.waselak.feature.cashier.pos.navigation.posScreen
import net.marllex.waselak.feature.cashier.receipt.navigation.navigateToReceipt
import net.marllex.waselak.feature.cashier.receipt.navigation.receiptScreen
import net.marllex.waselak.feature.manager.orders.OrdersScreen
import net.marllex.waselak.feature.manager.staff.AnnouncementsScreen
import net.marllex.waselak.feature.manager.staff.DeliveryDashboardScreen
import net.marllex.waselak.feature.manager.tables.TablesScreen

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
    PROFILE("cashier/profile", "Profile", Icons.Filled.Person),
}

private val allRoutes = CashierTab.entries.map { it.route } + CashierDrawerItem.entries.map { it.route }

@Composable
private fun localizedTabTitle(tab: CashierTab): String = when (tab) {
    CashierTab.POS -> stringResource(CoreRes.string.nav_new_order)
    CashierTab.ORDERS -> stringResource(CoreRes.string.nav_orders)
    CashierTab.TABLES -> stringResource(CoreRes.string.nav_tables)
}

@Composable
private fun localizedDrawerTitle(item: CashierDrawerItem): String = when (item) {
    CashierDrawerItem.DELIVERY -> stringResource(CoreRes.string.nav_delivery)
    CashierDrawerItem.ANNOUNCEMENTS -> stringResource(CoreRes.string.nav_alerts)
    CashierDrawerItem.ATTENDANCE -> stringResource(CoreRes.string.nav_attendance)
    CashierDrawerItem.PROFILE -> stringResource(CoreRes.string.nav_profile)
}

@Composable
private fun localizedRoleLabel(role: UserRole?): String = when (role) {
    UserRole.MANAGER -> stringResource(CoreRes.string.role_manager)
    UserRole.CASHIER -> stringResource(CoreRes.string.role_cashier)
    UserRole.DELIVERY -> stringResource(CoreRes.string.role_delivery)
    null -> ""
}

// ─── Bottom Bar (phone) ──────────────────────────────────────────
@Composable
private fun CashierBottomBar(
    navController: NavController,
    currentDestination: NavDestination?,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        CashierTab.entries.forEach { tab ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == tab.route } == true
            val title = localizedTabTitle(tab)

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
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CashierTab.entries.forEach { tab ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == tab.route } == true
            val title = localizedTabTitle(tab)

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
            if (!vendor?.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = vendor?.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop,
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

        CashierDrawerItem.entries.forEach { item ->
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

// ─── Profile Screen ──────────────────────────────────────────────
@Composable
private fun CashierProfileScreen(
    userName: String?,
    userPhone: String?,
    userEmail: String?,
    userRole: String?,
    vendor: Vendor?,
    onSignOut: () -> Unit,
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
            // Profile header card with store logo
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
                        if (!vendor?.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = vendor?.logoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentScale = ContentScale.Crop,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurface,
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

    // Sign-out with biometric verification (system prompt on Android/iOS)
    val onSignOut: () -> Unit = remember(scope) {
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
                    authRepository.logout()
                    navController.navigate(AUTH_ROUTE) {
                        popUpTo(0) { inclusive = true }
                    }
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
                vendor = vendor,
                onSignOut = onSignOut,
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
                                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
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
