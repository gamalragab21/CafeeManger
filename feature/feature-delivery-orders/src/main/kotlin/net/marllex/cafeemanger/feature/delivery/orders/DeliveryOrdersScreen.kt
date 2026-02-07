package net.marllex.cafeemanger.feature.delivery.orders

import android.content.Context
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
import androidx.compose.material.icons.filled.Receipt
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onNavigateToReceipt: (String) -> Unit = {},
    viewModel: DeliveryOrdersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                                        onViewReceipt = { onNavigateToReceipt(order.id) }
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
                                        onViewReceipt = { onNavigateToReceipt(order.id) }
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeliveryOrderCard(
    order: Order,
    onStatusUpdate: (OrderStatus) -> Unit,
    onNavigate: () -> Unit,
    onViewReceipt: () -> Unit,
) {
    // Dynamic color logic based on status
    val containerColor = when (order.status) {
        OrderStatus.ASSIGNED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        OrderStatus.OUT_FOR_DELIVERY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        OrderStatus.DELIVERED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: ID and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "#${order.id.takeLast(6).uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OrderStatusChip(status = order.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Client Info Section (Structured for readability)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                order.clientName?.let {
                    Text(text = it, style = MaterialTheme.typography.titleSmall)
                }

                order.clientPhone?.let { phone ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Phone, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(text = " $phone", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                order.clientAddress?.let { address ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = " $address",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${order.items.size} items • ${String.format("%.2f EGP", order.total)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Section
            // FlowRow ensures buttons wrap if they don't fit side-by-side
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2 // Keeps primary actions large
            ) {
                // Secondary Action: Receipt
                OutlinedButton(
                    onClick = onViewReceipt,
                    modifier = Modifier.weight(1f, fill = false).defaultMinSize(minWidth = 120.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Receipt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Receipt", maxLines = 1)
                }

                // Primary Action: Status Update
                val context = LocalContext.current
                val (buttonText, nextStatus) = when (order.status) {
                    OrderStatus.ASSIGNED -> stringResource(R.string.start_delivery) to OrderStatus.OUT_FOR_DELIVERY
                    OrderStatus.OUT_FOR_DELIVERY -> stringResource(R.string.mark_delivered) to OrderStatus.DELIVERED
                    OrderStatus.DELIVERED -> "Complete" to OrderStatus.COMPLETED
                    else -> null to null
                }

                if (buttonText != null && nextStatus != null) {
                    Button(
                        onClick = { onStatusUpdate(nextStatus) },
                        modifier = Modifier.weight(1f).defaultMinSize(minWidth = 140.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(buttonText, maxLines = 1)
                    }
                }

                // Tertiary Action: Navigation (Only if in delivery/delivered)
                if (order.status == OrderStatus.OUT_FOR_DELIVERY || order.status == OrderStatus.DELIVERED) {
                    FilledIconButton(
                        onClick = { context.openExternalMap(order) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = "Navigate")
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableOrderCard(
    order: Order,
    onPickup: () -> Unit,
    onViewReceipt: () -> Unit,
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
                text = "${order.items.size} items - Total: ${String.format("%.2f EGP", order.total)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onViewReceipt,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("View receipt")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onPickup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pick_up_order))
            }
        }
    }
}

private fun Context.openExternalMap(order: Order) {
    val uri = when {
        order.clientAddress?.isNotBlank() == true -> Uri.parse("geo:0,0?q=${Uri.encode(order.clientAddress)}")
        order.geoLat != null && order.geoLng != null -> Uri.parse("geo:${order.geoLat},${order.geoLng}")
        else -> null
    } ?: return
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    startActivity(intent)
}

private fun Context.openBrowser(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    startActivity(intent)
}
