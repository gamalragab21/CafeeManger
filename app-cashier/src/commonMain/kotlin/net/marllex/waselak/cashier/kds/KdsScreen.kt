package net.marllex.waselak.cashier.kds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.KdsOrder
import net.marllex.waselak.core.model.KdsOrderItem
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.util.VariantDisplayHelper
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsScreen(
    viewModel: KdsViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Start/stop polling based on screen lifecycle
    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.kitchen_display),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null && uiState.orders.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                uiState.orders.isEmpty() -> EmptyView(stringResource(Res.string.no_active_orders))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(uiState.orders, key = { it.orderId }) { order ->
                        KdsOrderCard(
                            order = order,
                            onItemStatusChange = { itemId, status -> viewModel.updateItemStatus(itemId, status) },
                            onMarkAllReady = { viewModel.markAllReady(order.orderId, order.items.filter { it.isPending || it.isCooking }.map { it.id }) },
                        )
                    }
                }
            }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Order header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("#${order.orderNumber}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${order.channel}${order.tableNumber?.let { " - ${stringResource(Res.string.table_label, it)}" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(Res.string.elapsed_minutes, order.elapsedMinutes), style = MaterialTheme.typography.titleMedium,
                        color = if (order.elapsedMinutes > 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    if (!order.allReady) {
                        TextButton(onClick = onMarkAllReady) { Text(stringResource(Res.string.all_ready)) }
                    }
                }
            }
            order.clientName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            order.notes?.let { Text(stringResource(Res.string.note_prefix, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            // Items
            order.items.forEach { item ->
                KdsItemRow(item = item, onStatusChange = { status -> onItemStatusChange(item.id, status) })
                Spacer(Modifier.height(4.dp))
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
        modifier = Modifier.fillMaxWidth().background(bgColor, RoundedCornerShape(8.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${item.quantity}x ${item.itemName}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            VariantDisplayHelper.formatVariantSummary(item.variantOptions)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (nextStatus != null) {
            FilledTonalButton(
                onClick = { onStatusChange(nextStatus) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    when (item.kitchenStatus) {
                        "PENDING" -> stringResource(Res.string.start)
                        "COOKING" -> stringResource(Res.string.ready)
                        "READY" -> stringResource(Res.string.served)
                        else -> item.kitchenStatus
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            Text(stringResource(Res.string.served), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
