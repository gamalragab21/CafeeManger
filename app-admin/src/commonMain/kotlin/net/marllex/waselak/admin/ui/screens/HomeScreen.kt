package net.marllex.waselak.admin.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.admin.network.AnalyticsSummary
import net.marllex.waselak.admin.network.PlatformAlertDto
import net.marllex.waselak.admin.network.PlanDistribution
import net.marllex.waselak.admin.network.VendorAnalytics
import net.marllex.waselak.admin.viewmodel.HomeViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel()
) {
    val analytics by viewModel.analytics.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && analytics == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (analytics != null) {
            val data = analytics!!
            val summary = data.summary
            val planDist = data.plan_distribution

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ─── Welcome Header ──────────────────────────────
                item {
                    WelcomeHeader()
                }

                // ─── Alerts Section ──────────────────────────────
                if (alerts.isNotEmpty()) {
                    item {
                        AlertsSection(alerts)
                    }
                }

                // ─── Key Metrics Row ─────────────────────────────
                item {
                    Text(
                        text = "Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                item {
                    KeyMetricsRow(summary)
                }

                // ─── Revenue Section ─────────────────────────────
                item {
                    RevenueSection(summary)
                }

                // ─── Users & Workers ─────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TeamCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(Res.string.users),
                            icon = Icons.Filled.Person,
                            active = summary.active_users,
                            total = summary.total_users,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TeamCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(Res.string.workers),
                            icon = Icons.Filled.Groups,
                            active = summary.active_workers,
                            total = summary.total_workers,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                // ─── Plan Distribution ───────────────────────────
                item {
                    PlanDistributionCard(planDist, summary.total_vendors)
                }

                // ─── Top Active Vendors ──────────────────────────
                if (data.vendors.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top Vendors Today",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    item {
                        TopVendorsSection(data.vendors)
                    }
                }

                // Bottom spacing
                item { Spacer(Modifier.height(8.dp)) }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { viewModel.loadData() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.retry))
                }
            }
        }
    }
}

// ─── Welcome Header ──────────────────────────────────────────────

@Composable
private fun WelcomeHeader() {
    val now = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val greeting = remember(now) {
        when (now.hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
    }
    val dateStr = remember(now) {
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        "${days[now.dayOfWeek.ordinal]}, ${months[now.monthNumber - 1]} ${now.dayOfMonth}, ${now.year}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$greeting \uD83D\uDC4B",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Icon(
                Icons.Filled.Dashboard,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
            )
        }
    }
}

// ─── Key Metrics Row ─────────────────────────────────────────────

@Composable
private fun KeyMetricsRow(summary: AnalyticsSummary) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MetricCard(
                icon = Icons.Filled.Store,
                title = stringResource(Res.string.total_vendors),
                value = "${summary.total_vendors}",
                subtitle = "${summary.active_vendors} ${stringResource(Res.string.active)}",
                iconTint = Color(0xFF4CAF50),
                iconBg = Color(0xFF4CAF50).copy(alpha = 0.12f),
            )
        }
        item {
            MetricCard(
                icon = Icons.Filled.Receipt,
                title = stringResource(Res.string.orders_today),
                value = "${summary.orders_today}",
                subtitle = null,
                iconTint = Color(0xFF2196F3),
                iconBg = Color(0xFF2196F3).copy(alpha = 0.12f),
            )
        }
        item {
            MetricCard(
                icon = Icons.Filled.CalendarMonth,
                title = stringResource(Res.string.orders_this_month),
                value = "${summary.orders_this_month}",
                subtitle = null,
                iconTint = Color(0xFF9C27B0),
                iconBg = Color(0xFF9C27B0).copy(alpha = 0.12f),
            )
        }
        item {
            MetricCard(
                icon = Icons.Filled.Warning,
                title = stringResource(Res.string.suspended),
                value = "${summary.suspended_vendors}",
                subtitle = null,
                iconTint = Color(0xFFFF9800),
                iconBg = Color(0xFFFF9800).copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String?,
    iconTint: Color,
    iconBg: Color,
) {
    ElevatedCard(
        modifier = Modifier.width(170.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── Revenue Section ─────────────────────────────────────────────

@Composable
private fun RevenueSection(summary: AnalyticsSummary) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Payments,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Revenue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.revenue_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "EGP ${"%.2f".format(summary.revenue_today)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(Res.string.revenue_this_month),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "EGP ${"%.2f".format(summary.revenue_this_month)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ─── Team Card ───────────────────────────────────────────────────

@Composable
private fun TeamCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    active: Int,
    total: Int,
    color: Color,
) {
    val ratio = if (total > 0) active.toFloat() / total else 0f
    val animatedRatio by animateFloatAsState(ratio, animationSpec = tween(800))

    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "$active / $total",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${stringResource(Res.string.active)} / Total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.12f),
            )
        }
    }
}

// ─── Plan Distribution ──────────────────────────────────────────

@Composable
private fun PlanDistributionCard(planDist: PlanDistribution, totalVendors: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.plan_distribution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            val total = (planDist.starter + planDist.business + planDist.enterprise).coerceAtLeast(1)

            PlanBar("Starter", planDist.starter, total, Color(0xFF2196F3))
            Spacer(Modifier.height(10.dp))
            PlanBar("Business", planDist.business, total, Color(0xFF9C27B0))
            Spacer(Modifier.height(10.dp))
            PlanBar("Enterprise", planDist.enterprise, total, Color(0xFFFF9800))
        }
    }
}

@Composable
private fun PlanBar(name: String, count: Int, total: Int, color: Color) {
    val ratio = count.toFloat() / total
    val animatedRatio by animateFloatAsState(ratio, animationSpec = tween(800))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(90.dp),
        )
        LinearProgressIndicator(
            progress = { animatedRatio },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.12f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp),
        )
    }
}

// ─── Top Vendors Section ─────────────────────────────────────────

@Composable
private fun TopVendorsSection(vendors: List<VendorAnalytics>) {
    val sortedVendors = remember(vendors) {
        vendors.sortedByDescending { it.orders_today }.take(5)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            sortedVendors.forEachIndexed { index, vendor ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Rank badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when (index) {
                                    0 -> Color(0xFFFFD700).copy(alpha = 0.2f)
                                    1 -> Color(0xFFC0C0C0).copy(alpha = 0.2f)
                                    2 -> Color(0xFFCD7F32).copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (index) {
                                0 -> Color(0xFFB8860B)
                                1 -> Color(0xFF808080)
                                2 -> Color(0xFF8B4513)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = vendor.vendor_name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (vendor.is_suspended) {
                                Spacer(Modifier.width(6.dp))
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            stringResource(Res.string.suspended),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                    modifier = Modifier.height(24.dp),
                                )
                            }
                        }
                        Text(
                            text = "${vendor.active_workers}/${vendor.total_workers} workers active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${vendor.orders_today} orders",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "EGP ${"%.0f".format(vendor.revenue_this_month)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ─── Alerts Section ──────────────────────────────────────────────

@Composable
private fun AlertsSection(alerts: List<PlatformAlertDto>) {
    val criticalCount = alerts.count { it.severity == "CRITICAL" }
    val warningCount = alerts.count { it.severity == "WARNING" }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (criticalCount > 0)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (criticalCount > 0) Icons.Filled.Error else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (criticalCount > 0) MaterialTheme.colorScheme.error else Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.alerts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (criticalCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "$criticalCount critical",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (warningCount > 0) {
                    Surface(
                        color = Color(0xFFFF9800).copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "$warningCount warnings",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Show first 5 alerts
            alerts.take(5).forEach { alert ->
                AlertRow(alert)
                Spacer(Modifier.height(6.dp))
            }

            if (alerts.size > 5) {
                Text(
                    text = "+${alerts.size - 5} more alerts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AlertRow(alert: PlatformAlertDto) {
    val iconTint = when (alert.severity) {
        "CRITICAL" -> MaterialTheme.colorScheme.error
        else -> Color(0xFFFF9800)
    }
    val typeIcon = when (alert.type) {
        "PLAN_LIMIT" -> Icons.Outlined.DataUsage
        "NO_ORDERS" -> Icons.Outlined.RemoveShoppingCart
        "SUBSCRIPTION_EXPIRING" -> Icons.Outlined.Schedule
        "SUBSCRIPTION_EXPIRED" -> Icons.Outlined.EventBusy
        else -> Icons.Outlined.Warning
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            typeIcon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alert.vendor_name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = alert.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
