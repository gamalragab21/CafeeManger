package net.marllex.waselak.manager.scheduledorders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.ScheduledOrder
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledOrdersScreen(
    viewModel: ScheduledOrdersViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val statusFilters = listOf(null to stringResource(Res.string.all), "SCHEDULED" to stringResource(Res.string.scheduled), "CONFIRMED" to stringResource(Res.string.confirmed), "PREPARING" to stringResource(Res.string.preparing), "COMPLETED" to stringResource(Res.string.completed))

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.scheduled_orders),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Status filter chips
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    statusFilters.forEach { (status, label) ->
                        FilterChip(
                            selected = uiState.selectedStatus == status,
                            onClick = { viewModel.onStatusFilter(status) },
                            label = { Text(label) },
                        )
                    }
                }

                when {
                    uiState.isLoading -> LoadingIndicator()
                    uiState.error != null && uiState.orders.isEmpty() -> ErrorView(message = uiState.error!!, onRetry = viewModel::load)
                    uiState.filteredOrders.isEmpty() -> EmptyView(stringResource(Res.string.no_scheduled_orders))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(uiState.filteredOrders, key = { it.id }) { order ->
                            ScheduledOrderCard(order = order, onConfirm = { viewModel.updateStatus(order.id, "CONFIRMED") }, onCancel = { viewModel.updateStatus(order.id, "CANCELLED") })
                        }
                    }
                }
            }
    }
}

@Composable
private fun ScheduledOrderCard(order: ScheduledOrder, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val statusLabel = when (order.status) {
        "SCHEDULED" -> stringResource(Res.string.scheduled)
        "CONFIRMED" -> stringResource(Res.string.confirmed)
        "PREPARING" -> stringResource(Res.string.preparing)
        "READY" -> stringResource(Res.string.ready)
        "COMPLETED" -> stringResource(Res.string.completed)
        "CANCELLED" -> stringResource(Res.string.cancelled)
        else -> order.status
    }
    val statusColor = when (order.status) {
        "SCHEDULED" -> MaterialTheme.colorScheme.primary
        "CONFIRMED" -> MaterialTheme.colorScheme.tertiary
        "PREPARING" -> MaterialTheme.colorScheme.secondary
        "READY" -> MaterialTheme.colorScheme.primary
        "COMPLETED" -> MaterialTheme.colorScheme.outline
        "CANCELLED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val channelLabel = when (order.channel) {
        "PICKUP_LATER" -> stringResource(Res.string.channel_pickup_later)
        "DELIVERY" -> stringResource(Res.string.channel_delivery)
        "DINE_IN" -> stringResource(Res.string.channel_dine_in)
        else -> order.channel
    }
    val paymentLabel = when (order.paymentMethod) {
        "CASH" -> stringResource(Res.string.payment_cash)
        else -> order.paymentMethod
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    order.clientName?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    order.clientPhone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(statusLabel, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = statusColor.copy(alpha = 0.12f),
                        labelColor = statusColor,
                    ),
                    border = null,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.order_channel_items, channelLabel, order.itemCount), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.order_total_payment, order.total.toString(), paymentLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (order.isScheduled) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.confirm)) }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.cancel)) }
                }
            }
        }
    }
}
