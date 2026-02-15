package net.marllex.waselak.feature.delivery.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.delivery.orders.generated.resources.Res
import net.marllex.waselak.feature.delivery.orders.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import net.marllex.waselak.core.model.OrderItem
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.OrderStatusChip
import net.marllex.waselak.core.ui.components.formatStatusLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryOrdersScreen(
    onNavigateToOrder: (String) -> Unit = {},
    onNavigateToReceipt: (String) -> Unit = {},
    viewModel: DeliveryOrdersViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-refresh when screen becomes visible
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadOrders()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    val logoUrl = uiState.vendorLogoUrl
                    if (!logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = logoUrl, contentDescription = null,
                            modifier = Modifier.padding(start = 12.dp).size(36.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier.padding(start = 12.dp).size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Store, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                title = {
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(uiState.vendorName.ifBlank { stringResource(Res.string.my_deliveries) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(Res.string.my_deliveries), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.orders.isEmpty() && uiState.availableOrders.isEmpty() -> LoadingIndicator()
            uiState.error != null && uiState.orders.isEmpty() && uiState.availableOrders.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadOrders,
            )
            else -> Column(modifier = Modifier.padding(padding)) {
                // Tab selector: My Orders vs Available
                TabRow(selectedTabIndex = uiState.selectedTab) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = { Text(stringResource(Res.string.my_orders, uiState.orders.size)) },
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = { Text(
                            stringResource(
                                Res.string.available,
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
                                    label = { Text(stringResource(Res.string.all)) },
                                )
                            }
                            val statuses = listOf(OrderStatus.ASSIGNED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)
                            items(statuses) { status ->
                                FilterChip(
                                    selected = uiState.selectedStatus == status.name,
                                    onClick = { viewModel.filterByStatus(status.name) },
                                    label = { Text(formatStatusLabel(status)) },
                                )
                            }
                        }

                        if (uiState.orders.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(stringResource(Res.string.no_deliveries_yet), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(Res.string.no_available_orders), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val platformActions = rememberPlatformActions()
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
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

            // Expandable order items
            var showItems by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showItems = !showItems },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${order.items.size} ${stringResource(Res.string.order_items)} • ${CurrencyFormatter.format(order.total)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                Icon(
                    if (showItems) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = showItems,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    order.items.forEach { item ->
                        DeliveryOrderItemRow(item = item)
                    }
                }
            }

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
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Receipt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.view_receipt), maxLines = 1)
                }

                // Primary Action: Status Update
                val (buttonText, nextStatus) = when (order.status) {
                    OrderStatus.ASSIGNED -> stringResource(Res.string.start_delivery) to OrderStatus.OUT_FOR_DELIVERY
                    OrderStatus.OUT_FOR_DELIVERY -> stringResource(Res.string.mark_delivered) to OrderStatus.DELIVERED
                    OrderStatus.DELIVERED -> stringResource(Res.string.complete) to OrderStatus.COMPLETED
                    else -> null to null
                }

                if (buttonText != null && nextStatus != null) {
                    Button(
                        onClick = { onStatusUpdate(nextStatus) },
                        modifier = Modifier.weight(1f).defaultMinSize(minWidth = 140.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(buttonText, maxLines = 1)
                    }
                }

                // Tertiary Action: Navigation (Only if in delivery/delivered)
                if (order.status == OrderStatus.OUT_FOR_DELIVERY || order.status == OrderStatus.DELIVERED) {
                    FilledIconButton(
                        onClick = { platformActions.openMap(order.clientAddress, order.geoLat, order.geoLng) },
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
        shape = RoundedCornerShape(16.dp)
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
                Text(text = "${stringResource(Res.string.client_name)}: $it", style = MaterialTheme.typography.bodyMedium)
            }
            order.clientAddress?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = " $it", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${order.items.size} ${stringResource(Res.string.order_items)} - ${stringResource(Res.string.total)}: ${CurrencyFormatter.format(order.total)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onViewReceipt,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.view_receipt))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onPickup,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(Res.string.pick_up_order))
            }
        }
    }
}

@Composable
private fun DeliveryOrderItemRow(item: OrderItem) {
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
            Text(
                text = item.itemNameSnapshot,
                style = MaterialTheme.typography.bodyMedium,
            )
            item.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = CurrencyFormatter.formatDecimal(item.itemPriceSnapshot * item.quantity),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

