package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.network.*
import net.marllex.waselak.admin.util.formatDecimal
import net.marllex.waselak.admin.util.formatPercent
import net.marllex.waselak.admin.viewmodel.VendorDetailViewModel

// ══════════════════════════════════════════════════════════════════════
// Analytics Tab Composables for VendorDetailScreen
// ══════════════════════════════════════════════════════════════════════

// ── 1. Revenue & Orders Tab ─────────────────────────────────────────

@Composable
fun RevenueOrdersTab(viewModel: VendorDetailViewModel) {
    val summary by viewModel.executiveSummary.collectAsState()
    val revenue by viewModel.revenueProfit.collectAsState()
    val orders by viewModel.ordersIntelligence.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Executive Summary Cards
        summary?.let { s ->
            item {
                Text("Executive Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        title = "Revenue",
                        value = formatDecimal(s.current.total_revenue, 2),
                        change = s.revenue_change_percent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Orders",
                        value = s.current.total_orders.toString(),
                        change = s.orders_change_percent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "AOV",
                        value = formatDecimal(s.current.average_order_value, 2),
                        change = s.aov_change_percent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard("Active Orders", s.active_orders.toString(), Modifier.weight(1f))
                    SmallInfoCard("Net Revenue", formatDecimal(s.current.net_revenue, 2), Modifier.weight(1f))
                    SmallInfoCard("Attendance", s.attendance_today.toString(), Modifier.weight(1f))
                }
            }
        }

        // Revenue breakdown
        revenue?.let { r ->
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text("Revenue Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRowSimple("Gross Revenue", formatDecimal(r.gross_revenue, 2))
                        InfoRowSimple("Delivery Fees", formatDecimal(r.total_delivery_fees, 2))
                        InfoRowSimple("Net Revenue", formatDecimal(r.net_revenue, 2))
                        Divider()
                        Text("Payment Methods", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        r.payment_methods.forEach { pm ->
                            InfoRowSimple("${pm.method} (${pm.order_count})", formatDecimal(pm.revenue, 2))
                        }
                    }
                }
            }
        }

        // Orders Intelligence
        orders?.let { o ->
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text("Orders Intelligence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard("Total", o.total_orders.toString(), Modifier.weight(1f))
                    SmallInfoCard("Completed", o.completed_orders.toString(), Modifier.weight(1f))
                    SmallInfoCard("Cancelled", o.cancelled_orders.toString(), Modifier.weight(1f))
                    SmallInfoCard("Refunded", o.refunded_orders.toString(), Modifier.weight(1f))
                }
            }
            if (o.channel_breakdown.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Channel Breakdown", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            o.channel_breakdown.forEach { ch ->
                                InfoRowSimple("${ch.channel} (${ch.count})", formatPercent(ch.percent, 1))
                            }
                        }
                    }
                }
            }
        }

        if (summary == null && revenue == null && orders == null) {
            item { EmptyTabContent("Revenue & Orders") }
        }
    }
}

// ── 2. Peak Times Tab ───────────────────────────────────────────────

@Composable
fun PeakTimesTab(viewModel: VendorDetailViewModel) {
    val peakTimes by viewModel.peakTimes.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        peakTimes?.let { pt ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard("Busiest Hour", "${pt.busiest_hour}:00", Modifier.weight(1f))
                    SmallInfoCard("Busiest Day", pt.busiest_day, Modifier.weight(1f))
                }
            }
            item {
                Text("Hourly Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        pt.hourly_data.filter { it.order_count > 0 }.forEach { h ->
                            InfoRowSimple("${h.hour}:00 - ${h.hour + 1}:00", "${h.order_count} orders (${formatDecimal(h.revenue, 2)})")
                        }
                        if (pt.hourly_data.all { it.order_count == 0 }) {
                            Text("No orders in this period", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text("Day of Week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pt.day_of_week.forEach { d ->
                            InfoRowSimple("${d.name} (${d.order_count} orders)", formatDecimal(d.revenue, 2))
                        }
                    }
                }
            }
        }

        if (peakTimes == null) {
            item { EmptyTabContent("Peak Times") }
        }
    }
}

// ── 3. Staff Performance Tab ────────────────────────────────────────

@Composable
fun StaffPerformanceTab(viewModel: VendorDetailViewModel) {
    val cashiers by viewModel.cashierPerformance.collectAsState()
    val drivers by viewModel.deliveryPerformance.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Cashier Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (cashiers.isEmpty()) {
            item { Text("No cashier data available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(cashiers) { c ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.cashier_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    InfoRowSimple("Orders", c.order_count.toString())
                    InfoRowSimple("Revenue", formatDecimal(c.revenue, 2))
                    InfoRowSimple("AOV", formatDecimal(c.average_order_value, 2))
                    InfoRowSimple("Cancelled", "${c.cancelled_orders} (${formatPercent(c.cancellation_rate, 1)})")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text("Delivery Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (drivers.isEmpty()) {
            item { Text("No delivery data available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(drivers) { d ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.driver_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    InfoRowSimple("Completed Orders", d.orders_completed.toString())
                    InfoRowSimple("Fees Collected", formatDecimal(d.fees_collected, 2))
                    InfoRowSimple("Revenue", formatDecimal(d.revenue, 2))
                }
            }
        }
    }
}

// ── 4. Products Tab ─────────────────────────────────────────────────

@Composable
fun ProductsTab(viewModel: VendorDetailViewModel) {
    val products by viewModel.productIntelligence.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        products?.let { p ->
            if (p.top_selling.isNotEmpty()) {
                item { Text("Top Selling", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(p.top_selling.take(10)) { item -> ProductRow(item) }
            }
            if (p.revenue_by_category.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Revenue by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(p.revenue_by_category) { cat ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${cat.category_name} (${cat.item_count} items)", style = MaterialTheme.typography.bodyMedium)
                            Text(formatDecimal(cat.revenue, 2), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (p.low_margin_warnings.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Low Margin Warnings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                items(p.low_margin_warnings.take(5)) { item -> ProductRow(item) }
            }
        }

        if (products == null) {
            item { EmptyTabContent("Products") }
        }
    }
}

// ── 5. Customers Tab ────────────────────────────────────────────────

@Composable
fun CustomersTab(viewModel: VendorDetailViewModel) {
    val intelligence by viewModel.customerIntelligence.collectAsState()
    val customerList by viewModel.customers.collectAsState()

    // We get vendorId from context (passed through parent tab already loading)
    // For customer search/sort we use the customerList data

    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("total_spent") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Intelligence summary cards
        intelligence?.let { ci ->
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard("Total", ci.total_customers.toString(), Modifier.weight(1f))
                    SmallInfoCard("New", formatPercent(ci.new_customers_percent, 0), Modifier.weight(1f))
                    SmallInfoCard("Returning", formatPercent(ci.returning_customers_percent, 0), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard("Avg Spend", formatDecimal(ci.average_spend, 2), Modifier.weight(1f))
                    SmallInfoCard("LTV", formatDecimal(ci.lifetime_value, 2), Modifier.weight(1f))
                }
            }
        }

        // Sort chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("total_spent" to "By Spend", "order_count" to "By Orders", "name" to "By Name").forEach { (sort, label) ->
                FilterChip(
                    selected = sortBy == sort,
                    onClick = { sortBy = sort },
                    label = { Text(label) }
                )
            }
        }

        // Customer list from CMS endpoint
        customerList?.let { data ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("${data.total} customers (Page ${data.page}/${data.total_pages})", style = MaterialTheme.typography.labelLarge)
                }
                items(data.customers) { customer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(customer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (customer.points_balance > 0) {
                                    Surface(
                                        color = Color(0xFFFFF3E0),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "${customer.points_balance} pts",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFF57C00)
                                        )
                                    }
                                }
                            }
                            InfoRowSimple("Phone", customer.phone)
                            InfoRowSimple("Orders", customer.order_count.toString())
                            InfoRowSimple("Total Spent", "${formatDecimal(customer.total_spent, 2)} EGP")
                        }
                    }
                }

                // Pagination
                if (data.page < data.total_pages) {
                    item {
                        // We need vendorId here — getting it from the intelligence
                        // Note: loadMoreCustomers is called from the tab, which doesn't have direct vendorId access
                        // The parent composable passes vendorId context
                    }
                }
            }
        }

        // Top customers from intelligence (if CMS list not loaded yet)
        if (customerList == null) {
            intelligence?.let { ci ->
                if (ci.top_customers.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Text("Top Customers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        items(ci.top_customers) { c ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(c.customer_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    InfoRowSimple("Phone", c.phone)
                                    InfoRowSimple("Orders", c.order_count.toString())
                                    InfoRowSimple("Total Spent", formatDecimal(c.total_spent, 2))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (intelligence == null && customerList == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

// ── 6. Stock Tab ────────────────────────────────────────────────────

@Composable
fun StockTab(viewModel: VendorDetailViewModel) {
    val stock by viewModel.stockOverview.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stock?.let { s ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard("Items", s.total_items.toString(), Modifier.weight(1f))
                    SmallInfoCard("Stock Value", formatDecimal(s.total_stock_value, 2), Modifier.weight(1f))
                    SmallInfoCard("Profit", formatDecimal(s.potential_profit, 2), Modifier.weight(1f))
                }
            }

            if (s.out_of_stock_items.isNotEmpty()) {
                item { Text("Out of Stock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                items(s.out_of_stock_items) { item -> StockItemRow(item) }
            }

            if (s.low_stock_items.isNotEmpty()) {
                item { Text("Low Stock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF57C00)) }
                items(s.low_stock_items) { item -> StockItemRow(item) }
            }

            if (s.dead_stock_items.isNotEmpty()) {
                item { Text("Dead Stock (no movement 30d)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(s.dead_stock_items.take(10)) { item -> StockItemRow(item) }
            }
        }

        if (stock == null) {
            item { EmptyTabContent("Stock") }
        }
    }
}

// ── 7. Offers & Discounts Tab ───────────────────────────────────────

@Composable
fun OffersDiscountsTab(viewModel: VendorDetailViewModel) {
    val offers by viewModel.offersAnalytics.collectAsState()
    val discounts by viewModel.discountAnalytics.collectAsState()
    val loyalty by viewModel.loyaltyAnalytics.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Offers section
        offers?.let { o ->
            item { Text("Offers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard("Total", o.total_offers.toString(), Modifier.weight(1f))
                    SmallInfoCard("Active", o.active_offers.toString(), Modifier.weight(1f))
                    SmallInfoCard("Uses", o.total_offer_uses.toString(), Modifier.weight(1f))
                }
            }
            if (o.top_offers.isNotEmpty()) {
                item { Text("Top Offers", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                items(o.top_offers.take(5)) { offer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(offer.offer_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (offer.is_active) {
                                    Text("Active", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            InfoRowSimple("Uses", offer.usage_count.toString())
                            InfoRowSimple("Discount Given", formatDecimal(offer.total_discount_given, 2))
                            InfoRowSimple("Revenue", formatDecimal(offer.total_revenue_from_offer_orders, 2))
                        }
                    }
                }
            }
        }

        // Discount section
        discounts?.let { d ->
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Discount Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoRowSimple("Orders with Discount", d.total_orders_with_discount.toString())
                        InfoRowSimple("Total Discount", formatDecimal(d.total_discount_given, 2))
                        InfoRowSimple("Avg per Order", formatDecimal(d.average_discount_per_order, 2))
                        InfoRowSimple("Discount Rate", formatPercent(d.discount_rate, 1))
                        if (d.breakdown.isNotEmpty()) {
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            d.breakdown.forEach { b ->
                                InfoRowSimple("${b.type} (${b.count})", "${formatDecimal(b.total_amount, 2)} (${formatPercent(b.percent_of_total, 0)})")
                            }
                        }
                    }
                }
            }
        }

        // Loyalty section
        loyalty?.let { l ->
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Loyalty Program", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoRowSimple("Points Earned", l.total_points_earned.toString())
                        InfoRowSimple("Points Redeemed", l.total_points_redeemed.toString())
                        InfoRowSimple("Outstanding", l.total_points_outstanding.toString())
                        InfoRowSimple("Active Customers", l.active_loyalty_customers.toString())
                        InfoRowSimple("Redemption Rate", formatPercent(l.redemption_rate, 1))
                        InfoRowSimple("Points Revenue", formatDecimal(l.points_to_revenue, 2))
                    }
                }
            }
        }

        if (offers == null && discounts == null && loyalty == null) {
            item { EmptyTabContent("Offers & Discounts") }
        }
    }
}

// ── 8. Alerts Tab ───────────────────────────────────────────────────

@Composable
fun AlertsTab(viewModel: VendorDetailViewModel) {
    val alertsData by viewModel.alerts.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        alertsData?.let { data ->
            if (data.alerts.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                            Text("No active alerts", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            items(data.alerts) { alert ->
                val bgColor = when (alert.severity) {
                    "CRITICAL" -> MaterialTheme.colorScheme.errorContainer
                    "WARNING" -> Color(0xFFFFF3E0)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val iconTint = when (alert.severity) {
                    "CRITICAL" -> MaterialTheme.colorScheme.error
                    "WARNING" -> Color(0xFFF57C00)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (alert.severity == "CRITICAL") Icons.Default.Error else Icons.Default.Warning,
                            contentDescription = null,
                            tint = iconTint
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(alert.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                            Text("${alert.severity} | ${alert.type}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (alertsData == null) {
            item { EmptyTabContent("Alerts") }
        }
    }
}

// ── 9. Orders List Tab ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersListTab(vendorId: String, viewModel: VendorDetailViewModel) {
    val ordersData by viewModel.orders.collectAsState()
    val orderDetail by viewModel.orderDetail.collectAsState()
    val orderDetailLoading by viewModel.orderDetailLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var showOrderDetail by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filters
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name/phone") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.loadMoreOrders(vendorId, 1, selectedStatus, search = null)
                        }) { Icon(Icons.Default.Clear, "Clear") }
                    }
                }
            )
            Button(onClick = {
                viewModel.loadMoreOrders(vendorId, 1, selectedStatus, search = searchQuery.ifBlank { null })
            }) { Text("Search") }
        }

        // Status filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(null to "All", "COMPLETED" to "Done", "CREATED" to "New", "CANCELED" to "Cancel", "REFUNDED" to "Refund").forEach { (status, label) ->
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = {
                        selectedStatus = status
                        viewModel.loadMoreOrders(vendorId, 1, status, search = searchQuery.ifBlank { null })
                    },
                    label = { Text(label) }
                )
            }
        }

        // Orders list
        ordersData?.let { data ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("${data.total} orders (Page ${data.page}/${data.total_pages})", style = MaterialTheme.typography.labelLarge)
                }
                items(data.orders) { order ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.loadOrderDetail(vendorId, order.id)
                            showOrderDetail = true
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(order.client_name.ifBlank { "Guest" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                StatusBadge(order.status)
                            }
                            InfoRowSimple("Channel", order.channel)
                            InfoRowSimple("Total", "${formatDecimal(order.total, 2)} EGP")
                            InfoRowSimple("Payment", "${order.payment_method} (${order.payment_status})")
                            if (order.client_phone.isNotBlank()) InfoRowSimple("Phone", order.client_phone)
                        }
                    }
                }

                // Pagination
                if (data.page < data.total_pages) {
                    item {
                        Button(
                            onClick = { viewModel.loadMoreOrders(vendorId, data.page + 1, selectedStatus, search = searchQuery.ifBlank { null }) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Load More") }
                    }
                }
            }
        }

        if (ordersData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    // Order detail dialog
    if (showOrderDetail) {
        AlertDialog(
            onDismissRequest = {
                showOrderDetail = false
                viewModel.clearOrderDetail()
            },
            title = { Text("Order Details") },
            text = {
                if (orderDetailLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    orderDetail?.let { detail ->
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Status + Channel
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusBadge(detail.status)
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        detail.channel,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            HorizontalDivider()

                            // Customer info
                            if (detail.client_name.isNotBlank()) InfoRowSimple("Customer", detail.client_name)
                            if (detail.client_phone.isNotBlank()) InfoRowSimple("Phone", detail.client_phone)
                            detail.client_address?.let { InfoRowSimple("Address", it) }

                            HorizontalDivider()

                            // Price breakdown
                            InfoRowSimple("Subtotal", "${formatDecimal(detail.subtotal, 2)} EGP")
                            if (detail.delivery_fee > 0) InfoRowSimple("Delivery Fee", "${formatDecimal(detail.delivery_fee, 2)} EGP")
                            if (detail.discount > 0) InfoRowSimple("Discount", "-${formatDecimal(detail.discount, 2)} EGP")
                            if (detail.tax > 0) InfoRowSimple("Tax", "${formatDecimal(detail.tax, 2)} EGP")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${formatDecimal(detail.total, 2)} EGP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            InfoRowSimple("Payment", "${detail.payment_method} (${detail.payment_status})")

                            // Refund info
                            if (detail.refunded_at != null || detail.refund_reason != null) {
                                HorizontalDivider()
                                Text("Refund Info", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                detail.refund_reason?.let { InfoRowSimple("Reason", it) }
                            }

                            // Points info
                            if (detail.points_earned > 0 || detail.points_redeemed > 0) {
                                HorizontalDivider()
                                if (detail.points_earned > 0) InfoRowSimple("Points Earned", detail.points_earned.toString())
                                if (detail.points_redeemed > 0) InfoRowSimple("Points Redeemed", detail.points_redeemed.toString())
                            }

                            // Order items
                            if (detail.items.isNotEmpty()) {
                                HorizontalDivider()
                                Text("Items (${detail.items.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                detail.items.forEach { item ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.item_name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                                item.note?.let { note ->
                                                    if (note.isNotBlank()) {
                                                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                item.variant_options?.let { vars ->
                                                    if (vars.isNotBlank()) {
                                                        Text(vars, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                                    }
                                                }
                                            }
                                            Text("${item.quantity} x ${formatDecimal(item.item_price, 2)}", style = MaterialTheme.typography.bodySmall)
                                            Text(formatDecimal(item.item_price * item.quantity, 2), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } ?: Text("Failed to load order details")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showOrderDetail = false
                    viewModel.clearOrderDetail()
                }) { Text("Close") }
            }
        )
    }
}

// ── 10. Workers Tab ─────────────────────────────────────────────────

@Composable
fun WorkersTab(viewModel: VendorDetailViewModel) {
    val workersData by viewModel.workers.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        workersData?.let { data ->
            item { Text("${data.total} Workers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(data.workers) { worker ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(worker.full_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (worker.checked_in_today) {
                                Text("Present", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        InfoRowSimple("ID", worker.worker_id)
                        InfoRowSimple("Role", worker.role)
                        InfoRowSimple("Salary", "${worker.salary_type}: ${formatDecimal(worker.salary_amount, 2)}")
                        InfoRowSimple("Attendance (30d)", "${worker.attendance_days_30d} days")
                        val hours = worker.worked_minutes_30d / 60
                        val mins = worker.worked_minutes_30d % 60
                        InfoRowSimple("Hours (30d)", "${hours}h ${mins}m")
                        if (!worker.active) {
                            Text("INACTIVE", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (workersData == null) {
            item { EmptyTabContent("Workers") }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Shared UI Components
// ══════════════════════════════════════════════════════════════════════

@Composable
fun MetricCard(title: String, value: String, change: Double, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val changeColor = if (change >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            val arrow = if (change >= 0) "\u2191" else "\u2193"
            Text("$arrow ${formatPercent(kotlin.math.abs(change), 1)}", style = MaterialTheme.typography.labelSmall, color = changeColor)
        }
    }
}

@Composable
fun SmallInfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoRowSimple(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "COMPLETED" -> Color(0xFF4CAF50)
        "CREATED", "PREPARING" -> Color(0xFF2196F3)
        "CANCELED", "CANCELLED" -> MaterialTheme.colorScheme.error
        "REFUNDED" -> Color(0xFFF57C00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ProductRow(item: ProductItemDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.item_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(item.category_name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InfoRowSimple("Qty Sold", item.quantity_sold.toString())
            InfoRowSimple("Revenue", formatDecimal(item.revenue, 2))
            InfoRowSimple("Margin", formatPercent(item.profit_margin, 1))
        }
    }
}

@Composable
fun StockItemRow(item: StockOverviewItemDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(item.item_name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${item.quantity} ${item.unit} (min: ${item.min_quantity})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(item.status.replace("_", " "))
        }
    }
}

@Composable
fun EmptyTabContent(tabName: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Loading $tabName data...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
