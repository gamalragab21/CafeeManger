package net.marllex.cafeemanger.feature.delivery.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.core.ui.components.OrderStatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryOrdersScreen(
    onNavigateToOrder: (String) -> Unit = {},
    viewModel: DeliveryOrdersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_deliveries)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.orders.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadOrders,
            )
            else -> Column(modifier = Modifier.padding(padding)) {
                // Tab selector: My Orders vs Available
                TabRow(selectedTabIndex = uiState.selectedTab) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = { Text(stringResource(R.string.my_orders, uiState.orders.size)) },
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = { Text(
                            stringResource(
                                R.string.available,
                                uiState.availableOrders.size
                            )) },
                    )
                }

                when (uiState.selectedTab) {
                    0 -> {
                        // My Orders tab
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = uiState.selectedStatus == null,
                                    onClick = { viewModel.filterByStatus(null) },
                                    label = { Text(stringResource(R.string.all)) },
                                )
                            }
                            val statuses = listOf("ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED")
                            items(statuses) { status ->
                                FilterChip(
                                    selected = uiState.selectedStatus == status,
                                    onClick = { viewModel.filterByStatus(status) },
                                    label = { Text(status.replace("_", " ")) },
                                )
                            }
                        }

                        if (uiState.orders.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(stringResource(R.string.no_deliveries_yet), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(uiState.orders, key = { it.id }) { order ->
                                    DeliveryOrderCard(
                                        order = order,
                                        onStatusUpdate = { viewModel.updateStatus(order.id, it) },
                                        onNavigate = { onNavigateToOrder(order.id) },
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Available orders tab
                        if (uiState.availableOrders.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(stringResource(R.string.no_available_orders), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(uiState.availableOrders, key = { it.id }) { order ->
                                    AvailableOrderCard(
                                        order = order,
                                        onPickup = { viewModel.pickupOrder(order.id) },
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

@Composable
private fun DeliveryOrderCard(
    order: Order,
    onStatusUpdate: (OrderStatus) -> Unit,
    onNavigate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (order.status) {
                OrderStatus.ASSIGNED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                OrderStatus.OUT_FOR_DELIVERY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${order.id.takeLast(6).uppercase()}", style = MaterialTheme.typography.titleMedium)
                OrderStatusChip(status = order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Client info
            order.clientName?.let {
                Text(text = "Client: $it", style = MaterialTheme.typography.bodyMedium)
            }
            order.clientPhone?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = " $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
            order.clientAddress?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = " $it", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${order.items.size} items - Total: ${String.format("%.2f", order.total)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (order.status) {
                    OrderStatus.ASSIGNED -> {
                        Button(
                            onClick = { onStatusUpdate(OrderStatus.OUT_FOR_DELIVERY) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.start_delivery))
                        }
                    }
                    OrderStatus.OUT_FOR_DELIVERY -> {
                        Button(
                            onClick = { onStatusUpdate(OrderStatus.DELIVERED) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.mark_delivered))
                        }
                        IconButton(onClick = onNavigate) {
                            Icon(Icons.Filled.LocationOn, contentDescription = "Navigate", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun AvailableOrderCard(
    order: Order,
    onPickup: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${order.id.takeLast(6).uppercase()}", style = MaterialTheme.typography.titleMedium)
                OrderStatusChip(status = order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            order.clientName?.let {
                Text(text = "Client: $it", style = MaterialTheme.typography.bodyMedium)
            }
            order.clientAddress?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = " $it", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${order.items.size} items - Total: ${String.format("%.2f", order.total)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPickup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pick_up_order))
            }
        }
    }
}
