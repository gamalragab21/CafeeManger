package net.marllex.cafeemanger.feature.manager.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderChannel
import net.marllex.cafeemanger.core.model.Stock
import net.marllex.cafeemanger.core.ui.components.ChannelChip
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.core.ui.components.OrderStatusChip
import net.marllex.cafeemanger.core.ui.components.formatStatusLabel
import net.marllex.cafeemanger.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    val logoUrl = uiState.vendor?.logoUrl
                    if (!logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Store, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                title = {
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            text = uiState.vendor?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        uiState.userName?.let { name ->
                            Text(
                                text = stringResource(CoreR.string.welcome_message, name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadDashboard,
            )
            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .then(if (isTablet) Modifier.widthIn(max = 840.dp) else Modifier),
                    contentPadding = PaddingValues(if (isTablet) 24.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                item {
                    Text(
                        text = stringResource(R.string.active_orders),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                item {
                    if (isTablet) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ExpandableStatCard(
                                title = stringResource(R.string.active_orders),
                                value = uiState.activeOrdersCount.toString(),
                                icon = Icons.Filled.Pending,
                                hint = stringResource(R.string.tap_to_expand),
                                modifier = Modifier.weight(1f),
                            ) {
                                ActiveOrdersDetail(uiState.activeOrders)
                            }
                            ExpandableStatCard(
                                title = stringResource(R.string.today_s_orders),
                                value = uiState.todayOrdersCount.toString(),
                                icon = Icons.Filled.Receipt,
                                hint = stringResource(R.string.tap_to_expand),
                                modifier = Modifier.weight(1f),
                            ) {
                                TodayOrdersDetail(uiState.todayOrders)
                            }
                            ExpandableStatCard(
                                title = stringResource(R.string.today_s_revenue),
                                value = String.format("%.2f", uiState.todayRevenue),
                                icon = Icons.Filled.AttachMoney,
                                hint = stringResource(R.string.tap_to_expand),
                                modifier = Modifier.weight(1f),
                            ) {
                                RevenueDetail(uiState.todayRevenueByPayment)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ExpandableStatCard(
                                title = stringResource(R.string.active_orders),
                                value = uiState.activeOrdersCount.toString(),
                                icon = Icons.Filled.Pending,
                                hint = stringResource(R.string.tap_to_expand),
                                modifier = Modifier.weight(1f),
                            ) {
                                ActiveOrdersDetail(uiState.activeOrders)
                            }
                            ExpandableStatCard(
                                title = stringResource(R.string.today_s_orders),
                                value = uiState.todayOrdersCount.toString(),
                                icon = Icons.Filled.Receipt,
                                hint = stringResource(R.string.tap_to_expand),
                                modifier = Modifier.weight(1f),
                            ) {
                                TodayOrdersDetail(uiState.todayOrders)
                            }
                        }
                    }
                }

                if (!isTablet) {
                    item {
                        ExpandableStatCard(
                            title = stringResource(R.string.today_s_revenue),
                            value = String.format("%.2f", uiState.todayRevenue),
                            icon = Icons.Filled.AttachMoney,
                            hint = stringResource(R.string.tap_to_expand),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RevenueDetail(uiState.todayRevenueByPayment)
                        }
                    }
                }

                // Stock Summary Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.stock_overview),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            title = stringResource(R.string.total_items),
                            value = uiState.totalStockItems.toString(),
                            icon = Icons.Filled.Inventory,
                            modifier = Modifier.weight(1f),
                        )
                        val hasAlerts = uiState.lowStockCount > 0 || uiState.outOfStockCount > 0
                        ExpandableAlertCard(
                            alertCount = uiState.lowStockCount + uiState.outOfStockCount,
                            lowCount = uiState.lowStockCount,
                            outCount = uiState.outOfStockCount,
                            hasAlerts = hasAlerts,
                            modifier = Modifier.weight(1f),
                        ) {
                            StockAlertsDetail(uiState.lowStockItems, uiState.outOfStockItems)
                        }
                    }
                }

                item {
                    StatCard(
                        title = stringResource(R.string.stock_value),
                        value = String.format("%.2f EGP", uiState.totalStockValue),
                        icon = Icons.Filled.AttachMoney,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.recent_orders),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                items(uiState.recentOrders, key = { it.id }) { order ->
                    RecentOrderCard(order = order)
                }
                }
            }
        }
    }
}

@Composable
private fun ExpandableStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    hint: String,
    modifier: Modifier = Modifier,
    expandedContent: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!expanded) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    expandedContent()
                }
            }
        }
    }
}

@Composable
private fun ExpandableAlertCard(
    alertCount: Int,
    lowCount: Int,
    outCount: Int,
    hasAlerts: Boolean,
    modifier: Modifier = Modifier,
    expandedContent: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.clickable(enabled = hasAlerts) { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasAlerts) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (hasAlerts) Color(0xFFFF9800).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (hasAlerts) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (hasAlerts) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$alertCount",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (hasAlerts) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.stock_alerts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasAlerts) {
                Text(
                    text = "$lowCount ${stringResource(R.string.low)} • $outCount ${stringResource(R.string.out)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE65100),
                )

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFE65100).copy(alpha = 0.2f))
                        Spacer(Modifier.height(8.dp))
                        expandedContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOrdersDetail(orders: List<Order>) {
    if (orders.isEmpty()) return
    val byStatus = orders.groupBy { it.status }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.by_status),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        byStatus.forEach { (status, statusOrders) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatStatusLabel(status),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "${statusOrders.size}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TodayOrdersDetail(orders: List<Order>) {
    if (orders.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val byChannel = orders.groupBy { it.channel }
        Text(
            stringResource(R.string.by_channel),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        byChannel.forEach { (channel, channelOrders) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    when (channel) {
                        OrderChannel.DINE_IN -> stringResource(CoreR.string.channel_dine_in)
                        OrderChannel.DELIVERY -> stringResource(CoreR.string.channel_delivery)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "${channelOrders.size}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RevenueDetail(revenueByPayment: Map<String, Double>) {
    if (revenueByPayment.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.by_payment),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        revenueByPayment.forEach { (method, amount) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    when (method) {
                        "CASH" -> stringResource(CoreR.string.payment_cash)
                        "WALLET" -> stringResource(CoreR.string.payment_wallet)
                        "CARD" -> stringResource(CoreR.string.payment_card)
                        else -> method
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    String.format("%.2f", amount),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StockAlertsDetail(lowStockItems: List<Stock>, outOfStockItems: List<Stock>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (outOfStockItems.isNotEmpty()) {
            Text(
                stringResource(R.string.out_of_stock_items),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD32F2F),
            )
            outOfStockItems.take(5).forEach { stock ->
                Text(
                    "• ${stock.itemName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE65100),
                )
            }
        }
        if (lowStockItems.isNotEmpty()) {
            if (outOfStockItems.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.low_stock_items),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE65100),
            )
            lowStockItems.take(5).forEach { stock ->
                Text(
                    "• ${stock.itemName} (${stock.quantity}/${stock.minQuantity})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE65100),
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentOrderCard(order: Order) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${order.id.takeLast(6).uppercase()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelChip(channel = order.channel.name)
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
                OrderStatusChip(status = order.status)
                Text(
                    text = String.format("%.2f", order.total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
