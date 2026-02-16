package net.marllex.waselak.feature.manager.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import waselak.core.core_ui.generated.resources.Res as CoreUiRes
import waselak.core.core_ui.generated.resources.channel_dine_in
import waselak.core.core_ui.generated.resources.channel_delivery
import waselak.core.core_ui.generated.resources.payment_cash
import waselak.core.core_ui.generated.resources.payment_wallet
import waselak.core.core_ui.generated.resources.payment_card
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
fun ModernAnalyticsScreen(
    viewModel: AnalyticsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedPeriod by remember { mutableStateOf("TODAY") }
    var showChannelFilter by remember { mutableStateOf(false) }
    var showPaymentFilter by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.analytics),
                        fontWeight = FontWeight.Bold
                    )
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
                // Period Selector
                item {
                    PeriodSelector(
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = {
                            selectedPeriod = it
                            viewModel.loadAnalytics(it)
                        }
                    )
                }

                // Quick Stats Overview
                item {
                    Text(
                        text = stringResource(Res.string.overview),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DataCard(
                            title = stringResource(Res.string.total_revenue),
                            value = CurrencyFormatter.formatDecimal(uiState.totalRevenue),
                            subtitle = "EGP",
                            icon = Icons.Default.AttachMoney,
                            gradient = listOf(ChartGreen, ChartGreen),
                            modifier = Modifier.weight(1f)
                        )
                        DataCard(
                            title = stringResource(Res.string.total_orders),
                            value = uiState.totalOrders.toString(),
                            subtitle = stringResource(Res.string.orders),
                            icon = Icons.Default.Receipt,
                            gradient = listOf(ChartIndigo, ChartPurple),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Filters Section
                item {
                    Text(
                        text = stringResource(Res.string.filters),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Channel Filter
                        FilterDropdown(
                            label = stringResource(Res.string.channel),
                            selectedValue = uiState.selectedChannel?.let {
                                when (it) {
                                    OrderChannel.DINE_IN -> stringResource(CoreUiRes.string.channel_dine_in)
                                    OrderChannel.DELIVERY -> stringResource(CoreUiRes.string.channel_delivery)
                                }
                            } ?: stringResource(Res.string.all),
                            icon = Icons.Default.Store,
                            expanded = showChannelFilter,
                            onExpandedChange = { showChannelFilter = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.all)) },
                                onClick = {
                                    viewModel.filterByChannel(null)
                                    showChannelFilter = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(CoreUiRes.string.channel_dine_in)) },
                                onClick = {
                                    viewModel.filterByChannel(OrderChannel.DINE_IN)
                                    showChannelFilter = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(CoreUiRes.string.channel_delivery)) },
                                onClick = {
                                    viewModel.filterByChannel(OrderChannel.DELIVERY)
                                    showChannelFilter = false
                                }
                            )
                        }

                        // Payment Method Filter
                        FilterDropdown(
                            label = stringResource(Res.string.payment_method),
                            selectedValue = uiState.selectedPaymentMethod?.let {
                                when (it) {
                                    PaymentMethod.CASH -> stringResource(CoreUiRes.string.payment_cash)
                                    PaymentMethod.WALLET -> stringResource(CoreUiRes.string.payment_wallet)
                                    PaymentMethod.CARD -> stringResource(CoreUiRes.string.payment_card)
                                }
                            } ?: stringResource(Res.string.all),
                            icon = Icons.Default.Payment,
                            expanded = showPaymentFilter,
                            onExpandedChange = { showPaymentFilter = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.all)) },
                                onClick = {
                                    viewModel.filterByPaymentMethod(null)
                                    showPaymentFilter = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(CoreUiRes.string.payment_cash)) },
                                onClick = {
                                    viewModel.filterByPaymentMethod(PaymentMethod.CASH)
                                    showPaymentFilter = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(CoreUiRes.string.payment_wallet)) },
                                onClick = {
                                    viewModel.filterByPaymentMethod(PaymentMethod.WALLET)
                                    showPaymentFilter = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(CoreUiRes.string.payment_card)) },
                                onClick = {
                                    viewModel.filterByPaymentMethod(PaymentMethod.CARD)
                                    showPaymentFilter = false
                                }
                            )
                        }
                    }
                }

                // Revenue Breakdown
                item {
                    Text(
                        text = stringResource(Res.string.revenue_breakdown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    RevenueBreakdownCard(
                        byChannel = uiState.revenueByChannel,
                        byPayment = uiState.revenueByPayment
                    )
                }

                // Top Performing Items
                if (uiState.topItems.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.top_items),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(uiState.topItems.take(10)) { item ->
                        TopItemCard(
                            rank = uiState.topItems.indexOf(item) + 1,
                            itemName = item.itemName,
                            quantity = item.quantity,
                            revenue = item.revenue
                        )
                    }
                }

                // Team Performance
                if (uiState.cashierPerformance.isNotEmpty() || uiState.deliveryPerformance.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.team_performance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (uiState.cashierPerformance.isNotEmpty()) {
                        item {
                            TeamPerformanceSection(
                                title = stringResource(Res.string.cashiers),
                                icon = Icons.Default.PointOfSale,
                                performance = uiState.cashierPerformance.map { 
                                    AnalyticsViewModel.PersonPerformance(
                                        name = it.deliveryUserName,
                                        orderCount = it.orderCount,
                                        revenue = it.totalRevenue
                                    )
                                }
                            )
                        }
                    }

                    if (uiState.deliveryPerformance.isNotEmpty()) {
                        item {
                            TeamPerformanceSection(
                                title = stringResource(Res.string.delivery_team),
                                icon = Icons.Default.DeliveryDining,
                                performance = uiState.deliveryPerformance.map { 
                                    AnalyticsViewModel.PersonPerformance(
                                        name = it.deliveryUserName,
                                        orderCount = it.orderCount,
                                        revenue = it.totalRevenue
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
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
            "ALL" to Res.string.all_time
        )

        items(periods) { (period, labelRes) ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Normal
                    )
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
private fun DataCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp),
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
private fun FilterDropdown(
    label: String,
    selectedValue: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = { onExpandedChange(true) }
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedValue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.fillMaxWidth(0.45f)
        ) {
            content()
        }
    }
}

@Composable
private fun RevenueBreakdownCard(
    byChannel: Map<OrderChannel, Double>,
    byPayment: Map<PaymentMethod, Double>
) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // By Channel
            if (byChannel.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(Res.string.by_channel),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    byChannel.forEach { (channel, revenue) ->
                        DataRow(
                            label = when (channel) {
                                OrderChannel.DINE_IN -> stringResource(CoreUiRes.string.channel_dine_in)
                                OrderChannel.DELIVERY -> stringResource(CoreUiRes.string.channel_delivery)
                            },
                            value = CurrencyFormatter.format(revenue),
                            percentage = if (byChannel.values.sum() > 0) 
                                (revenue / byChannel.values.sum() * 100).toInt() 
                            else 0
                        )
                    }
                }
            }

            if (byChannel.isNotEmpty() && byPayment.isNotEmpty()) {
                HorizontalDivider()
            }

            // By Payment Method
            if (byPayment.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Payment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(Res.string.by_payment_method),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    byPayment.forEach { (method, revenue) ->
                        DataRow(
                            label = when (method) {
                                PaymentMethod.CASH -> stringResource(CoreUiRes.string.payment_cash)
                                PaymentMethod.WALLET -> stringResource(CoreUiRes.string.payment_wallet)
                                PaymentMethod.CARD -> stringResource(CoreUiRes.string.payment_card)
                            },
                            value = CurrencyFormatter.format(revenue),
                            percentage = if (byPayment.values.sum() > 0) 
                                (revenue / byPayment.values.sum() * 100).toInt() 
                            else 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String,
    percentage: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun TopItemCard(
    rank: Int,
    itemName: String,
    quantity: Int,
    revenue: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (rank) {
                1 -> Color(0xFFFFD700).copy(alpha = 0.1f)
                2 -> Color(0xFFC0C0C0).copy(alpha = 0.1f)
                3 -> Color(0xFFCD7F32).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when (rank) {
                                1 -> Color(0xFFFFD700)
                                2 -> Color(0xFFC0C0C0)
                                3 -> Color(0xFFCD7F32)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = itemName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$quantity ${stringResource(Res.string.sold)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = CurrencyFormatter.formatDecimal(revenue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TeamPerformanceSection(
    title: String,
    icon: ImageVector,
    performance: List<AnalyticsViewModel.PersonPerformance>
) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            performance.take(5).forEach { person ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${person.orderCount} ${stringResource(Res.string.orders)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatDecimal(person.revenue),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
