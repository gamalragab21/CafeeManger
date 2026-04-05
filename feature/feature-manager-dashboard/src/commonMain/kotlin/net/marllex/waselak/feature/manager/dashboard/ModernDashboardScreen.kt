package net.marllex.waselak.feature.manager.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.Image
import coil3.compose.AsyncImage
import net.marllex.waselak.core.ui.components.waslekLogoPainter
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.ui.components.ChannelChip
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.OrderStatusChip
import net.marllex.waselak.feature.manager.dashboard.components.*
import net.marllex.waselak.feature.manager.dashboard.generated.resources.Res
import net.marllex.waselak.feature.manager.dashboard.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// ══════════════════════════════════════════════════════════════════════
// Branded Top Bar — kept from original
// ══════════════════════════════════════════════════════════════════════

@Composable
fun BrandedTopBar(uiState: HomeDashboardUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Logo
            val logoUrl = uiState.vendor?.logoUrl
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val logoPainter = waslekLogoPainter()
                if (!logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = logoPainter,
                        error = logoPainter,
                    )
                } else {
                    Image(
                        painter = logoPainter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Identity
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.vendor?.name ?: stringResource(Res.string.our_store),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                uiState.userName?.let { name ->
                    Text(
                        text = stringResource(Res.string.welcome_message, name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = { /* TODO: notifications */ }) {
                Icon(Icons.Rounded.NotificationsNone, contentDescription = null)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Main Dashboard Screen
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModernDashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
    onNavigateToChatbot: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-refresh when screen becomes visible
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadDashboard()
        }
    }

    Scaffold(
        topBar = { BrandedTopBar(uiState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Section 1: Today Snapshot (KPI Cards) ──────────────────
            item {
                TodaySnapshotSection(
                    state = uiState.executiveSummary,
                    onRetry = viewModel::loadDashboard,
                    showDelivery = uiState.vendor?.enableDelivery != false,
                )
            }

            // ── Section 2: Real-Time Alerts ────────────────────────────
            item {
                AlertsSection(
                    state = uiState.alerts,
                    onRetry = viewModel::loadDashboard,
                )
            }

            // ── Section 3: Top Performance ─────────────────────────────
            item {
                TopPerformanceSection(
                    topProducts = uiState.topProducts,
                    bestCashier = uiState.bestCashier,
                    bestDriver = uiState.bestDriver,
                    onRetry = viewModel::loadDashboard,
                    showDelivery = uiState.vendor?.enableDelivery != false,
                )
            }

            // ── Section 4: Stock Health ────────────────────────────────
            item {
                StockHealthSection(
                    state = uiState.stockHealth,
                    onRetry = viewModel::loadDashboard,
                )
            }

            // ── Section 5: Recent Orders ───────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.recent_orders),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${uiState.recentOrders.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (uiState.recentOrders.isEmpty() && !uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.no_recent_orders),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(uiState.recentOrders, key = { it.id }) { order ->
                    ModernOrderCard(order = order)
                }
            }
        }
        } // PullToRefreshBox
    }
}

// ══════════════════════════════════════════════════════════════════════
// Section 1: Today Snapshot
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaySnapshotSection(
    state: SectionState<net.marllex.waselak.core.model.ExecutiveSummary>,
    onRetry: () -> Unit,
    showDelivery: Boolean = true,
) {
    SectionContainer(
        title = stringResource(Res.string.today_snapshot),
        state = state,
        onRetry = onRetry,
    ) { summary ->
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            KpiCard(
                label = stringResource(Res.string.total_revenue),
                value = "${formatCurrency(summary.current.totalRevenue)} EGP",
                changePercent = summary.revenueChangePercent,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.total_orders),
                value = formatNumber(summary.current.totalOrders),
                changePercent = summary.ordersChangePercent,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.avg_order_value),
                value = "${formatCurrency(summary.current.averageOrderValue)} EGP",
                changePercent = summary.aovChangePercent,
                modifier = Modifier.weight(1f),
            )
            if (showDelivery) {
                KpiCard(
                    label = stringResource(Res.string.delivery_fees),
                    value = "${formatCurrency(summary.current.totalDeliveryFees)} EGP",
                    modifier = Modifier.weight(1f),
                )
            }
            KpiCard(
                label = stringResource(Res.string.active_orders),
                value = formatNumber(summary.activeOrders),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(Res.string.staff_present),
                value = formatNumber(summary.attendanceToday),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Section 2: Alerts
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun AlertsSection(
    state: SectionState<List<net.marllex.waselak.core.model.AnalyticsAlert>>,
    onRetry: () -> Unit,
) {
    SectionContainer(
        title = stringResource(Res.string.alerts),
        state = state,
        onRetry = onRetry,
    ) { alerts ->
        if (alerts.isEmpty()) {
            AllClearCard()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alerts.take(5).forEach { alert ->
                    AlertCard(alert = alert)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Section 3: Top Performance
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun TopPerformanceSection(
    topProducts: SectionState<List<net.marllex.waselak.core.model.ProductItem>>,
    bestCashier: SectionState<net.marllex.waselak.core.model.CashierPerformanceV2?>,
    bestDriver: SectionState<net.marllex.waselak.core.model.DeliveryPerformanceV2?>,
    onRetry: () -> Unit,
    showDelivery: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.top_performance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            // Top 5 Products
            Text(
                text = stringResource(Res.string.top_products),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            when (topProducts) {
                is SectionState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                is SectionState.Error -> {
                    Text(
                        text = topProducts.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is SectionState.Success -> {
                    if (topProducts.data.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.no_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SimpleBarChart(
                            items = topProducts.data.map { it.itemName to it.quantitySold.toDouble() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Best Cashier + Best Driver side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Best Cashier
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.best_cashier),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    when (bestCashier) {
                        is SectionState.Loading -> {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        is SectionState.Error -> {
                            Text(
                                text = bestCashier.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        is SectionState.Success -> {
                            val cashier = bestCashier.data
                            if (cashier != null) {
                                StaffHighlightCard(
                                    name = cashier.cashierName,
                                    metric1Label = stringResource(Res.string.revenue_label),
                                    metric1Value = "${formatCurrency(cashier.revenue)} EGP",
                                    metric2Label = stringResource(Res.string.orders_label),
                                    metric2Value = cashier.orderCount.toString(),
                                )
                            } else {
                                Text(
                                    text = stringResource(Res.string.no_data),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Best Driver (only if delivery is enabled)
                if (showDelivery) Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.best_driver),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    when (bestDriver) {
                        is SectionState.Loading -> {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        is SectionState.Error -> {
                            Text(
                                text = bestDriver.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        is SectionState.Success -> {
                            val driver = bestDriver.data
                            if (driver != null) {
                                StaffHighlightCard(
                                    name = driver.driverName,
                                    metric1Label = stringResource(Res.string.deliveries_label),
                                    metric1Value = driver.ordersCompleted.toString(),
                                    metric2Label = stringResource(Res.string.fees_collected),
                                    metric2Value = "${formatCurrency(driver.feesCollected)} EGP",
                                )
                            } else {
                                Text(
                                    text = stringResource(Res.string.no_data),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Section 4: Stock Health
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun StockHealthSection(
    state: SectionState<net.marllex.waselak.core.model.StockOverview>,
    onRetry: () -> Unit,
) {
    SectionContainer(
        title = stringResource(Res.string.stock_health),
        state = state,
        onRetry = onRetry,
    ) { stock ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Stock Value
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(Res.string.stock_value),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${CurrencyFormatter.format(stock.totalStockValue)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StockBadge(
                    label = stringResource(Res.string.low_stock_label),
                    count = stock.lowStockItems.size,
                    color = Color(0xFFF59E0B), // Amber
                    modifier = Modifier.weight(1f),
                )
                StockBadge(
                    label = stringResource(Res.string.out_of_stock_label),
                    count = stock.outOfStockItems.size,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                StockBadge(
                    label = stringResource(Res.string.dead_stock_label),
                    count = stock.deadStockItems.size,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Order Card — kept from original
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun ModernOrderCard(order: Order) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "#${order.id.takeLast(6).uppercase()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        order.clientName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = CurrencyFormatter.format(order.total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OrderStatusChip(status = order.status)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelChip(channel = order.channel.name)
                Text(
                    text = "\u2022",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${order.items.size} ${stringResource(Res.string.items_label)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
