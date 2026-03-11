package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.network.PlatformAnalyticsDto
import net.marllex.waselak.admin.network.VendorAnalytics
import net.marllex.waselak.admin.util.LocalWindowSizeClass
import net.marllex.waselak.admin.util.WindowWidthSizeClass
import net.marllex.waselak.admin.util.formatDecimal
import net.marllex.waselak.admin.viewmodel.AnalyticsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = koinViewModel()
) {
    val analytics by viewModel.analytics.collectAsState()
    val platformAnalytics by viewModel.platformAnalytics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAnalytics()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { viewModel.selectTab(0) },
                text = { Text(stringResource(Res.string.summary)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.selectTab(1) },
                text = { Text(stringResource(Res.string.platform_analytics)) }
            )
        }

        when (selectedTab) {
            0 -> OverviewTab(analytics, isLoading, onRetry = { viewModel.loadAnalytics() })
            1 -> PlatformTab(platformAnalytics, isLoading, onRetry = { viewModel.loadPlatformAnalytics() })
        }
    }
}

@Composable
private fun OverviewTab(
    analytics: net.marllex.waselak.admin.network.AnalyticsOverviewDto?,
    isLoading: Boolean,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && analytics == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (analytics != null) {
            val data = analytics
            val summary = data.summary
            val planDist = data.plan_distribution

            val activeText = stringResource(Res.string.active)
            val suspendedText = stringResource(Res.string.suspended)

            val widthClass = LocalWindowSizeClass.current

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Text(
                        text = stringResource(Res.string.analytics_overview),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Summary Stats
                item {
                    Text(
                        text = stringResource(Res.string.summary),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                when (widthClass) {
                    WindowWidthSizeClass.COMPACT -> {
                        item {
                            AnalyticsStatCard(
                                title = stringResource(Res.string.total_vendors),
                                value = "${summary.total_vendors}",
                                subtitle = "${summary.active_vendors} $activeText / ${summary.suspended_vendors} $suspendedText",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.orders_today), "${summary.orders_today}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.orders_this_month), "${summary.orders_this_month}", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.revenue_today), "EGP ${formatDecimal(summary.revenue_today, 2)}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.revenue_this_month), "EGP ${formatDecimal(summary.revenue_this_month, 2)}", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.users), "${summary.active_users}/${summary.total_users}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.workers), "${summary.active_workers}/${summary.total_workers}", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    WindowWidthSizeClass.MEDIUM -> {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.total_vendors), "${summary.total_vendors}",
                                    subtitle = "${summary.active_vendors} $activeText / ${summary.suspended_vendors} $suspendedText", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.orders_today), "${summary.orders_today}", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.revenue_today), "EGP ${formatDecimal(summary.revenue_today, 2)}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.revenue_this_month), "EGP ${formatDecimal(summary.revenue_this_month, 2)}", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.orders_this_month), "${summary.orders_this_month}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.users), "${summary.active_users} / ${summary.total_users}",
                                    subtitle = "$activeText / Total", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.workers), "${summary.active_workers} / ${summary.total_workers}",
                                    subtitle = "$activeText / Total", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.plan_distribution), "",
                                    subtitle = "Starter: ${planDist.starter} | Business: ${planDist.business} | Enterprise: ${planDist.enterprise}",
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    WindowWidthSizeClass.EXPANDED -> {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.total_vendors), "${summary.total_vendors}",
                                    subtitle = "${summary.active_vendors} $activeText / ${summary.suspended_vendors} $suspendedText", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.orders_today), "${summary.orders_today}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.orders_this_month), "${summary.orders_this_month}", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.revenue_today), "EGP ${formatDecimal(summary.revenue_today, 2)}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.revenue_this_month), "EGP ${formatDecimal(summary.revenue_this_month, 2)}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.users), "${summary.active_users} / ${summary.total_users}",
                                    subtitle = "$activeText / Total", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.workers), "${summary.active_workers} / ${summary.total_workers}",
                                    subtitle = "$activeText / Total", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.plan_distribution), "",
                                    subtitle = "Starter: ${planDist.starter} | Business: ${planDist.business} | Enterprise: ${planDist.enterprise}",
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Vendor Breakdown
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(Res.string.vendor_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }

                when (widthClass) {
                    WindowWidthSizeClass.COMPACT -> {
                        items(data.vendors, key = { it.vendor_id }) { vendor ->
                            VendorAnalyticsCard(vendor)
                        }
                    }
                    WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.EXPANDED -> {
                        item { VendorTableHeader() }
                        items(data.vendors, key = { it.vendor_id }) { vendor ->
                            VendorTableRow(vendor)
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        } else {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.no_data), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
            }
        }
    }
}

@Composable
private fun PlatformTab(
    platform: PlatformAnalyticsDto?,
    isLoading: Boolean,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && platform == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (platform != null) {
            val widthClass = LocalWindowSizeClass.current

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.platform_analytics),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // KPI Cards
                when (widthClass) {
                    WindowWidthSizeClass.COMPACT -> {
                        item {
                            AnalyticsStatCard(
                                title = stringResource(Res.string.monthly_recurring),
                                value = "EGP ${platform.mrr}",
                                subtitle = "${platform.active_subscriptions} ${stringResource(Res.string.active_subscriptions)}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.total_vendors), "${platform.total_vendors}",
                                    subtitle = "${platform.active_vendors} ${stringResource(Res.string.active)}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.new_this_month), "${platform.new_this_month}", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.total_orders_platform), "${platform.orders_this_month}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.total_revenue_platform), "EGP ${formatDecimal(platform.revenue_this_month, 2)}", modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.avg_revenue_vendor), "EGP ${formatDecimal(platform.avg_revenue_per_vendor, 2)}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.expired_subscriptions), "${platform.expired_subscriptions}", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.EXPANDED -> {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.monthly_recurring), "EGP ${platform.mrr}",
                                    subtitle = "${platform.active_subscriptions} ${stringResource(Res.string.active_subscriptions)}",
                                    modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.total_vendors), "${platform.total_vendors}",
                                    subtitle = "${platform.active_vendors} ${stringResource(Res.string.active)} / ${platform.new_this_month} new",
                                    modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.total_revenue_platform), "EGP ${formatDecimal(platform.revenue_this_month, 2)}",
                                    subtitle = "Avg: EGP ${formatDecimal(platform.avg_revenue_per_vendor, 2)}/vendor",
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsStatCard(stringResource(Res.string.total_orders_platform), "${platform.orders_this_month}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.active_subscriptions), "${platform.active_subscriptions}", modifier = Modifier.weight(1f))
                                AnalyticsStatCard(stringResource(Res.string.expired_subscriptions), "${platform.expired_subscriptions}", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Plan Revenue Breakdown
                if (platform.plan_revenue.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.plan_revenue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            platform.plan_revenue.forEach { plan ->
                                val planColor = when (plan.plan.uppercase()) {
                                    "ENTERPRISE" -> MaterialTheme.colorScheme.tertiary
                                    "BUSINESS" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                                ElevatedCard(modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Surface(
                                            color = planColor.copy(alpha = 0.15f),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                plan.plan,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = planColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text("EGP ${plan.revenue}/mo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("${plan.count} vendors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Top Vendors
                if (platform.top_vendors.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.top_vendors), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    when (widthClass) {
                        WindowWidthSizeClass.COMPACT -> {
                            items(platform.top_vendors) { vendor ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(vendor.vendor_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${vendor.orders} orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("EGP ${formatDecimal(vendor.revenue, 2)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.EXPANDED -> {
                            // Table header
                            item {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("#", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                                        Text(stringResource(Res.string.vendor), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                                        Text("Orders", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                        Text("Revenue", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                                    }
                                }
                            }
                            items(platform.top_vendors.size) { index ->
                                val vendor = platform.top_vendors[index]
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${index + 1}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(vendor.vendor_name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(2f))
                                        Text("${vendor.orders}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                        Text("EGP ${formatDecimal(vendor.revenue, 2)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1.5f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        } else {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.no_data), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
            }
        }
    }
}

// ─── Shared Composables ────────────────────────────────────────────

@Composable
private fun AnalyticsStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VendorAnalyticsCard(vendor: VendorAnalytics) {
    val statusColor = if (vendor.is_suspended) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val activeText = stringResource(Res.string.active)
    val suspendedText = stringResource(Res.string.suspended)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(vendor.vendor_name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(if (vendor.is_suspended) suspendedText else activeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(Res.string.orders_today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${vendor.orders_today}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(Res.string.revenue_month), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("EGP ${formatDecimal(vendor.revenue_this_month, 2)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(Res.string.workers), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${vendor.active_workers}/${vendor.total_workers}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun VendorTableHeader() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.vendor), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
            Text(stringResource(Res.string.status), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(stringResource(Res.string.orders_today), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(stringResource(Res.string.revenue_month), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
            Text(stringResource(Res.string.workers), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun VendorTableRow(vendor: VendorAnalytics) {
    val statusColor = if (vendor.is_suspended) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val activeText = stringResource(Res.string.active)
    val suspendedText = stringResource(Res.string.suspended)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(vendor.vendor_name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(2f))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(if (vendor.is_suspended) suspendedText else activeText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
            Text("${vendor.orders_today}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("EGP ${formatDecimal(vendor.revenue_this_month, 2)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
            Text("${vendor.active_workers}/${vendor.total_workers}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
    }
}
