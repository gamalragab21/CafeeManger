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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedAnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
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
                    IconButton(onClick = { viewModel.showReportDialog() }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Download Report",
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
    
    // Calculate tax from settlements (real data) - if no tax, it will be 0 (not 14% fallback)
    val totalTax = uiState.settlements?.byPaymentMethod?.values?.sumOf { it.totalTax } ?: 0.0
    
    // Calculate delivery fees correctly from delivery performance data
    // Delivery fees = tax collected from all delivery orders (sum of tax from each delivery person)
    val deliveryFees = uiState.deliveryPerformance.sumOf { it.totalTax }
    
    // Net revenue = Total revenue - Tax
    val netRevenue = totalRevenue - totalTax

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
