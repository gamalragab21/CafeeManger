package net.marllex.waselak.manager.scheduledorders

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
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.scheduled_orders))
                        Text(stringResource(Res.string.active_count, uiState.activeCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    order.clientName?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    order.clientPhone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                SuggestionChip(onClick = {}, label = { Text(order.status) })
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.order_channel_items, order.channel, order.itemCount), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.order_total_payment, order.total.toString(), order.paymentMethod), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (order.isScheduled) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.confirm)) }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.cancel)) }
                }
            }
        }
    }
}
