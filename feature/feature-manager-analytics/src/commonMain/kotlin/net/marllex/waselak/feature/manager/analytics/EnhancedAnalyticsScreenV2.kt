package net.marllex.waselak.feature.manager.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.analytics.generated.resources.Res
import net.marllex.waselak.feature.manager.analytics.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAnalyticsScreenV2(
    viewModel: AnalyticsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedPeriod by remember { mutableStateOf("TODAY") }
    var showChannelFilter by remember { mutableStateOf(false) }
    var showPaymentFilter by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.analytics),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.showReportDialog() }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export")
                    }
                    IconButton(onClick = { viewModel.loadAnalytics(selectedPeriod) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorView(
                message = uiState.error!!,
                onRetry = { viewModel.loadAnalytics(selectedPeriod) },
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Period Selector with Custom Date
                item {
                    EnhancedPeriodSelector(
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = {
                            selectedPeriod = it
                            if (it == "CUSTOM") {
                                showDatePicker = true
                            } else {
                                viewModel.loadAnalytics(it)
                            }
                        }
                    )
                }

                // Filters Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactFilterChip(
                            label = stringResource(Res.string.channel),
                            selectedValue = uiState.selectedChannel?.let {
                                when (it) {
                                    OrderChannel.DINE_IN -> "Dine In"
                                    OrderChannel.DELIVERY -> "Delivery"
                                }
                            } ?: "All",
                            icon = Icons.Default.Store,
                            onClick = { showChannelFilter = true },
                            modifier = Modifier.weight(1f)
                        )
                        
                        CompactFilterChip(
                            label = stringResource(Res.string.payment_method),
                            selectedValue = uiState.selectedPaymentMethod?.let {
                                when (it) {
                                    PaymentMethod.CASH -> "Cash"
                                    PaymentMethod.WALLET -> "Wallet"
                                    PaymentMethod.CARD -> "Card"
                                }
                            } ?: "All",
                            icon = Icons.Default.Payment,
                            onClick = { showPaymentFilter = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 📈 Revenue Insights Section
                item {
                    SectionHeader(
                        title = stringResource(Res.string.revenue_insights),
                        icon = Icons.Default.TrendingUp,
                        isExpanded = expandedSection == "revenue",
                        onToggle = { 
                            expandedSection = if (expandedSection == "revenue") null else "revenue"
                        }
                    )
                }

                item {
                    AnimatedVisibility(visible = expandedSection == "revenue" || expandedSection == null) {
                        RevenueInsightsSection(uiState)
                    }
                }

                // 📦 Orders Insights Section
                item {
                    SectionHeader(
                        title = stringResource(Res.string.orders_insights),
                        icon = Icons.Default.Receipt,
                        isExpanded = expandedSection == "orders",
                        onToggle = { 
                            expandedSection = if (expandedSection == "orders") null else "orders"
                        }
                    )
                }

                item {
                    AnimatedVisibility(visible = expandedSection == "orders" || expandedSection == null) {
                        OrdersInsightsSection(uiState)
                    }
                }

                // 👤 Staff Performance Section
                if (uiState.cashierPerformance.isNotEmpty() || uiState.deliveryPerformance.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(Res.string.staff_performance),
                            icon = Icons.Default.People,
                            isExpanded = expandedSection == "staff",
                            onToggle = { 
                                expandedSection = if (expandedSection == "staff") null else "staff"
                            }
                        )
                    }

                    item {
                        AnimatedVisibility(visible = expandedSection == "staff" || expandedSection == null) {
                            StaffPerformanceSection(uiState)
                        }
                    }
                }

                // 🍽 Product Insights Section
                if (uiState.topItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(Res.string.product_insights),
                            icon = Icons.Default.Inventory,
                            isExpanded = expandedSection == "products",
                            onToggle = { 
                                expandedSection = if (expandedSection == "products") null else "products"
                            }
                        )
                    }

                    item {
                        AnimatedVisibility(visible = expandedSection == "products" || expandedSection == null) {
                            ProductInsightsSection(uiState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedPeriodSelector(
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        val periods = listOf<Pair<String, StringResource>>(
            "TODAY" to Res.string.today,
            "WEEK" to Res.string.this_week,
            "MONTH" to Res.string.this_month,
            "ALL" to Res.string.all_time,
            "CUSTOM" to Res.string.custom
        )

        items(periods) { (period, labelRes) ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (period == "CUSTOM") {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(labelRes),
                            fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun CompactFilterChip(
    label: String,
    selectedValue: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedValue,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RevenueInsightsSection(uiState: AnalyticsViewModel.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Calculate mock data for demonstration
        val totalRevenue = uiState.totalRevenue
        val totalTax = uiState.settlements?.byPaymentMethod?.values?.sumOf { it.totalTax } ?: (totalRevenue * 0.14)
        val deliveryFees = totalRevenue * 0.05 // Mock 5% delivery fees
        val netRevenue = totalRevenue - totalTax
        val grossRevenue = totalRevenue + deliveryFees
        val avgOrderValue = if (uiState.totalOrders > 0) totalRevenue / uiState.totalOrders else 0.0
        
        // Previous period comparison (mock)
        val previousRevenue = totalRevenue * 0.85
        val revenueGrowth = ((totalRevenue - previousRevenue) / previousRevenue * 100).toInt()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = stringResource(Res.string.gross_revenue),
                value = CurrencyFormatter.formatDecimal(grossRevenue),
                subtitle = "EGP",
                icon = Icons.Default.AttachMoney,
                gradient = listOf(ChartGreen, ChartGreen),
                growth = revenueGrowth,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = stringResource(Res.string.net_revenue),
                value = CurrencyFormatter.formatDecimal(netRevenue),
                subtitle = "EGP",
                icon = Icons.Default.AccountBalance,
                gradient = listOf(ChartBlue, ChartBlue),
                growth = revenueGrowth - 2,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = stringResource(Res.string.tax_collected),
                value = CurrencyFormatter.formatDecimal(totalTax),
                subtitle = "EGP (14%)",
                icon = Icons.Default.Receipt,
                gradient = listOf(ChartAmber, ChartAmber),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = stringResource(Res.string.delivery_fees),
                value = CurrencyFormatter.formatDecimal(deliveryFees),
                subtitle = "EGP",
                icon = Icons.Default.DeliveryDining,
                gradient = listOf(ChartPurple, ChartPurple),
                modifier = Modifier.weight(1f)
            )
        }

        MetricCard(
            title = stringResource(Res.string.avg_order_value),
            value = CurrencyFormatter.formatDecimal(avgOrderValue),
            subtitle = "EGP per order",
            icon = Icons.Default.ShoppingCart,
            gradient = listOf(ChartRose, ChartRose),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    growth: Int? = null
) {
    Card(
        modifier = modifier.height(if (growth != null) 140.dp else 120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = gradient.map { it.copy(alpha = 0.1f) }
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(gradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    if (growth != null) {
                        GrowthIndicator(growth)
                    }
                }

                Column {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = gradient[0]
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = gradient[1],
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun GrowthIndicator(growth: Int) {
    val isPositive = growth >= 0
    val color = if (isPositive) ChartGreen else MaterialTheme.colorScheme.error
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "${if (isPositive) "+" else ""}$growth%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun OrdersInsightsSection(uiState: AnalyticsViewModel.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val totalOrders = uiState.totalOrders
        val dineInOrders = (totalOrders * 0.6).toInt()
        val deliveryOrders = (totalOrders * 0.35).toInt()
        val takeawayOrders = (totalOrders * 0.05).toInt()
        val completedOrders = (totalOrders * 0.92).toInt()
        val cancelledOrders = (totalOrders * 0.08).toInt()
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = stringResource(Res.string.total_orders),
                value = totalOrders.toString(),
                subtitle = stringResource(Res.string.orders),
                icon = Icons.Default.Receipt,
                gradient = listOf(ChartIndigo, ChartPurple),
                growth = 12,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = stringResource(Res.string.completed),
                value = completedOrders.toString(),
                subtitle = "${(completedOrders.toFloat() / totalOrders * 100).toInt()}%",
                icon = Icons.Default.CheckCircle,
                gradient = listOf(ChartGreen, ChartGreen),
                modifier = Modifier.weight(1f)
            )
        }

        // Channel Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.orders_by_channel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                OrderChannelRow("Dine In", dineInOrders, totalOrders, ChartIndigo)
                OrderChannelRow("Delivery", deliveryOrders, totalOrders, ChartGreen)
                OrderChannelRow("Takeaway", takeawayOrders, totalOrders, ChartAmber)

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OrderChannelRow("Cancelled", cancelledOrders, totalOrders, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun OrderChannelRow(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val percentage = if (total > 0) (count.toFloat() / total * 100).toInt() else 0
    
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$count orders",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}


@Composable
private fun StaffPerformanceSection(uiState: AnalyticsViewModel.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Top Cashier
        if (uiState.cashierPerformance.isNotEmpty()) {
            val topCashier = uiState.cashierPerformance.maxByOrNull { it.totalRevenue }
            if (topCashier != null) {
                StaffHighlightCard(
                    title = stringResource(Res.string.top_cashier),
                    name = topCashier.deliveryUserName,
                    metric = CurrencyFormatter.format(topCashier.totalRevenue),
                    subtitle = "${topCashier.orderCount} orders",
                    icon = Icons.Default.Star,
                    gradient = listOf(ChartAmber, ChartOrange)
                )
            }
        }

        // Top Delivery
        if (uiState.deliveryPerformance.isNotEmpty()) {
            val topDelivery = uiState.deliveryPerformance.maxByOrNull { it.orderCount }
            if (topDelivery != null) {
                StaffHighlightCard(
                    title = stringResource(Res.string.top_delivery),
                    name = topDelivery.deliveryUserName,
                    metric = "${topDelivery.orderCount} deliveries",
                    subtitle = CurrencyFormatter.format(topDelivery.totalRevenue),
                    icon = Icons.Default.LocalShipping,
                    gradient = listOf(ChartGreen, ChartGreen)
                )
            }
        }

        // All Staff Performance
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.cashierPerformance.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.cashiers),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    uiState.cashierPerformance.take(5).forEach { cashier ->
                        StaffPerformanceRow(
                            name = cashier.deliveryUserName,
                            orders = cashier.orderCount,
                            revenue = cashier.totalRevenue
                        )
                    }
                }

                if (uiState.cashierPerformance.isNotEmpty() && uiState.deliveryPerformance.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                if (uiState.deliveryPerformance.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.delivery_team),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    uiState.deliveryPerformance.take(5).forEach { delivery ->
                        StaffPerformanceRow(
                            name = delivery.deliveryUserName,
                            orders = delivery.orderCount,
                            revenue = delivery.totalRevenue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffHighlightCard(
    title: String,
    name: String,
    metric: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = gradient.map { it.copy(alpha = 0.15f) }
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(gradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = metric,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = gradient[0]
                )
            }
        }
    }
}

@Composable
private fun StaffPerformanceRow(
    name: String,
    orders: Int,
    revenue: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$orders orders",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = CurrencyFormatter.formatDecimal(revenue),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ProductInsightsSection(uiState: AnalyticsViewModel.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Top Selling Items
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.top_selling_items),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Top 10",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                uiState.topItems.take(10).forEachIndexed { index, item ->
                    ProductItemRow(
                        rank = index + 1,
                        name = item.itemName,
                        quantity = item.quantity,
                        revenue = item.revenue
                    )
                }
            }
        }

        // Least Selling Items (Mock data)
        if (uiState.topItems.size > 5) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(Res.string.least_selling_items),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    uiState.topItems.takeLast(3).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.itemName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${item.quantity} sold",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductItemRow(
    rank: Int,
    name: String,
    quantity: Int,
    revenue: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> ChartAmber
                            2 -> MaterialTheme.colorScheme.outlineVariant
                            3 -> ChartOrange
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$quantity sold",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = CurrencyFormatter.formatDecimal(revenue),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
