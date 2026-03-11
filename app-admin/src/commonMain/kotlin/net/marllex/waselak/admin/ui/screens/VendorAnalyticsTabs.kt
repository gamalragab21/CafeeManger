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
import org.jetbrains.compose.resources.stringResource
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

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
                Text(stringResource(Res.string.executive_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        title = stringResource(Res.string.revenue),
                        value = formatDecimal(s.current.total_revenue, 2),
                        change = s.revenue_change_percent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = stringResource(Res.string.orders),
                        value = s.current.total_orders.toString(),
                        change = s.orders_change_percent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = stringResource(Res.string.aov),
                        value = formatDecimal(s.current.average_order_value, 2),
                        change = s.aov_change_percent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard(stringResource(Res.string.active_orders), s.active_orders.toString(), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.net_revenue), formatDecimal(s.current.net_revenue, 2), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.attendance), s.attendance_today.toString(), Modifier.weight(1f))
                }
            }
        }

        // Revenue breakdown
        revenue?.let { r ->
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(stringResource(Res.string.revenue_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRowSimple(stringResource(Res.string.gross_revenue), formatDecimal(r.gross_revenue, 2))
                        InfoRowSimple(stringResource(Res.string.delivery_fees), formatDecimal(r.total_delivery_fees, 2))
                        InfoRowSimple(stringResource(Res.string.net_revenue), formatDecimal(r.net_revenue, 2))
                        Divider()
                        Text(stringResource(Res.string.payment_methods), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(Res.string.orders_intelligence), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard(stringResource(Res.string.total), o.total_orders.toString(), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.completed), o.completed_orders.toString(), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.cancelled), o.cancelled_orders.toString(), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.refunded), o.refunded_orders.toString(), Modifier.weight(1f))
                }
            }
            if (o.channel_breakdown.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(Res.string.channel_breakdown), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            o.channel_breakdown.forEach { ch ->
                                InfoRowSimple("${ch.channel} (${ch.count})", formatPercent(ch.percent, 1))
                            }
                        }
                    }
                }
            }
        }

        if (summary == null && revenue == null && orders == null) {
            item { EmptyTabContent(stringResource(Res.string.revenue_and_orders)) }
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
                    SmallInfoCard(stringResource(Res.string.busiest_hour), "${pt.busiest_hour}:00", Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.busiest_day), pt.busiest_day, Modifier.weight(1f))
                }
            }
            item {
                Text(stringResource(Res.string.hourly_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        pt.hourly_data.filter { it.order_count > 0 }.forEach { h ->
                            InfoRowSimple("${h.hour}:00 - ${h.hour + 1}:00", stringResource(Res.string.orders_revenue_format, h.order_count, formatDecimal(h.revenue, 0)))
                        }
                        if (pt.hourly_data.all { it.order_count == 0 }) {
                            Text(stringResource(Res.string.no_orders_period), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(stringResource(Res.string.day_of_week), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pt.day_of_week.forEach { d ->
                            InfoRowSimple(stringResource(Res.string.day_orders_format, d.name, d.order_count), formatDecimal(d.revenue, 2))
                        }
                    }
                }
            }
        }

        if (peakTimes == null) {
            item { EmptyTabContent(stringResource(Res.string.tab_peak_times)) }
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
            Text(stringResource(Res.string.cashier_performance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (cashiers.isEmpty()) {
            item { Text(stringResource(Res.string.no_cashier_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(cashiers) { c ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.cashier_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    InfoRowSimple(stringResource(Res.string.orders), c.order_count.toString())
                    InfoRowSimple(stringResource(Res.string.revenue), formatDecimal(c.revenue, 2))
                    InfoRowSimple(stringResource(Res.string.aov), formatDecimal(c.average_order_value, 2))
                    InfoRowSimple(stringResource(Res.string.cancelled), "${c.cancelled_orders} (${formatPercent(c.cancellation_rate, 1)})")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text(stringResource(Res.string.delivery_performance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (drivers.isEmpty()) {
            item { Text(stringResource(Res.string.no_delivery_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(drivers) { d ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.driver_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    InfoRowSimple(stringResource(Res.string.completed_orders), d.orders_completed.toString())
                    InfoRowSimple(stringResource(Res.string.fees_collected), formatDecimal(d.fees_collected, 2))
                    InfoRowSimple(stringResource(Res.string.revenue), formatDecimal(d.revenue, 2))
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
                item { Text(stringResource(Res.string.top_selling), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(p.top_selling.take(10)) { item -> ProductRow(item) }
            }
            if (p.revenue_by_category.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { Text(stringResource(Res.string.revenue_by_category), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
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
                item { Text(stringResource(Res.string.low_margin_warnings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                items(p.low_margin_warnings.take(5)) { item -> ProductRow(item) }
            }
        }

        if (products == null) {
            item { EmptyTabContent(stringResource(Res.string.tab_products)) }
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
                    SmallInfoCard(stringResource(Res.string.total), ci.total_customers.toString(), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.new_label), formatPercent(ci.new_customers_percent, 0), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.returning), formatPercent(ci.returning_customers_percent, 0), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard(stringResource(Res.string.avg_spend), formatDecimal(ci.average_spend, 2), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.ltv), formatDecimal(ci.lifetime_value, 2), Modifier.weight(1f))
                }
            }
        }

        // Sort chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("total_spent" to stringResource(Res.string.by_spend), "order_count" to stringResource(Res.string.by_orders), "name" to stringResource(Res.string.by_name)).forEach { (sort, label) ->
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
                    Text(stringResource(Res.string.customers_page_format, data.total, data.page, data.total_pages), style = MaterialTheme.typography.labelLarge)
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
                            InfoRowSimple(stringResource(Res.string.phone), customer.phone)
                            InfoRowSimple(stringResource(Res.string.orders), customer.order_count.toString())
                            InfoRowSimple(stringResource(Res.string.total_spent), "${formatDecimal(customer.total_spent, 2)} EGP")
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
                        item { Text(stringResource(Res.string.top_customers), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        items(ci.top_customers) { c ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(c.customer_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    InfoRowSimple(stringResource(Res.string.phone), c.phone)
                                    InfoRowSimple(stringResource(Res.string.orders), c.order_count.toString())
                                    InfoRowSimple(stringResource(Res.string.total_spent), formatDecimal(c.total_spent, 2))
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
                    SmallInfoCard(stringResource(Res.string.items), s.total_items.toString(), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.stock_value), formatDecimal(s.total_stock_value, 2), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.profit), formatDecimal(s.potential_profit, 2), Modifier.weight(1f))
                }
            }

            if (s.out_of_stock_items.isNotEmpty()) {
                item { Text(stringResource(Res.string.out_of_stock), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                items(s.out_of_stock_items) { item -> StockItemRow(item) }
            }

            if (s.low_stock_items.isNotEmpty()) {
                item { Text(stringResource(Res.string.low_stock), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF57C00)) }
                items(s.low_stock_items) { item -> StockItemRow(item) }
            }

            if (s.dead_stock_items.isNotEmpty()) {
                item { Text(stringResource(Res.string.dead_stock), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(s.dead_stock_items.take(10)) { item -> StockItemRow(item) }
            }
        }

        if (stock == null) {
            item { EmptyTabContent(stringResource(Res.string.tab_stock)) }
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
            item { Text(stringResource(Res.string.tab_offers), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallInfoCard(stringResource(Res.string.total), o.total_offers.toString(), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.active), o.active_offers.toString(), Modifier.weight(1f))
                    SmallInfoCard(stringResource(Res.string.uses), o.total_offer_uses.toString(), Modifier.weight(1f))
                }
            }
            if (o.top_offers.isNotEmpty()) {
                item { Text(stringResource(Res.string.top_offers), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                items(o.top_offers.take(5)) { offer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(offer.offer_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (offer.is_active) {
                                    Text(stringResource(Res.string.active), color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            InfoRowSimple(stringResource(Res.string.uses), offer.usage_count.toString())
                            InfoRowSimple(stringResource(Res.string.discount_given), formatDecimal(offer.total_discount_given, 2))
                            InfoRowSimple(stringResource(Res.string.revenue), formatDecimal(offer.total_revenue_from_offer_orders, 2))
                        }
                    }
                }
            }
        }

        // Discount section
        discounts?.let { d ->
            item { Spacer(Modifier.height(8.dp)) }
            item { Text(stringResource(Res.string.discount_analytics_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoRowSimple(stringResource(Res.string.orders_with_discount), d.total_orders_with_discount.toString())
                        InfoRowSimple(stringResource(Res.string.total_discount), formatDecimal(d.total_discount_given, 2))
                        InfoRowSimple(stringResource(Res.string.avg_per_order), formatDecimal(d.average_discount_per_order, 2))
                        InfoRowSimple(stringResource(Res.string.discount_rate_label), formatPercent(d.discount_rate, 1))
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
            item { Text(stringResource(Res.string.loyalty_program), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoRowSimple(stringResource(Res.string.points_earned), l.total_points_earned.toString())
                        InfoRowSimple(stringResource(Res.string.points_redeemed), l.total_points_redeemed.toString())
                        InfoRowSimple(stringResource(Res.string.outstanding), l.total_points_outstanding.toString())
                        InfoRowSimple(stringResource(Res.string.active_customers), l.active_loyalty_customers.toString())
                        InfoRowSimple(stringResource(Res.string.redemption_rate), formatPercent(l.redemption_rate, 1))
                        InfoRowSimple(stringResource(Res.string.points_revenue), formatDecimal(l.points_to_revenue, 2))
                    }
                }
            }
        }

        if (offers == null && discounts == null && loyalty == null) {
            item { EmptyTabContent(stringResource(Res.string.offers_and_discounts)) }
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
                            Text(stringResource(Res.string.no_active_alerts), style = MaterialTheme.typography.bodyLarge)
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
            item { EmptyTabContent(stringResource(Res.string.tab_alerts)) }
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
                placeholder = { Text(stringResource(Res.string.search_by_name_phone)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.loadMoreOrders(vendorId, 1, selectedStatus, search = null)
                        }) { Icon(Icons.Default.Clear, stringResource(Res.string.clear)) }
                    }
                }
            )
            Button(onClick = {
                viewModel.loadMoreOrders(vendorId, 1, selectedStatus, search = searchQuery.ifBlank { null })
            }) { Text(stringResource(Res.string.search)) }
        }

        // Status filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(null to stringResource(Res.string.all), "COMPLETED" to stringResource(Res.string.filter_done), "CREATED" to stringResource(Res.string.filter_new), "CANCELED" to stringResource(Res.string.filter_cancel), "REFUNDED" to stringResource(Res.string.filter_refund)).forEach { (status, label) ->
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
                    Text(stringResource(Res.string.orders_page_format, data.total, data.page, data.total_pages), style = MaterialTheme.typography.labelLarge)
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
                                Text(order.client_name.ifBlank { stringResource(Res.string.guest) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                StatusBadge(order.status)
                            }
                            InfoRowSimple(stringResource(Res.string.channel), order.channel)
                            InfoRowSimple(stringResource(Res.string.total), "${formatDecimal(order.total, 2)} EGP")
                            InfoRowSimple(stringResource(Res.string.payment), "${order.payment_method} (${order.payment_status})")
                            if (order.client_phone.isNotBlank()) InfoRowSimple(stringResource(Res.string.phone), order.client_phone)
                        }
                    }
                }

                // Pagination
                if (data.page < data.total_pages) {
                    item {
                        Button(
                            onClick = { viewModel.loadMoreOrders(vendorId, data.page + 1, selectedStatus, search = searchQuery.ifBlank { null }) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(Res.string.load_more)) }
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
            title = { Text(stringResource(Res.string.order_details)) },
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
                            if (detail.client_name.isNotBlank()) InfoRowSimple(stringResource(Res.string.customer), detail.client_name)
                            if (detail.client_phone.isNotBlank()) InfoRowSimple(stringResource(Res.string.phone), detail.client_phone)
                            detail.client_address?.let { InfoRowSimple(stringResource(Res.string.address), it) }

                            HorizontalDivider()

                            // Price breakdown
                            InfoRowSimple(stringResource(Res.string.subtotal), "${formatDecimal(detail.subtotal, 2)} EGP")
                            if (detail.delivery_fee > 0) InfoRowSimple(stringResource(Res.string.delivery_fee), "${formatDecimal(detail.delivery_fee, 2)} EGP")
                            if (detail.discount > 0) InfoRowSimple(stringResource(Res.string.discount), "-${formatDecimal(detail.discount, 2)} EGP")
                            if (detail.tax > 0) InfoRowSimple(stringResource(Res.string.tax), "${formatDecimal(detail.tax, 2)} EGP")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(Res.string.total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${formatDecimal(detail.total, 2)} EGP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            InfoRowSimple(stringResource(Res.string.payment), "${detail.payment_method} (${detail.payment_status})")

                            // Refund info
                            if (detail.refunded_at != null || detail.refund_reason != null) {
                                HorizontalDivider()
                                Text(stringResource(Res.string.refund_info), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                detail.refund_reason?.let { InfoRowSimple(stringResource(Res.string.reason), it) }
                            }

                            // Points info
                            if (detail.points_earned > 0 || detail.points_redeemed > 0) {
                                HorizontalDivider()
                                if (detail.points_earned > 0) InfoRowSimple(stringResource(Res.string.points_earned), detail.points_earned.toString())
                                if (detail.points_redeemed > 0) InfoRowSimple(stringResource(Res.string.points_redeemed), detail.points_redeemed.toString())
                            }

                            // Order items
                            if (detail.items.isNotEmpty()) {
                                HorizontalDivider()
                                Text(stringResource(Res.string.items_count_format, detail.items.size), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                    } ?: Text(stringResource(Res.string.failed_load_order))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showOrderDetail = false
                    viewModel.clearOrderDetail()
                }) { Text(stringResource(Res.string.close)) }
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
            item { Text(stringResource(Res.string.workers_total_format, data.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(data.workers) { worker ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(worker.full_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (worker.checked_in_today) {
                                Text(stringResource(Res.string.present), color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        InfoRowSimple(stringResource(Res.string.id_label), worker.worker_id)
                        InfoRowSimple(stringResource(Res.string.role), worker.role)
                        InfoRowSimple(stringResource(Res.string.salary), "${worker.salary_type}: ${formatDecimal(worker.salary_amount, 2)}")
                        InfoRowSimple(stringResource(Res.string.attendance_30d), "${worker.attendance_days_30d} days")
                        val hours = worker.worked_minutes_30d / 60
                        val mins = worker.worked_minutes_30d % 60
                        InfoRowSimple(stringResource(Res.string.hours_30d), "${hours}h ${mins}m")
                        if (!worker.active) {
                            Text(stringResource(Res.string.inactive_label), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (workersData == null) {
            item { EmptyTabContent(stringResource(Res.string.tab_workers)) }
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
            InfoRowSimple(stringResource(Res.string.qty_sold), item.quantity_sold.toString())
            InfoRowSimple(stringResource(Res.string.revenue), formatDecimal(item.revenue, 2))
            InfoRowSimple(stringResource(Res.string.margin), formatPercent(item.profit_margin, 1))
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
            Text(stringResource(Res.string.loading_tab_data, tabName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
