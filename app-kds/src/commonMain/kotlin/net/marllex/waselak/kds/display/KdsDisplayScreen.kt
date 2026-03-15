package net.marllex.waselak.kds.display

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.marllex.waselak.core.model.KdsOrder
import net.marllex.waselak.core.model.KdsOrderItem
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LanguageSelector
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.util.VariantDisplayHelper
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsDisplayScreen(
    viewModel: KdsDisplayViewModel = koinViewModel(),
    onLogout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Start/stop polling based on screen lifecycle
    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(Res.string.close))
                }
            },
            text = {
                LanguageSelector()
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(Res.string.kitchen_display),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        // Summary chips
                        SummaryChip(stringResource(Res.string.pending), uiState.summary.pending, Color(0xFFFF9800))
                        SummaryChip(stringResource(Res.string.cooking), uiState.summary.cooking, Color(0xFF2196F3))
                        SummaryChip(stringResource(Res.string.ready), uiState.summary.ready, Color(0xFF4CAF50))
                        if (uiState.summary.avgPrepTimeMinutes > 0) {
                            Text(
                                stringResource(Res.string.avg_prep_time, uiState.summary.avgPrepTimeMinutes.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    // Station filter
                    val stations = uiState.orders.flatMap { o -> o.items.mapNotNull { it.kitchenStation } }.distinct()
                    if (stations.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = uiState.selectedStation == null,
                                onClick = { viewModel.onStationFilter(null) },
                                label = { Text(stringResource(Res.string.all)) },
                            )
                            stations.forEach { station ->
                                FilterChip(
                                    selected = uiState.selectedStation == station,
                                    onClick = { viewModel.onStationFilter(station) },
                                    label = { Text(station) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(Icons.Default.Language, contentDescription = "Language / اللغة")
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh))
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = stringResource(Res.string.logout))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.orders.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::load,
            )
            uiState.orders.isEmpty() -> EmptyView(stringResource(Res.string.no_active_orders))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.orders, key = { it.orderId }) { order ->
                    KdsOrderCard(
                        order = order,
                        onItemStatusChange = { itemId, status ->
                            viewModel.updateItemStatus(itemId, status)
                        },
                        onMarkAllReady = {
                            viewModel.markAllReady(
                                order.orderId,
                                order.items.filter { it.isPending || it.isCooking }.map { it.id },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

@Composable
private fun KdsOrderCard(
    order: KdsOrder,
    onItemStatusChange: (String, String) -> Unit,
    onMarkAllReady: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Order header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "#${order.orderNumber}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    )
                    Text(
                        "${order.channel}${order.tableNumber?.let { stringResource(Res.string.table_label, it) } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(Res.string.elapsed_minutes, order.elapsedMinutes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (order.elapsedMinutes > 15) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (!order.allReady) {
                        Button(
                            onClick = onMarkAllReady,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(stringResource(Res.string.all_ready), fontSize = 14.sp)
                        }
                    }
                }
            }
            order.clientName?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            order.notes?.let {
                Text(
                    stringResource(Res.string.note_prefix, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            // Items
            order.items.forEach { item ->
                KdsItemRow(
                    item = item,
                    onStatusChange = { status -> onItemStatusChange(item.id, status) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun KdsItemRow(item: KdsOrderItem, onStatusChange: (String) -> Unit) {
    val bgColor = when {
        item.isPending -> Color(0x1AFF9800)
        item.isCooking -> Color(0x1A2196F3)
        item.isReady -> Color(0x1A4CAF50)
        item.isServed -> Color(0x1A9E9E9E)
        else -> Color.Transparent
    }
    val nextStatus = when {
        item.isPending -> "COOKING"
        item.isCooking -> "READY"
        item.isReady -> "SERVED"
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${item.quantity}x ${item.itemName}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            item.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
            VariantDisplayHelper.formatVariantSummary(item.variantOptions)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (nextStatus != null) {
            Button(
                onClick = { onStatusChange(nextStatus) },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    when (item.kitchenStatus) {
                        "PENDING" -> stringResource(Res.string.start)
                        "COOKING" -> stringResource(Res.string.ready)
                        "READY" -> stringResource(Res.string.served)
                        else -> item.kitchenStatus
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 16.sp,
                )
            }
        } else {
            Text(
                stringResource(Res.string.served),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
