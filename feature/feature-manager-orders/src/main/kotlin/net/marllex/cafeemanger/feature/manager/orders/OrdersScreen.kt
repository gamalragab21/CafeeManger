package net.marllex.cafeemanger.feature.manager.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.widthIn
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderItem
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.ui.components.ChannelChip
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.core.ui.components.OrderStatusChip
import net.marllex.cafeemanger.core.ui.components.PaymentMethodChip
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = hiltViewModel(),
    onViewReceipt: ((String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val context = LocalContext.current
    val fallbackViewReceipt: (String) -> Unit = remember(viewModel, context) {
        { orderId ->
            viewModel.shareReceipt(orderId) { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.orders)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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
                // Status filter
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedStatus == null,
                            onClick = { viewModel.filterByStatus(null) },
                            label = { Text(stringResource(R.string.all)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                    val statuses = listOf(
                        "CREATED",
                        "IN_PREPARATION",
                        "READY",
                        "ASSIGNED",
                        "OUT_FOR_DELIVERY",
                        "DELIVERED",
                        "COMPLETED",
                        "CANCELED"
                    )
                    items(statuses) { status ->
                        FilterChip(
                            selected = uiState.selectedStatus == status,
                            onClick = { viewModel.filterByStatus(status) },
                            label = { Text(status.replace("_", " ")) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().then(if (isTablet) Modifier.widthIn(max = 720.dp) else Modifier),
                    contentPadding = PaddingValues(if (isTablet) 24.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.orders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            onStatusUpdate = { viewModel.updateOrderStatus(order.id, it) },
                            onViewReceipt = { (onViewReceipt ?: fallbackViewReceipt)(order.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderCard(
    order: Order,
    onStatusUpdate: (OrderStatus) -> Unit,
    onViewReceipt: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // --- Header Section ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${order.id.takeLast(6).uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f) // Ensures ID pushes away from status
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OrderStatusChip(status = order.status)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Channel & Payment (Flowing) ---
            // If screen is narrow, PaymentMethodChip will wrap to the next line
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChannelChip(channel = order.channel.name)
                PaymentMethodChip(method = order.paymentMethod.name)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Info Section (Compact Grid Style) ---
            Text(
                text = "${order.items.size} items • Total: ${
                    String.format(
                        "%.2f",
                        order.total
                    )
                }",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Using a simple helper for these labels to keep code clean
            val details = listOfNotNull(
                order.cashierName?.let { "Cashier" to it },
                order.deliveryUserName?.let { "Delivery" to it },
                order.clientName?.let { "Client" to it },
                order.clientPhone?.let { "Phone" to it }
            )

            details.forEach { (label, value) ->
                Text(
                    text = "$label: $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Expandable Section ---
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Order Items",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    order.items.forEach { item ->
                        OrderItemRow(item = item)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    // Price Breakdown
                    PriceRow("Subtotal", String.format("%.2f EGP", order.subtotal))
                    PriceRow("Tax", String.format("%.2f EGP", order.tax))
                    PriceRow("Total", String.format("%.2f EGP", order.total), isBold = true)

                    // Secondary Info
                    if (order.notes != null || order.clientAddress != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.3f
                                    ), MaterialTheme.shapes.small
                                )
                                .padding(8.dp)
                        ) {
                            order.notes?.let {
                                Text(
                                    "Notes: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            order.clientAddress?.let {
                                Text(
                                    "Address: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // --- Action Buttons (Responsive) ---
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onViewReceipt,
                modifier = Modifier.fillMaxWidth(),
//                colors =OutlinedButtonDefaults.outlinedButtonColors(
//                    contentColor = MaterialTheme.colorScheme.primary,
//                ),
            ) {
                Icon(Icons.Filled.Receipt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("View receipt")
            }
            Spacer(modifier = Modifier.height(8.dp))
            val nextStatuses = getNextStatuses(order)
            if (nextStatuses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                // FlowRow is CRITICAL here so buttons don't disappear off-screen
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    nextStatuses.forEach { status ->
                        FilledTonalButton(
                            onClick = { onStatusUpdate(status) },
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                status.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

    }
}

// Helper for the expanded price lines
@Composable
fun PriceRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
    }
}

@Composable
private fun OrderItemRow(item: OrderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${item.quantity}x",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.itemNameSnapshot, style = MaterialTheme.typography.bodyMedium)
            item.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = String.format("%.2f", item.itemPriceSnapshot * item.quantity),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun getNextStatuses(order: Order): List<OrderStatus> {
    return OrderStatus.entries.filter { order.status.canTransitionTo(it, order.channel) }
}
