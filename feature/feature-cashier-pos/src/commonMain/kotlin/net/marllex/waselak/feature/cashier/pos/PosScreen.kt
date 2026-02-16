package net.marllex.waselak.feature.cashier.pos

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.cashier.pos.generated.resources.Res
import net.marllex.waselak.feature.cashier.pos.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.CartItem
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.PaymentMethod
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    onOrderCreated: (Order) -> Unit = {},
    viewModel: PosViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCartSheet by remember { mutableStateOf(false) }

    BoxWithConstraints {
    val isTablet = maxWidth >= 600.dp
    val horizontalPadding = if (isTablet) 24.dp else 16.dp

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
                        Box(Modifier.padding(start = 12.dp).size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Store, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                title = {
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(uiState.vendorName.ifBlank { stringResource(Res.string.new_order) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(Res.string.new_order), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = { showCartSheet = true }) {
                        BadgedBox(badge = {
                            if (uiState.cart.isNotEmpty()) {
                                Badge { Text("${uiState.cart.sumOf { it.quantity }}") }
                            }
                        }) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.items.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadMenu,
            )
            else -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) { Column(
                modifier = Modifier.then(if (isTablet) Modifier.widthIn(max = 720.dp) else Modifier.fillMaxWidth()),
            ) {
                // Channel selector – only show enabled channels
                val availableChannels = remember(uiState.enableDineIn, uiState.enableDelivery, uiState.enableTakeaway) {
                    buildList {
                        if (uiState.enableDineIn) add(OrderChannel.DINE_IN)
                        if (uiState.enableDelivery) add(OrderChannel.DELIVERY)
                        if (uiState.enableTakeaway) add(OrderChannel.TAKEAWAY)
                        if (isEmpty()) addAll(OrderChannel.entries) // fallback: show all
                    }
                }
                if (availableChannels.size > 1) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 8.dp),
                    ) {
                        availableChannels.forEachIndexed { index, channel ->
                            SegmentedButton(
                                selected = uiState.channel == channel,
                                onClick = { viewModel.setChannel(channel) },
                                shape = SegmentedButtonDefaults.itemShape(index, availableChannels.size),
                            ) {
                                Text(
                                    when (channel) {
                                        OrderChannel.DINE_IN -> stringResource(Res.string.channel_dine_in)
                                        OrderChannel.DELIVERY -> stringResource(Res.string.channel_delivery)
                                        OrderChannel.TAKEAWAY -> stringResource(Res.string.channel_takeaway)
                                    }
                                )
                            }
                        }
                    }
                }

                // Category filter
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCategoryId == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                    items(uiState.categories) { cat ->
                        FilterChip(
                            selected = uiState.selectedCategoryId == cat.id,
                            onClick = { viewModel.selectCategory(cat.id) },
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }

                // Items grid
                val filteredItems = if (uiState.selectedCategoryId != null) {
                    uiState.items.filter { it.categoryId == uiState.selectedCategoryId }
                } else uiState.items

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        MenuItemCard(
                            item = item,
                            cartQuantity = uiState.cart.find { it.item.id == item.id }?.quantity ?: 0,
                            onAdd = { viewModel.addToCart(item) },
                        )
                    }
                }
            } }
        }

        if (showCartSheet && uiState.cart.isNotEmpty()) {
            CartBottomSheet(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { showCartSheet = false },
                onOrderCreated = onOrderCreated,
            )
        }
    }
    } // BoxWithConstraints
}

@Composable
private fun MenuItemCard(item: Item, cartQuantity: Int, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = if (cartQuantity > 0) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ) else CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.titleSmall)
                item.description?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = CurrencyFormatter.formatDecimal(item.price),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (cartQuantity > 0) {
                Text(
                    text = "x$cartQuantity",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.add_to_cart))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartBottomSheet(
    uiState: PosViewModel.UiState,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onOrderCreated: (Order) -> Unit,
) {
    var selectedPayment by remember { mutableStateOf(PaymentMethod.CASH) }
    val isDeliveryOrTakeaway = uiState.channel == OrderChannel.DELIVERY || uiState.channel == OrderChannel.TAKEAWAY

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Order Summary", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(uiState.cart) { cartItem ->
                CartItemRow(
                    cartItem = cartItem,
                    onIncrease = { viewModel.updateCartQuantity(cartItem.item.id, cartItem.quantity + 1) },
                    onDecrease = { viewModel.updateCartQuantity(cartItem.item.id, cartItem.quantity - 1) },
                    onRemove = { viewModel.removeFromCart(cartItem.item.id) },
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Subtotal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = CurrencyFormatter.formatDecimal(viewModel.getSubtotal()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Customer phone field — shown for both DELIVERY and TAKEAWAY
            if (isDeliveryOrTakeaway) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.clientPhone, onValueChange = viewModel::setClientPhone,
                        label = { Text(stringResource(Res.string.customer_phone)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            when {
                                uiState.isLookingUpCustomer -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                uiState.customerLookupDone && uiState.selectedCustomer != null -> Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = stringResource(Res.string.customer_found),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }

                // Customer info card when found
                if (uiState.selectedCustomer != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uiState.selectedCustomer.name ?: uiState.selectedCustomer.phone,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(Res.string.customer_found),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                if (uiState.selectedCustomer.orderCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text("${uiState.selectedCustomer.orderCount}")
                                    }
                                }
                            }
                        }
                    }
                }

                // Client name field
                item {
                    OutlinedTextField(
                        value = uiState.clientName, onValueChange = viewModel::setClientName,
                        label = { Text(stringResource(Res.string.client_name)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Address picker — only for DELIVERY channel with saved addresses
                if (uiState.channel == OrderChannel.DELIVERY && uiState.customerAddresses.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(Res.string.select_address), style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.customerAddresses) { addr ->
                                FilterChip(
                                    selected = uiState.selectedAddressId == addr.id,
                                    onClick = { viewModel.selectCustomerAddress(addr.id) },
                                    label = {
                                        Text(
                                            addr.label ?: addr.address.take(25),
                                            maxLines = 1,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(),
                                )
                            }
                        }
                    }
                }

                // Delivery address field — required for DELIVERY, optional for TAKEAWAY
                item {
                    OutlinedTextField(
                        value = uiState.clientAddress, onValueChange = viewModel::setClientAddress,
                        label = { Text(stringResource(Res.string.client_address)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Tax place selector — only for DELIVERY
                if (uiState.channel == OrderChannel.DELIVERY && uiState.taxPlaces.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(Res.string.delivery_fee_label), style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.taxPlaces) { place ->
                                FilterChip(
                                    selected = uiState.selectedTaxPlaceId == place.id,
                                    onClick = { viewModel.setSelectedTaxPlaceId(place.id) },
                                    label = { Text("${place.name} (+${place.taxPercent} EGP)") },
                                    colors = FilterChipDefaults.filterChipColors(),
                                )
                            }
                        }
                    }
                }

                // Recent orders section
                if (uiState.recentOrders.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(Res.string.recent_orders),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(uiState.recentOrders) { order ->
                        RecentOrderCard(
                            order = order,
                            onReorder = { viewModel.reorderFromHistory(order) },
                        )
                    }
                }
            }

            // Table selector for dine-in (only if tables are enabled)
            if (uiState.enableTables && uiState.channel == OrderChannel.DINE_IN && uiState.tables.isNotEmpty()) {
                item {
                    Text(text = stringResource(Res.string.select_table), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.tables) { table ->
                            FilterChip(
                                selected = uiState.selectedTableId == table.id,
                                onClick = { viewModel.setTableId(table.id) },
                                label = { Text("T${table.number}") },
                                colors = FilterChipDefaults.filterChipColors(),
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.notes, onValueChange = viewModel::setNotes,
                    label = { Text(stringResource(Res.string.notes_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Payment method
            item {
                Text("Payment Method", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PaymentMethod.entries.forEachIndexed { index, method ->
                        SegmentedButton(
                            selected = selectedPayment == method,
                            onClick = { selectedPayment = method },
                            shape = SegmentedButtonDefaults.itemShape(index, PaymentMethod.entries.size),
                        ) {
                            Text(method.name)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.submitOrder(selectedPayment) { onOrderCreated(it); onDismiss() } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSubmitting && uiState.cart.isNotEmpty(),
                ) {
                    Text(if (uiState.isSubmitting) {
                        stringResource(Res.string.placing_order)
                    } else stringResource(
                        Res.string.place_order,
                        CurrencyFormatter.formatDecimal(viewModel.getSubtotal())
                    )
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RecentOrderCard(
    order: Order,
    onReorder: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.order_items_count, order.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = CurrencyFormatter.formatDecimal(order.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onReorder,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(Res.string.reorder),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun CartItemRow(
    cartItem: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = cartItem.item.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${CurrencyFormatter.formatDecimal(cartItem.item.price)} each",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDecrease) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text(text = "${cartItem.quantity}", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = CurrencyFormatter.formatDecimal(cartItem.item.price * cartItem.quantity),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}
