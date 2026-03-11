package net.marllex.waselak.admin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import net.marllex.waselak.admin.network.ActionStatDto
import net.marllex.waselak.admin.network.EndpointStatDto
import net.marllex.waselak.admin.network.LogEntryDto
import net.marllex.waselak.admin.network.ResourceStatDto
import net.marllex.waselak.admin.util.LocalWindowSizeClass
import net.marllex.waselak.admin.util.WindowWidthSizeClass
import net.marllex.waselak.admin.util.formatDecimal
import net.marllex.waselak.admin.viewmodel.LogsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsDashboardScreen(
    viewModel: LogsViewModel = koinViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    val topEndpoints by viewModel.topEndpoints.collectAsState()
    val slowestEndpoints by viewModel.slowestEndpoints.collectAsState()
    val errorEndpoints by viewModel.errorEndpoints.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val resourceBreakdown by viewModel.resourceBreakdown.collectAsState()
    val actionBreakdown by viewModel.actionBreakdown.collectAsState()
    val monitoring by viewModel.monitoring.collectAsState()
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsState()

    val selectedVendorId by viewModel.selectedVendorId.collectAsState()
    val selectedMethod by viewModel.selectedMethod.collectAsState()
    val selectedStatusGroup by viewModel.selectedStatusGroup.collectAsState()
    val selectedResource by viewModel.selectedResource.collectAsState()
    val pathSearch by viewModel.pathSearch.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
        viewModel.loadLogs()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading && stats == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val widthClass = LocalWindowSizeClass.current

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Header ─────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.logs_dashboard),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { viewModel.toggleMonitoring() }) {
                                Icon(
                                    Icons.Default.Monitor,
                                    contentDescription = stringResource(Res.string.live_monitoring),
                                    tint = if (monitoringEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                viewModel.loadDashboard()
                                viewModel.loadLogs()
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.retry))
                            }
                            IconButton(onClick = { showCleanupDialog = true }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(Res.string.cleanup_logs),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // ─── Live Monitoring Banner ─────────────────────────
                monitoring?.let { m ->
                    if (monitoringEnabled) {
                        item {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.live_monitoring),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50)
                                        )
                                        Text(
                                            text = stringResource(Res.string.monitoring_on),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(Res.string.requests_per_min),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatDecimal(m.requestsPerMinute, 1),
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(Res.string.p95_duration),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${m.p95DurationMs} ms",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (m.p95DurationMs > 1000) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    if (m.activeResources.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(Res.string.active_resources),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            m.activeResources.take(8).forEach { res ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(
                                                        text = res,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── Stats Cards ────────────────────────────────────
                stats?.let { s ->
                    when (widthClass) {
                        WindowWidthSizeClass.COMPACT -> {
                            item {
                                MiniStatCard(
                                    title = stringResource(Res.string.total_requests),
                                    value = "${s.totalRequests}",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            item {
                                MiniStatCard(
                                    title = stringResource(Res.string.error_count),
                                    value = "${s.errorCount}",
                                    valueColor = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            item {
                                MiniStatCard(
                                    title = stringResource(Res.string.error_rate),
                                    value = "${formatDecimal(s.errorRate, 1)}%",
                                    valueColor = if (s.errorRate > 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            item {
                                MiniStatCard(
                                    title = stringResource(Res.string.avg_duration),
                                    value = "${formatDecimal(s.avgDurationMs, 0)} ms",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        WindowWidthSizeClass.MEDIUM -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MiniStatCard(
                                        title = stringResource(Res.string.total_requests),
                                        value = "${s.totalRequests}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiniStatCard(
                                        title = stringResource(Res.string.error_count),
                                        value = "${s.errorCount}",
                                        valueColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MiniStatCard(
                                        title = stringResource(Res.string.error_rate),
                                        value = "${formatDecimal(s.errorRate, 1)}%",
                                        valueColor = if (s.errorRate > 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiniStatCard(
                                        title = stringResource(Res.string.avg_duration),
                                        value = "${formatDecimal(s.avgDurationMs, 0)} ms",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        WindowWidthSizeClass.EXPANDED -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MiniStatCard(
                                        title = stringResource(Res.string.total_requests),
                                        value = "${s.totalRequests}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiniStatCard(
                                        title = stringResource(Res.string.error_count),
                                        value = "${s.errorCount}",
                                        valueColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiniStatCard(
                                        title = stringResource(Res.string.error_rate),
                                        value = "${formatDecimal(s.errorRate, 1)}%",
                                        valueColor = if (s.errorRate > 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiniStatCard(
                                        title = stringResource(Res.string.avg_duration),
                                        value = "${formatDecimal(s.avgDurationMs, 0)} ms",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Status breakdown chips
                    if (s.statusBreakdown.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.status_breakdown),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                s.statusBreakdown.forEach { (group, count) ->
                                    val chipColor = when (group) {
                                        "2xx" -> Color(0xFF4CAF50)
                                        "4xx" -> Color(0xFFFF9800)
                                        "5xx" -> Color(0xFFF44336)
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                    Surface(
                                        color = chipColor.copy(alpha = 0.15f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "$group: $count",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = chipColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── Resource Breakdown ──────────────────────────────
                if (resourceBreakdown.isNotEmpty()) {
                    item {
                        ResourceBreakdownSection(
                            title = stringResource(Res.string.resource_breakdown),
                            resources = resourceBreakdown
                        )
                    }
                }

                // ─── Action Breakdown ────────────────────────────────
                if (actionBreakdown.isNotEmpty()) {
                    item {
                        ActionBreakdownSection(
                            title = stringResource(Res.string.action_breakdown),
                            actions = actionBreakdown
                        )
                    }
                }

                // ─── Endpoint Analytics ─────────────────────────────
                if (topEndpoints.isNotEmpty()) {
                    item {
                        EndpointSection(
                            title = stringResource(Res.string.top_endpoints),
                            endpoints = topEndpoints,
                            highlightDuration = false
                        )
                    }
                }

                if (slowestEndpoints.isNotEmpty()) {
                    item {
                        EndpointSection(
                            title = stringResource(Res.string.slowest_endpoints),
                            endpoints = slowestEndpoints,
                            highlightDuration = true
                        )
                    }
                }

                if (errorEndpoints.isNotEmpty()) {
                    item {
                        EndpointSection(
                            title = stringResource(Res.string.error_endpoints),
                            endpoints = errorEndpoints,
                            highlightDuration = false,
                            highlightErrors = true
                        )
                    }
                }

                // ─── Filters ────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.all_logs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filters",
                                tint = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (showFilters) {
                    item {
                        FiltersSection(
                            vendors = vendors,
                            resourceBreakdown = resourceBreakdown,
                            selectedVendorId = selectedVendorId,
                            selectedMethod = selectedMethod,
                            selectedStatusGroup = selectedStatusGroup,
                            selectedResource = selectedResource,
                            pathSearch = pathSearch,
                            onVendorChange = { viewModel.setVendorFilter(it) },
                            onMethodChange = { viewModel.setMethodFilter(it) },
                            onStatusGroupChange = { viewModel.setStatusGroupFilter(it) },
                            onResourceChange = { viewModel.setResourceFilter(it) },
                            onPathSearchChange = { viewModel.setPathSearch(it) },
                            onApply = { viewModel.applyFilters() }
                        )
                    }
                }

                // ─── Log entries ────────────────────────────────────
                val logsData = logs
                if (logsData != null && logsData.logs.isNotEmpty()) {
                    items(logsData.logs, key = { it.id }) { entry ->
                        LogEntryCard(entry)
                    }

                    // Pagination
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.loadLogs(currentPage - 1) },
                                enabled = currentPage > 1
                            ) {
                                Text(stringResource(Res.string.previous_page))
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "${currentPage} / ${logsData.totalPages}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(16.dp))
                            OutlinedButton(
                                onClick = { viewModel.loadLogs(currentPage + 1) },
                                enabled = currentPage < logsData.totalPages
                            ) {
                                Text(stringResource(Res.string.next_page))
                            }
                        }
                    }
                } else if (logsData != null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.no_logs),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // Cleanup confirmation dialog
    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = { Text(stringResource(Res.string.cleanup_logs)) },
            text = { Text("Delete logs older than 30 days?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cleanupLogs(30)
                        showCleanupDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun MiniStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun ResourceBreakdownSection(
    title: String,
    resources: List<ResourceStatDto>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            resources.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = r.resource,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "${r.count}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${formatDecimal(r.avgDurationMs, 0)}ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (r.errorCount > 0) {
                            Text(
                                text = "${r.errorCount} err",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBreakdownSection(
    title: String,
    actions: List<ActionStatDto>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            actions.forEach { a ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = a.action,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "${a.count}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (a.errorCount > 0) {
                            Text(
                                text = "${a.errorCount} err",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointSection(
    title: String,
    endpoints: List<EndpointStatDto>,
    highlightDuration: Boolean = false,
    highlightErrors: Boolean = false
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            endpoints.forEach { ep ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MethodBadge(ep.method)
                        Text(
                            text = ep.path,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${ep.count}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (highlightDuration) {
                            Text(
                                text = "${formatDecimal(ep.avgDurationMs, 0)}ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ep.avgDurationMs > 500) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (highlightErrors && ep.errorCount > 0) {
                            Text(
                                text = "${ep.errorCount} err",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val color = when (method) {
        "GET" -> Color(0xFF4CAF50)
        "POST" -> Color(0xFF2196F3)
        "PUT" -> Color(0xFFFF9800)
        "PATCH" -> Color(0xFF9C27B0)
        "DELETE" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = method,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun StatusBadge(statusCode: Int) {
    val color = when {
        statusCode in 200..299 -> Color(0xFF4CAF50)
        statusCode in 300..399 -> Color(0xFF2196F3)
        statusCode in 400..499 -> Color(0xFFFF9800)
        statusCode >= 500 -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = "$statusCode",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSection(
    vendors: List<net.marllex.waselak.admin.network.LogVendorDto>,
    resourceBreakdown: List<ResourceStatDto>,
    selectedVendorId: String?,
    selectedMethod: String?,
    selectedStatusGroup: String?,
    selectedResource: String?,
    pathSearch: String,
    onVendorChange: (String?) -> Unit,
    onMethodChange: (String?) -> Unit,
    onStatusGroupChange: (String?) -> Unit,
    onResourceChange: (String?) -> Unit,
    onPathSearchChange: (String) -> Unit,
    onApply: () -> Unit
) {
    val allText = stringResource(Res.string.all)
    val methods = listOf(null, "GET", "POST", "PUT", "PATCH", "DELETE")
    val statusGroups = listOf(null, "2xx", "4xx", "5xx")
    val resources = listOf<String?>(null) + resourceBreakdown.map { it.resource }

    var vendorExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var resourceExpanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Vendor filter
            ExposedDropdownMenuBox(
                expanded = vendorExpanded,
                onExpandedChange = { vendorExpanded = it }
            ) {
                OutlinedTextField(
                    value = vendors.find { it.id == selectedVendorId }?.name ?: allText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.filter_vendor)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vendorExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = vendorExpanded,
                    onDismissRequest = { vendorExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(allText) },
                        onClick = {
                            onVendorChange(null)
                            vendorExpanded = false
                        }
                    )
                    vendors.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.name) },
                            onClick = {
                                onVendorChange(v.id)
                                vendorExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Method filter
                ExposedDropdownMenuBox(
                    expanded = methodExpanded,
                    onExpandedChange = { methodExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedMethod ?: allText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.filter_method)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = methodExpanded,
                        onDismissRequest = { methodExpanded = false }
                    ) {
                        methods.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m ?: allText) },
                                onClick = {
                                    onMethodChange(m)
                                    methodExpanded = false
                                }
                            )
                        }
                    }
                }

                // Status filter
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedStatusGroup ?: allText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.filter_status)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        statusGroups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g ?: allText) },
                                onClick = {
                                    onStatusGroupChange(g)
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Resource filter
            ExposedDropdownMenuBox(
                expanded = resourceExpanded,
                onExpandedChange = { resourceExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedResource ?: allText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.filter_resource)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resourceExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = resourceExpanded,
                    onDismissRequest = { resourceExpanded = false }
                ) {
                    resources.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r ?: allText) },
                            onClick = {
                                onResourceChange(r)
                                resourceExpanded = false
                            }
                        )
                    }
                }
            }

            // Path search
            OutlinedTextField(
                value = pathSearch,
                onValueChange = onPathSearchChange,
                label = { Text(stringResource(Res.string.filter_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Apply button
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.filter_status))
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntryDto) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Collapsed header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MethodBadge(entry.method)
                    Text(
                        text = entry.path,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(entry.statusCode)
                    Text(
                        text = "${entry.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Resource + Action badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                entry.resource?.let { res ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = res,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                entry.action?.let { act ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = act,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                // Timestamp
                Spacer(Modifier.weight(1f))
                Text(
                    text = entry.createdAt.replace("T", " ").take(19),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Tags row (always visible if present)
            entry.tags?.let { tagsJson ->
                val parsedTags = remember(tagsJson) {
                    try {
                        Json.parseToJsonElement(tagsJson).jsonObject
                    } catch (_: Exception) {
                        null
                    }
                }
                parsedTags?.let { tags ->
                    if (tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.entries.take(5).forEach { (key, value) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = "$key=${value.jsonPrimitive.content}",
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expanded details
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))

                    entry.vendorId?.let { DetailRow("Vendor ID", it) }
                    entry.userId?.let { DetailRow("User ID", it) }
                    entry.userRole?.let { DetailRow("Role", it) }
                    entry.clientIp?.let { DetailRow("Client IP", it) }
                    entry.queryParams?.let { DetailRow("Query", it) }
                    entry.userAgent?.let {
                        DetailRow("User-Agent", it.take(100) + if (it.length > 100) "..." else "")
                    }

                    // Show all tags in expanded view
                    entry.tags?.let { tagsJson ->
                        val parsedTags = remember(tagsJson) {
                            try {
                                Json.parseToJsonElement(tagsJson).jsonObject
                            } catch (_: Exception) {
                                null
                            }
                        }
                        parsedTags?.let { tags ->
                            if (tags.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(Res.string.tags),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                tags.forEach { (key, value) ->
                                    DetailRow(key, value.jsonPrimitive.content)
                                }
                            }
                        }
                    }

                    entry.requestBody?.let { body ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.request_body),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = body.take(500) + if (body.length > 500) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    entry.responseBody?.let { body ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.response_body),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            color = if (entry.statusCode >= 400) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = body.take(500) + if (body.length > 500) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Description
                    entry.description?.let { desc ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Trace Log (step-by-step request trace)
                    entry.traceLog?.let { traceJson ->
                        val traceSteps = remember(traceJson) {
                            try {
                                Json.parseToJsonElement(traceJson).jsonArray
                            } catch (_: Exception) {
                                null
                            }
                        }
                        traceSteps?.let { steps ->
                            if (steps.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Request Trace (${steps.size} steps)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        steps.forEachIndexed { index, stepElement ->
                                            val stepObj = stepElement.jsonObject
                                            val stepNum = stepObj["step"]?.jsonPrimitive?.intOrNull ?: (index + 1)
                                            val message = stepObj["message"]?.jsonPrimitive?.content ?: ""
                                            val elapsedMs = stepObj["elapsed_ms"]?.jsonPrimitive?.longOrNull
                                            val data = try {
                                                stepObj["data"]?.jsonObject
                                            } catch (_: Exception) {
                                                null
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                // Step number badge
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = MaterialTheme.shapes.extraSmall
                                                ) {
                                                    Text(
                                                        text = "$stepNum",
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                    // Show data key-value pairs
                                                    data?.let { d ->
                                                        d.entries.forEach { (key, value) ->
                                                            Text(
                                                                text = "  $key: ${value.jsonPrimitive.content}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                }
                                                // Elapsed time
                                                elapsedMs?.let {
                                                    Text(
                                                        text = "+${it}ms",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontFamily = FontFamily.Monospace,
                                                    )
                                                }
                                            }
                                            if (index < steps.size - 1) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 24.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    entry.errorMessage?.let { err ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = err.take(300),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
