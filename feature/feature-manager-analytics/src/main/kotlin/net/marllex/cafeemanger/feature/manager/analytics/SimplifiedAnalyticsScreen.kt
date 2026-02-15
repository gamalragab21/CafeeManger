package net.marllex.cafeemanger.feature.manager.analytics

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import net.marllex.cafeemanger.core.model.OrderChannel
import net.marllex.cafeemanger.core.model.PaymentMethod
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedAnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    onNavigateToExport: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var selectedPeriod by remember { mutableStateOf("TODAY") }

    // Auto-refresh when screen becomes visible - fixes 401 error
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadAnalytics(selectedPeriod)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.analytics),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.business_overview),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToExport) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Export Data",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Period Selector
                item {
                    SimplePeriodSelector(
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = {
                            selectedPeriod = it
                            viewModel.loadAnalytics(it)
                        }
                    )
                }

                // Time Comparison Section - NEW CRITICAL FEATURE
                item {
                    SectionTitle(
                        title = stringResource(R.string.compare_with_past),
                        subtitle = stringResource(R.string.compare_hint),
                        icon = Icons.Default.TrendingUp
                    )
                }

                item {
                    TimeComparisonSection(uiState, selectedPeriod)
                }

                // Main Money Section - What You Earned
                item {
                    SectionTitle(
                        title = stringResource(R.string.money_earned),
                        subtitle = stringResource(R.string.money_earned_hint),
                        icon = Icons.Default.AttachMoney
                    )
                }

                item {
                    MoneySection(uiState)
                }
                
                // Daily Goal Progress - NEW CRITICAL FEATURE
                item {
                    SectionTitle(
                        title = stringResource(R.string.daily_goal),
                        subtitle = stringResource(R.string.daily_goal_hint),
                        icon = Icons.Default.Flag
                    )
                }
                
                item {
                    DailyGoalSection(uiState)
                }

                // Orders Section - How Many Orders
                item {
                    SectionTitle(
                        title = stringResource(R.string.your_orders),
                        subtitle = stringResource(R.string.orders_hint),
                        icon = Icons.Default.Receipt
                    )
                }

                item {
                    OrdersSection(uiState)
                }

                // Peak Hours Analysis - NEW CRITICAL FEATURE
                item {
                    SectionTitle(
                        title = stringResource(R.string.peak_hours),
                        subtitle = stringResource(R.string.peak_hours_hint),
                        icon = Icons.Default.Schedule
                    )
                }
                
                item {
                    PeakHoursSection(uiState)
                }

                // Best Selling Items - What Customers Love
                if (uiState.topItems.isNotEmpty()) {
                    item {
                        SectionTitle(
                            title = stringResource(R.string.best_sellers),
                            subtitle = stringResource(R.string.best_sellers_hint),
                            icon = Icons.Default.Star
                        )
                    }

                    items(uiState.topItems.take(5)) { item ->
                        SimpleItemCard(
                            rank = uiState.topItems.indexOf(item) + 1,
                            name = item.itemName,
                            quantity = item.quantity,
                            revenue = item.revenue
                        )
                    }
                }
                
                // Profit & Loss Statement - NEW CRITICAL FEATURE
                item {
                    SectionTitle(
                        title = stringResource(R.string.profit_loss),
                        subtitle = stringResource(R.string.profit_loss_hint),
                        icon = Icons.Default.AccountBalance
                    )
                }
                
                item {
                    ProfitLossSection(uiState)
                }
                
                // Customer Insights - NEW CRITICAL FEATURE
                item {
                    SectionTitle(
                        title = stringResource(R.string.customer_insights),
                        subtitle = stringResource(R.string.customer_insights_hint),
                        icon = Icons.Default.Group
                    )
                }
                
                item {
                    CustomerInsightsSection(uiState)
                }

                // Team Performance - Your Staff
                if (uiState.cashierPerformance.isNotEmpty() || uiState.deliveryPerformance.isNotEmpty()) {
                    item {
                        SectionTitle(
                            title = stringResource(R.string.your_team),
                            subtitle = stringResource(R.string.team_hint),
                            icon = Icons.Default.People
                        )
                    }

                    if (uiState.cashierPerformance.isNotEmpty()) {
                        item {
                            DetailedTeamSection(
                                title = stringResource(R.string.cashiers),
                                members = uiState.cashierPerformance.take(3)
                            )
                        }
                    }

                    if (uiState.deliveryPerformance.isNotEmpty()) {
                        item {
                            DetailedTeamSection(
                                title = stringResource(R.string.delivery_team),
                                members = uiState.deliveryPerformance.take(3)
                            )
                        }
                    }
                }
            }
        }
        
        // Show report dialog when requested
        if (uiState.showReportDialog) {
            ReportDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.dismissReportDialog() }
            )
        }
        
        // Show export dialog when report is ready
        if (uiState.showExportDialog) {
            ExportDialog(
                viewModel = viewModel,
                report = uiState.generatedReport,
                onDismiss = { viewModel.dismissExportDialog() }
            )
        }
    }
}

@Composable
private fun SimplePeriodSelector(
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.select_time_period),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val periods = listOf(
                    "TODAY" to R.string.today,
                    "WEEK" to R.string.this_week,
                    "MONTH" to R.string.this_month,
                    "ALL" to R.string.all_time
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
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
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
    }
}

@Composable
private fun MoneySection(uiState: AnalyticsViewModel.UiState) {
    // Use REAL data from API
    val totalRevenue = uiState.totalRevenue
    
    // NOTE: totalTax field actually represents delivery fees (not tax)
    // Calculate delivery fees from settlements (real data)
    val totalDeliveryFees = uiState.settlements?.byPaymentMethod?.values?.sumOf { it.totalTax } ?: 0.0
    
    // Calculate delivery fees correctly from delivery performance data
    // Delivery fees = fees collected from all delivery orders (sum from each delivery person)
    val deliveryFees = uiState.deliveryPerformance.sumOf { it.totalTax }
    
    // Net revenue = Total revenue - Delivery Fees
    val netRevenue = totalRevenue - totalDeliveryFees

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Total Money Card - Most Important
        BigMoneyCard(
            label = stringResource(R.string.total_money_made),
            amount = totalRevenue,
            hint = stringResource(R.string.total_money_hint),
            gradient = listOf(Color(0xFF10B981), Color(0xFF059669))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallMoneyCard(
                label = stringResource(R.string.after_tax),
                amount = netRevenue,
                hint = stringResource(R.string.after_tax_hint),
                color = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
//            SmallMoneyCard(
//                label = stringResource(R.string.tax_paid),
//                amount = totalTax,
//                hint = if (totalTax > 0) "14%" else stringResource(R.string.no_tax_yet),
//                color = Color(0xFFF59E0B),
//                modifier = Modifier.weight(1f)
//            )
        }

        // Always show delivery fees (even if 0)
        SmallMoneyCard(
            label = stringResource(R.string.delivery_fees_collected),
            amount = deliveryFees,
            hint = if (deliveryFees > 0) stringResource(R.string.delivery_fees_hint) else stringResource(R.string.no_delivery_fees),
            color = Color(0xFF8B5CF6),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BigMoneyCard(
    label: String,
    amount: Double,
    hint: String,
    gradient: List<Color>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = String.format("%.2f EGP", amount),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = gradient[0],
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = gradient[0].copy(alpha = 0.15f)
                ) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = gradient[0],
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallMoneyCard(
    label: String,
    amount: Double,
    hint: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format("%.2f", amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = "EGP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OrdersSection(uiState: AnalyticsViewModel.UiState) {
    val totalOrders = uiState.totalOrders
    
    // Use REAL data from API - ordersByChannel
    val dineInOrders = uiState.summary?.ordersByChannel?.get("DINE_IN") 
        ?: uiState.summary?.ordersByChannel?.get("DINEIN") 
        ?: 0
    val deliveryOrders = uiState.summary?.ordersByChannel?.get("DELIVERY") ?: 0
    val takeawayOrders = uiState.summary?.ordersByChannel?.get("TAKEAWAY") ?: 0

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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Total Orders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.total_orders_count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = totalOrders.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
            }

            HorizontalDivider()

            // Order Types
            Text(
                text = stringResource(R.string.order_types),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (dineInOrders > 0) {
                SimpleOrderTypeRow(
                    icon = Icons.Default.Restaurant,
                    label = stringResource(R.string.dine_in_orders),
                    count = dineInOrders,
                    color = Color(0xFF6366F1)
                )
            }

            if (deliveryOrders > 0) {
                SimpleOrderTypeRow(
                    icon = Icons.Default.DeliveryDining,
                    label = stringResource(R.string.delivery_orders),
                    count = deliveryOrders,
                    color = Color(0xFF10B981)
                )
            }

            if (takeawayOrders > 0) {
                SimpleOrderTypeRow(
                    icon = Icons.Default.ShoppingBag,
                    label = stringResource(R.string.takeaway_orders),
                    count = takeawayOrders,
                    color = Color(0xFFF59E0B)
                )
            }
        }
    }
}

@Composable
private fun SimpleOrderTypeRow(
    icon: ImageVector,
    label: String,
    count: Int,
    color: Color
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

@Composable
private fun SimpleItemCard(
    rank: Int,
    name: String,
    quantity: Int,
    revenue: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (rank) {
                1 -> Color(0xFFFFD700).copy(alpha = 0.15f)
                2 -> Color(0xFFC0C0C0).copy(alpha = 0.15f)
                3 -> Color(0xFFCD7F32).copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$quantity ${stringResource(R.string.sold)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.0f", revenue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "EGP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class TeamMember(val name: String, val orders: Int, val revenue: Double)

@Composable
private fun DetailedTeamSection(
    title: String,
    members: List<net.marllex.cafeemanger.core.model.DeliveryPerformance>
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            members.forEach { member ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.deliveryUserName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${member.orderCount} ${stringResource(R.string.orders)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format("%.0f EGP", member.totalRevenue),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // Show delivery fees (tax) for each person
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (member.totalTax > 0) 
                                    Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = stringResource(R.string.delivery_fees_label, String.format("%.2f", member.totalTax)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (member.totalTax > 0) 
                                        Color(0xFF8B5CF6)
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                if (member != members.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SimpleTeamSection(
    title: String,
    members: List<TeamMember>
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            members.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${member.orders} ${stringResource(R.string.orders)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = String.format("%.0f EGP", member.revenue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (member != members.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}


// NEW CRITICAL FEATURES

@Composable
private fun TimeComparisonSection(
    uiState: AnalyticsViewModel.UiState,
    selectedPeriod: String
) {
    val currentRevenue = uiState.totalRevenue
    val currentOrders = uiState.totalOrders
    
    // Calculate comparison based on daily data
    val dailyData = uiState.dailyData.sortedBy { it.date } // Sort by date to ensure correct order
    
    // Get comparison data based on period
    val (comparisonRevenue, comparisonOrders, comparisonLabel) = when (selectedPeriod) {
        "TODAY" -> {
            // Compare TODAY with YESTERDAY
            // Current = today's data (last item)
            // Previous = yesterday's data (second to last item)
            val yesterday = dailyData.getOrNull(dailyData.size - 2)
            Triple(
                yesterday?.revenue ?: 0.0,
                yesterday?.orders ?: 0,
                stringResource(R.string.vs_yesterday)
            )
        }
        "WEEK" -> {
            // Compare THIS WEEK (last 7 days) with PREVIOUS WEEK (days 8-14 ago)
            // Example with 14 days: [day1, day2, ..., day13, day14]
            // Current week = last 7 days = [day8, day9, ..., day14]
            // Previous week = days 8-14 ago = [day1, day2, ..., day7]
            if (dailyData.size >= 14) {
                // Get days 8-14 ago (previous week)
                val previousWeekData = dailyData.dropLast(7).takeLast(7)
                val previousWeekRevenue = previousWeekData.sumOf { it.revenue }
                val previousWeekOrders = previousWeekData.sumOf { it.orders }
                Triple(
                    previousWeekRevenue,
                    previousWeekOrders,
                    stringResource(R.string.vs_last_week)
                )
            } else {
                // Not enough data for comparison
                Triple(0.0, 0, stringResource(R.string.no_comparison))
            }
        }
        "MONTH" -> {
            // Compare THIS MONTH (last 30 days) with PREVIOUS MONTH (days 31-60 ago)
            // Example with 60 days: [day1, day2, ..., day59, day60]
            // Current month = last 30 days = [day31, day32, ..., day60]
            // Previous month = days 31-60 ago = [day1, day2, ..., day30]
            if (dailyData.size >= 60) {
                // Get days 31-60 ago (previous month)
                val previousMonthData = dailyData.dropLast(30).takeLast(30)
                val previousMonthRevenue = previousMonthData.sumOf { it.revenue }
                val previousMonthOrders = previousMonthData.sumOf { it.orders }
                Triple(
                    previousMonthRevenue,
                    previousMonthOrders,
                    stringResource(R.string.vs_last_month)
                )
            } else {
                // Not enough data for comparison
                Triple(0.0, 0, stringResource(R.string.no_comparison))
            }
        }
        else -> Triple(0.0, 0, stringResource(R.string.no_comparison))
    }
    
    val revenueChange = if (comparisonRevenue > 0) {
        ((currentRevenue - comparisonRevenue) / comparisonRevenue) * 100
    } else 0.0
    
    val ordersChange = if (comparisonOrders > 0) {
        ((currentOrders - comparisonOrders).toDouble() / comparisonOrders) * 100
    } else 0.0
    
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = comparisonLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ComparisonCard(
                    label = stringResource(R.string.revenue),
                    currentValue = String.format("%.0f", currentRevenue),
                    previousValue = String.format("%.0f", comparisonRevenue),
                    changePercent = revenueChange,
                    modifier = Modifier.weight(1f)
                )
                
                ComparisonCard(
                    label = stringResource(R.string.orders),
                    currentValue = currentOrders.toString(),
                    previousValue = comparisonOrders.toString(),
                    changePercent = ordersChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ComparisonCard(
    label: String,
    currentValue: String,
    previousValue: String,
    changePercent: Double,
    modifier: Modifier = Modifier
) {
    val isPositive = changePercent >= 0
    val color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = currentValue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = String.format("%.1f%%", kotlin.math.abs(changePercent)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Text(
                text = stringResource(R.string.was_value, previousValue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DailyGoalSection(uiState: AnalyticsViewModel.UiState) {
    // Set a daily goal (can be made configurable later)
    val dailyGoal = 5000.0 // 5000 EGP daily goal
    
    // IMPORTANT: For daily goal, we should use TODAY's revenue only, not total
    // Get today's revenue from daily data (last item in sorted list)
    val dailyData = uiState.dailyData.sortedBy { it.date }
    val todayRevenue = dailyData.lastOrNull()?.revenue ?: uiState.totalRevenue
    
    val progress = (todayRevenue / dailyGoal).coerceIn(0.0, 1.0).toFloat()
    val progressPercent = (progress * 100).toInt()
    
    val goalColor = when {
        progress >= 1.0 -> Color(0xFF10B981) // Green - Goal achieved!
        progress >= 0.75 -> Color(0xFF3B82F6) // Blue - Almost there
        progress >= 0.5 -> Color(0xFFF59E0B) // Orange - Halfway
        else -> Color(0xFFEF4444) // Red - Need more
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = goalColor.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.daily_target),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%.0f / %.0f EGP", todayRevenue, dailyGoal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(goalColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = goalColor
                    )
                }
            }
            
            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = goalColor,
                    trackColor = goalColor.copy(alpha = 0.2f)
                )
                
                Text(
                    text = when {
                        progress >= 1.0 -> stringResource(R.string.goal_achieved)
                        progress >= 0.75 -> stringResource(R.string.almost_there)
                        progress >= 0.5 -> stringResource(R.string.halfway_there)
                        else -> stringResource(R.string.keep_going)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = goalColor
                )
            }
        }
    }
}

@Composable
private fun PeakHoursSection(uiState: AnalyticsViewModel.UiState) {
    // Analyze daily data to find peak hours (simplified - using order distribution)
    // IMPORTANT: For peak hours, use TODAY's orders only (from daily data)
    val dailyData = uiState.dailyData.sortedBy { it.date }
    val todayOrders = dailyData.lastOrNull()?.orders ?: uiState.totalOrders
    
    // Simulate peak hours based on typical restaurant patterns
    // In real implementation, this would come from API with hourly data
    val peakHours = listOf(
        PeakHour("12:00 - 14:00", stringResource(R.string.lunch_time), (todayOrders * 0.35).toInt(), Color(0xFFEF4444)),
        PeakHour("19:00 - 21:00", stringResource(R.string.dinner_time), (todayOrders * 0.30).toInt(), Color(0xFFF59E0B)),
        PeakHour("16:00 - 18:00", stringResource(R.string.afternoon), (todayOrders * 0.20).toInt(), Color(0xFF3B82F6)),
        PeakHour("09:00 - 11:00", stringResource(R.string.morning), (todayOrders * 0.15).toInt(), Color(0xFF10B981))
    )
    
    val maxOrders = peakHours.maxOfOrNull { it.orders } ?: 1
    
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.busiest_times),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            peakHours.forEach { hour ->
                PeakHourBar(
                    hour = hour,
                    maxOrders = maxOrders
                )
            }
        }
    }
}

data class PeakHour(
    val timeRange: String,
    val label: String,
    val orders: Int,
    val color: Color
)

@Composable
private fun PeakHourBar(
    hour: PeakHour,
    maxOrders: Int
) {
    val progress = if (maxOrders > 0) (hour.orders.toFloat() / maxOrders) else 0f
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = hour.timeRange,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = hour.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = hour.color.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${hour.orders} ${stringResource(R.string.orders)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = hour.color,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = hour.color,
            trackColor = hour.color.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun ProfitLossSection(uiState: AnalyticsViewModel.UiState) {
    val totalRevenue = uiState.totalRevenue
    // NOTE: totalTax field actually represents delivery fees (not tax)
    // The "tax" field in settings is used for delivery fees
    val totalDeliveryFees = uiState.settlements?.byPaymentMethod?.values?.sumOf { it.totalTax } ?: 0.0
    
    // CRITICAL FIX: Filter salary payments to match the selected period
    // The fromDate and toDate in uiState should match the selected period
    val fromDate = uiState.fromDate ?: (System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
    val toDate = uiState.toDate ?: System.currentTimeMillis()
    
    // Filter salaries paid within the selected period
    val periodSalaries = uiState.salaryPayments.filter { payment ->
        val paidAt = payment.paidAt
        paidAt != null && paidAt >= fromDate && paidAt <= toDate
    }
    val totalSalaries = periodSalaries.sumOf { it.amount }
    
    // Calculate costs (salaries + delivery fees)
    val totalCosts = totalSalaries + totalDeliveryFees
    val netProfit = totalRevenue - totalCosts
    val profitMargin = if (totalRevenue > 0) (netProfit / totalRevenue) * 100 else 0.0
    
    val profitColor = if (netProfit >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
    
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Income Section
            Text(
                text = stringResource(R.string.income),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981)
            )
            
            ProfitLossRow(
                label = stringResource(R.string.total_revenue),
                amount = totalRevenue,
                color = Color(0xFF10B981)
            )
            
            HorizontalDivider()
            
            // Expenses Section
            Text(
                text = stringResource(R.string.expenses),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444)
            )
            
            ProfitLossRow(
                label = stringResource(R.string.salaries_paid),
                amount = totalSalaries,
                color = Color(0xFFEF4444),
                isExpense = true
            )
            
            ProfitLossRow(
                label = stringResource(R.string.tax_paid),
                amount = totalDeliveryFees,
                color = Color(0xFFEF4444),
                isExpense = true
            )
            
            HorizontalDivider()
            
            // Net Profit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.net_profit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = stringResource(R.string.profit_margin_value, String.format("%.1f", profitMargin)),
                        style = MaterialTheme.typography.bodySmall,
                        color = profitColor
                    )
                }
                
                Text(
                    text = String.format("%.2f EGP", netProfit),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = profitColor
                )
            }
        }
    }
}

@Composable
private fun ProfitLossRow(
    label: String,
    amount: Double,
    color: Color,
    isExpense: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "${if (isExpense) "-" else "+"} ${String.format("%.2f", amount)} EGP",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun CustomerInsightsSection(uiState: AnalyticsViewModel.UiState) {
    val totalOrders = uiState.totalOrders
    val totalRevenue = uiState.totalRevenue
    val avgOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0
    
    // Simulate customer data (in real implementation, this would come from API)
    val newCustomers = (totalOrders * 0.35).toInt()
    val returningCustomers = (totalOrders * 0.65).toInt()
    
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Average Order Value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.avg_order_value),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.per_order_average),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = String.format("%.2f EGP", avgOrderValue),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF3B82F6)
                )
            }
            
            HorizontalDivider()
            
            // Customer Types
            Text(
                text = stringResource(R.string.customer_types),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CustomerTypeCard(
                    label = stringResource(R.string.new_customers),
                    count = newCustomers,
                    percentage = 35,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                
                CustomerTypeCard(
                    label = stringResource(R.string.returning_customers),
                    count = returningCustomers,
                    percentage = 65,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Loyalty indicator
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.loyalty_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerTypeCard(
    label: String,
    count: Int,
    percentage: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ReportDialog(
    viewModel: AnalyticsViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.generate_report),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.select_report_period),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportPeriodOption(
                        selected = uiState.reportPeriod == AnalyticsViewModel.ReportPeriod.DAILY,
                        onClick = { viewModel.setReportPeriod(AnalyticsViewModel.ReportPeriod.DAILY) },
                        title = stringResource(R.string.daily_report),
                        subtitle = stringResource(R.string.today)
                    )
                    ReportPeriodOption(
                        selected = uiState.reportPeriod == AnalyticsViewModel.ReportPeriod.WEEKLY,
                        onClick = { viewModel.setReportPeriod(AnalyticsViewModel.ReportPeriod.WEEKLY) },
                        title = stringResource(R.string.weekly_report),
                        subtitle = stringResource(R.string.last_7_days)
                    )
                    ReportPeriodOption(
                        selected = uiState.reportPeriod == AnalyticsViewModel.ReportPeriod.MONTHLY,
                        onClick = { viewModel.setReportPeriod(AnalyticsViewModel.ReportPeriod.MONTHLY) },
                        title = stringResource(R.string.monthly_report),
                        subtitle = stringResource(R.string.last_30_days)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.generateReport() },
                enabled = !uiState.generatingReport
            ) {
                if (uiState.generatingReport) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.generatingReport) stringResource(R.string.generating) else stringResource(R.string.generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ReportPeriodOption(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun ExportDialog(
    viewModel: AnalyticsViewModel,
    report: AnalyticsViewModel.ComprehensiveReport?,
    onDismiss: () -> Unit
) {
    if (report == null) {
        onDismiss()
        return
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981)
                )
                Text(
                    text = stringResource(R.string.report_ready),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.report_generated_successfully),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportSummaryRow(
                            label = stringResource(R.string.total_revenue),
                            value = String.format("%.2f EGP", report.summary.totalRevenue)
                        )
                        ReportSummaryRow(
                            label = stringResource(R.string.total_orders),
                            value = report.summary.totalOrders.toString()
                        )
                        ReportSummaryRow(
                            label = stringResource(R.string.net_profit),
                            value = String.format("%.2f EGP", report.netProfit)
                        )
                    }
                }
                
                Text(
                    text = stringResource(R.string.export_options),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.exportReportAsPDF() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.export_as_pdf))
                }
                OutlinedButton(
                    onClick = { viewModel.shareReport() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share_report))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ReportSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
