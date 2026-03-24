package net.marllex.waselak.feature.cashier.pos

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.CartItem
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.PaymentTiming
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import coil3.compose.AsyncImage
import net.marllex.waselak.core.ui.components.waslekLogoPainter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import net.marllex.waselak.core.ui.components.WaselakSearchBar
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.FeatureNotAvailableBottomSheet
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.PlanLimitBottomSheet
import net.marllex.waselak.core.ui.components.WaslekLogo
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import net.marllex.waselak.core.model.Offer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.payment_method_label

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

    // Vendor-type-aware labels
    val newOrderLabel = when (uiState.businessType) {
        "PHARMACY" -> stringResource(Res.string.new_prescription)
        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(Res.string.new_invoice)
        else -> stringResource(Res.string.new_order)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    val logoUrl = uiState.vendorLogoUrl
                    val logoPainter = waslekLogoPainter()
                    if (!logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = logoUrl, contentDescription = null,
                            modifier = Modifier.padding(start = 12.dp).size(36.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentScale = ContentScale.Crop,
                            placeholder = logoPainter,
                            error = logoPainter,
                        )
                    } else {
                        WaslekLogo(modifier = Modifier.padding(start = 12.dp).size(36.dp))
                    }
                },
                title = {
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(uiState.vendorName.ifBlank { newOrderLabel }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(newOrderLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    // Barcode scanner toggle
                    IconButton(onClick = viewModel::toggleBarcodeScanner) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = stringResource(Res.string.barcode_scanner),
                            tint = if (uiState.showBarcodeScanner) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Cart button
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
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
        when {
            uiState.isLoading && uiState.items.isEmpty() -> LoadingIndicator()
            uiState.error != null && uiState.items.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadMenu,
            )
            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) { Column(
                modifier = Modifier.then(if (isTablet) Modifier.widthIn(max = 720.dp) else Modifier.fillMaxWidth()),
            ) {
                // Channel selector – only show enabled channels
                val availableChannels = remember(uiState.enableDineIn, uiState.enableDelivery, uiState.enableTakeaway, uiState.enableInStore, uiState.enablePickupLater) {
                    buildList {
                        if (uiState.enableDineIn) add(OrderChannel.DINE_IN)
                        if (uiState.enableInStore) add(OrderChannel.IN_STORE)
                        if (uiState.enableDelivery) add(OrderChannel.DELIVERY)
                        if (uiState.enableTakeaway) add(OrderChannel.TAKEAWAY)
                        if (uiState.enablePickupLater) add(OrderChannel.PICKUP_LATER)
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
                                        OrderChannel.DINE_IN -> if (uiState.businessType == "PHARMACY") stringResource(Res.string.channel_direct_dispense) else stringResource(Res.string.channel_dine_in)
                                        OrderChannel.DELIVERY -> stringResource(Res.string.channel_delivery)
                                        OrderChannel.TAKEAWAY -> stringResource(Res.string.channel_takeaway)
                                        OrderChannel.IN_STORE -> stringResource(Res.string.channel_in_store)
                                        OrderChannel.PICKUP_LATER -> stringResource(Res.string.channel_pickup_later)
                                    }
                                )
                            }
                        }
                    }
                }

                // ─── Barcode Scanner Panel ─────────────────────────
                if (uiState.showBarcodeScanner) {
                    net.marllex.waselak.core.ui.components.BarcodeScannerView(
                        onBarcodeScanned = viewModel::handleBarcodeScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(horizontal = horizontalPadding),
                        enabled = uiState.showBarcodeScanner,
                    )
                }
                // Barcode scan feedback message
                uiState.barcodeScanMessage?.let { message ->
                    val isSuccess = message.startsWith("✓")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        ),
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isSuccess)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                // ─── Offers Banner Carousel ────────────────────────
                if (uiState.activeOffers.isNotEmpty()) {
                    OffersBannerCarousel(
                        offers = uiState.activeOffers,
                        onOfferClick = { offer -> viewModel.applyOffer(offer) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 4.dp),
                    )
                }

                // ─── Search Bar ──────────────────────────────
                WaselakSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    placeholder = stringResource(Res.string.search_menu),
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
                )

                // ─── Category Sidebar + Items Grid ─────────────────
                val allLabel = stringResource(Res.string.all_categories)
                val allCategories = remember(uiState.categories, allLabel) {
                    listOf<Pair<String?, String>>(null to allLabel) +
                        uiState.categories.map { it.id to it.name }
                }

                val filteredItems = uiState.filteredItems

                // Count items per category for badges
                val itemCountByCategory = remember(uiState.items) {
                    val counts = mutableMapOf<String?, Int>()
                    counts[null] = uiState.items.size // "All" count
                    uiState.items.groupBy { it.categoryId }.forEach { (catId, items) ->
                        counts[catId] = items.size
                    }
                    counts
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 4.dp),
                ) {
                    // ─── Left: Category Sidebar ─────────────────
                    CategorySidebar(
                        categories = allCategories,
                        selectedCategoryId = if (uiState.searchQuery.isNotBlank()) null else uiState.selectedCategoryId,
                        onCategorySelected = viewModel::selectCategory,
                        isTablet = isTablet,
                        itemCountByCategory = itemCountByCategory,
                    )

                    // ─── Right: Items Grid with fade animation ────
                    AnimatedContent(
                        targetState = if (uiState.searchQuery.isNotBlank()) uiState.searchQuery else uiState.selectedCategoryId,
                        transitionSpec = {
                            (fadeIn(tween(250)) + expandVertically(tween(250)))
                                .togetherWith(fadeOut(tween(200)) + shrinkVertically(tween(200)))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "itemsGridAnimation",
                    ) { _ ->
                        val animatedItems = uiState.filteredItems

                        if (animatedItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyView(stringResource(Res.string.no_menu_items))
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = if (isTablet) GridCells.Adaptive(minSize = 150.dp)
                                          else GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    end = horizontalPadding,
                                    top = 4.dp,
                                    bottom = 16.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(animatedItems, key = { it.id }) { item ->
                                    MenuItemGridCard(
                                        item = item,
                                        cartQuantity = uiState.cart.find { it.item.id == item.id }?.quantity ?: 0,
                                        onAdd = { viewModel.addToCart(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            } }
        }
        }

        if (showCartSheet && uiState.cart.isNotEmpty()) {
            CartBottomSheet(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { showCartSheet = false },
                onOrderCreated = onOrderCreated,
            )
        }

        // Variant selector dialog
        val variantItem = uiState.variantSelectorItem
        if (variantItem != null) {
            VariantSelectorDialog(
                item = variantItem,
                selections = uiState.variantSelections,
                onSelect = { groupId, groupName, optionName, priceAdj ->
                    viewModel.selectVariantOption(groupId, groupName, optionName, priceAdj)
                },
                onConfirm = viewModel::confirmVariantSelection,
                onDismiss = viewModel::dismissVariantSelector,
            )
        }

        // Plan limit dialog
        if (uiState.showPlanLimitDialog) {
            PlanLimitBottomSheet(
                message = uiState.planLimitMessage,
                onDismiss = viewModel::dismissPlanLimitDialog,
            )
        }

        // Feature not available dialog
        if (uiState.showFeatureNotAvailable) {
            FeatureNotAvailableBottomSheet(
                message = uiState.featureNotAvailableMessage,
                onDismiss = viewModel::dismissFeatureNotAvailable,
            )
        }

        // Manager PIN approval dialog
        if (uiState.showPinDialog) {
            var pinInput by remember { mutableStateOf("") }
            // Get list of manager workers to show as options
            // For simplicity, ask for worker ID + PIN in one dialog
            androidx.compose.material3.AlertDialog(
                onDismissRequest = viewModel::dismissPinDialog,
                title = {
                    Text(
                        text = stringResource(Res.string.pin_required_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.pin_required_message))
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                            label = { Text(stringResource(Res.string.manager_pin)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            isError = uiState.pinError != null,
                            supportingText = uiState.pinError?.let { err -> { Text(err) } },
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.verifyManagerPin(pinInput) },
                        enabled = pinInput.length >= 4,
                    ) {
                        Text(stringResource(Res.string.verify_pin))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = viewModel::dismissPinDialog) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }

        // Offline confirmation dialog
        if (uiState.showOfflineConfirmation) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = viewModel::declineOfflineMode,
                title = {
                    Text(
                        text = stringResource(Res.string.offline_confirm_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(stringResource(Res.string.offline_confirm_message))
                },
                confirmButton = {
                    Button(onClick = viewModel::confirmOfflineMode) {
                        Text(stringResource(Res.string.continue_offline))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = viewModel::declineOfflineMode) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }
    } // BoxWithConstraints
}

@Composable
private fun CategorySidebar(
    categories: List<Pair<String?, String>>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    isTablet: Boolean,
    itemCountByCategory: Map<String?, Int> = emptyMap(),
) {
    val sidebarWidth = if (isTablet) 110.dp else 92.dp
    val categoryListState = rememberLazyListState()

    val selectedIndex = remember(selectedCategoryId, categories) {
        categories.indexOfFirst { it.first == selectedCategoryId }.coerceAtLeast(0)
    }
    LaunchedEffect(selectedIndex) {
        categoryListState.animateScrollToItem(selectedIndex)
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
    ) {
        LazyColumn(
            state = categoryListState,
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                items = categories,
                key = { it.first ?: "__all__" },
            ) { (categoryId, categoryName) ->
                val isSelected = categoryId == selectedCategoryId
                val count = itemCountByCategory[categoryId]

                CategorySidebarItem(
                    name = categoryName,
                    isSelected = isSelected,
                    itemCount = count,
                    onClick = { onCategorySelected(categoryId) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySidebarItem(
    name: String,
    isSelected: Boolean,
    itemCount: Int? = null,
    onClick: () -> Unit,
) {
    // Animated colors
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(300),
        label = "categoryBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "categoryContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        animationSpec = tween(300),
        label = "categoryBorder",
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "categoryElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "categoryScale",
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            ),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        contentColor = contentColor,
        shadowElevation = elevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (itemCount != null && itemCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "$itemCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuItemGridCard(item: Item, cartQuantity: Int, onAdd: () -> Unit) {
    val isInCart = cartQuantity > 0

    // Animated properties
    val cardBgColor by animateColorAsState(
        targetValue = if (isInCart)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(350),
        label = "itemCardBg",
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isInCart)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        animationSpec = tween(350),
        label = "itemCardBorder",
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isInCart) 4.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "itemCardElevation",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isInCart) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "itemCardScale",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(onClick = onAdd),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = BorderStroke(
            width = if (isInCart) 1.5.dp else 0.5.dp,
            color = cardBorderColor,
        ),
    ) {
        Column {
            // Image section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                val itemLogoPainter = waslekLogoPainter()
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = itemLogoPainter,
                        error = itemLogoPainter,
                    )
                } else {
                    Image(
                        painter = itemLogoPainter,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Crop,
                        alpha = 0.35f,
                    )
                }

                // Cart quantity badge with scale animation
                if (isInCart) {
                    val badgeScale by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "badgeScale",
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = cartQuantity,
                                transitionSpec = {
                                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                        .togetherWith(scaleOut() + fadeOut())
                                },
                                label = "qtyAnimation",
                            ) { qty ->
                                Text(
                                    text = "$qty",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Item info
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = CurrencyFormatter.formatDecimal(item.price),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (item.variantGroups.isNotEmpty()) {
                        Text(
                            text = "+${stringResource(Res.string.select_options)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
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
    var selectedTiming by remember { mutableStateOf(
        if (uiState.channel == OrderChannel.DINE_IN) PaymentTiming.PAY_LATER else PaymentTiming.PAY_NOW
    ) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    val isDeliveryOrTakeaway = uiState.channel in listOf(OrderChannel.DELIVERY, OrderChannel.TAKEAWAY, OrderChannel.PICKUP_LATER)
    var expandManualDiscount by remember { mutableStateOf(false) }
    var expandLoyaltyPoints by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.order_summary),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (uiState.cart.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.clearOrder()
                            onDismiss()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(Res.string.clear_cart),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(uiState.cart) { index, cartItem ->
                CartItemRow(
                    cartItem = cartItem,
                    onIncrease = { viewModel.updateCartQuantity(index, cartItem.quantity + 1) },
                    onDecrease = { viewModel.updateCartQuantity(index, cartItem.quantity - 1) },
                    onRemove = { viewModel.removeFromCart(index) },
                )
            }

            // ─── Applied Offer Card (with remove button) ──────────────────────
            if (uiState.appliedOffer != null) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(Res.string.applied_offer_label, uiState.appliedOffer!!.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearAppliedOffer() },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(Res.string.remove_offer),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ─── Manual Discount Section (Collapsible) ─────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val manualDiscountActive = viewModel.getManualDiscount() > 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (manualDiscountActive)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandManualDiscount = !expandManualDiscount }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.manual_discount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (manualDiscountActive) {
                                    Text(
                                        text = "- ${CurrencyFormatter.formatDecimal(viewModel.getManualDiscount())}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (expandManualDiscount) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        AnimatedVisibility(
                            visible = expandManualDiscount,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Type toggle: Fixed / Percent
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.width(160.dp)) {
                                        listOf("FIXED" to Res.string.discount_type_fixed, "PERCENT" to Res.string.discount_type_percent).forEachIndexed { index, (type, labelRes) ->
                                            SegmentedButton(
                                                selected = uiState.manualDiscountType == type,
                                                onClick = { viewModel.setManualDiscountType(type) },
                                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                            ) { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = uiState.manualDiscountValue,
                                        onValueChange = viewModel::setManualDiscountValue,
                                        label = { Text(stringResource(Res.string.discount_value)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = uiState.manualDiscountReason,
                                    onValueChange = viewModel::setManualDiscountReason,
                                    label = { Text(stringResource(Res.string.discount_reason_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = viewModel::applyManualDiscount,
                                        modifier = Modifier.weight(1f),
                                        enabled = uiState.manualDiscountValue.toDoubleOrNull()?.let { it > 0 } == true,
                                    ) {
                                        if (uiState.pinApproved) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(Res.string.pin_approved))
                                        } else {
                                            Text(stringResource(Res.string.apply_discount))
                                        }
                                    }
                                    if (manualDiscountActive) {
                                        androidx.compose.material3.TextButton(
                                            onClick = viewModel::clearManualDiscount,
                                        ) { Text(stringResource(Res.string.clear_discount)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Loyalty Points Section (Collapsible) ──────────────────────────
            if (uiState.loyaltyEnabled && uiState.selectedCustomer != null) {
                val pointsBalance = uiState.selectedCustomer.pointsBalance
                if (pointsBalance > 0) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        val pointsApplied = uiState.pointsToRedeem > 0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (pointsApplied)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandLoyaltyPoints = !expandLoyaltyPoints }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.loyalty_points_section),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (pointsApplied) {
                                            Text(
                                                text = "- ${CurrencyFormatter.formatDecimal(viewModel.getPointsDiscount())}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(Res.string.points_available, pointsBalance),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (expandLoyaltyPoints) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = expandLoyaltyPoints,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                                        if (pointsApplied) {
                                            // Points applied — show confirmation with clear option
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = stringResource(Res.string.points_applied_label, uiState.pointsToRedeem),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                    Text(
                                                        text = stringResource(Res.string.points_discount_value, CurrencyFormatter.formatDecimal(viewModel.getPointsDiscount())),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                androidx.compose.material3.TextButton(
                                                    onClick = viewModel::clearPointsRedemption,
                                                ) { Text(stringResource(Res.string.clear_points)) }
                                            }
                                        } else if (pointsBalance >= uiState.minPointsRedeem) {
                                            // Points available — show apply button
                                            val discountPreview = pointsBalance * uiState.pointsRedeemRate
                                            Button(
                                                onClick = viewModel::applyAllPoints,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Text(
                                                    stringResource(
                                                        Res.string.apply_all_points,
                                                        pointsBalance,
                                                        CurrencyFormatter.formatDecimal(discountPreview),
                                                    ),
                                                )
                                            }
                                        } else {
                                            // Not enough points
                                            Text(
                                                text = stringResource(Res.string.min_points_hint, uiState.minPointsRedeem),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Price Breakdown ─────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Subtotal", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = CurrencyFormatter.formatDecimal(viewModel.getSubtotal()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                // Detailed discount breakdown
                val offerDiscount = viewModel.getOfferDiscount()
                val manualDiscount = viewModel.getManualDiscount()
                val pointsDiscount = viewModel.getPointsDiscount()
                val totalDiscount = viewModel.getDiscountAmount()

                if (offerDiscount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.offer_discount_row),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(Res.string.remove_offer),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp).clickable { viewModel.clearAppliedOffer() },
                            )
                        }
                        Text(
                            text = "- ${CurrencyFormatter.formatDecimal(offerDiscount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (manualDiscount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.manual_discount_row),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "- ${CurrencyFormatter.formatDecimal(manualDiscount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (pointsDiscount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.points_discount_row),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "- ${CurrencyFormatter.formatDecimal(pointsDiscount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (totalDiscount > 0) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.total_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = CurrencyFormatter.formatDecimal(viewModel.getTotal()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Customer phone field — shown for all channels (required for DELIVERY/TAKEAWAY, optional for DINE_IN)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val isPhoneError = hasAttemptedSubmit && isDeliveryOrTakeaway && uiState.clientPhone.isBlank()
                val phoneLabel = stringResource(Res.string.customer_phone) +
                    if (!isDeliveryOrTakeaway) " (${stringResource(Res.string.optional_hint)})" else ""
                OutlinedTextField(
                    value = uiState.clientPhone, onValueChange = viewModel::setClientPhone,
                    label = { Text(phoneLabel) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    isError = isPhoneError,
                    supportingText = if (isPhoneError) {{ Text(stringResource(Res.string.phone_required)) }} else null,
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

            // Phone autocomplete suggestions — shown as inline cards (no popup, no focus stealing)
            if (uiState.showPhoneDropdown && uiState.phoneSearchResults.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            uiState.phoneSearchResults.take(5).forEach { customer ->
                                val displayName = customer.name.orEmpty()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectCustomerFromDropdown(customer) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(customer.phone, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (displayName.isNotBlank()) {
                                            Text(displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                            // Show saved addresses in customer card
                            if (uiState.customerAddresses.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(6.dp))
                                uiState.customerAddresses.forEach { addr ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                        )
                                        Text(
                                            text = buildString {
                                                val lbl = addr.label
                                                if (!lbl.isNullOrBlank()) append("$lbl: ")
                                                append(addr.address)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Client name field (optional for all channels)
            item {
                val nameLabel = stringResource(Res.string.client_name) +
                    if (!isDeliveryOrTakeaway) " (${stringResource(Res.string.optional_hint)})" else ""
                OutlinedTextField(
                    value = uiState.clientName, onValueChange = viewModel::setClientName,
                    label = { Text(nameLabel) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Delivery-only fields: address, address suggestions, tax place, recent orders
            if (isDeliveryOrTakeaway) {
                // Delivery address field — required for DELIVERY, optional for TAKEAWAY
                if (uiState.channel == OrderChannel.DELIVERY) {
                    item {
                        val isAddressError = hasAttemptedSubmit && uiState.clientAddress.isBlank()
                        OutlinedTextField(
                            value = uiState.clientAddress, onValueChange = viewModel::setClientAddress,
                            label = { Text(stringResource(Res.string.client_address)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = isAddressError,
                            supportingText = if (isAddressError) {{ Text(stringResource(Res.string.address_required)) }} else null,
                        )
                    }

                    // Address suggestions — show as clickable inline list when customer has saved addresses
                    if (uiState.customerAddresses.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(Res.string.select_address),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                                    )
                                    uiState.customerAddresses.forEach { addr ->
                                        val isSelected = uiState.selectedAddressId == addr.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                                    else Modifier
                                                )
                                                .clickable { viewModel.selectCustomerAddress(addr.id) }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.LocationOn,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                val addrLabel = addr.label
                                                if (!addrLabel.isNullOrBlank()) {
                                                    Text(
                                                        text = addrLabel,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                                Text(
                                                    text = addr.address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
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
                        RecentOrderCard(order = order)
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

            // Scheduled pickup date/time — only for PICKUP_LATER
            if (uiState.channel == OrderChannel.PICKUP_LATER) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(Res.string.scheduled_pickup_time), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))

                    var showDatePicker by remember { mutableStateOf(false) }
                    var showTimePicker by remember { mutableStateOf(false) }
                    var selectedDateMillis by remember { mutableLongStateOf(uiState.scheduledFor ?: 0L) }
                    var selectedHour by remember { mutableIntStateOf(12) }
                    var selectedMinute by remember { mutableIntStateOf(0) }

                    // Display selected date/time or prompt to pick
                    if (uiState.scheduledFor != null) {
                        val formatted = remember(uiState.scheduledFor) {
                            val instant = Instant.fromEpochMilliseconds(uiState.scheduledFor!!)
                            val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                            "${local.dayOfMonth}/${local.monthNumber}/${local.year} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
                        }
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showDatePicker = true },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(Res.string.scheduled_for_label, formatted), style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(onClick = { viewModel.setScheduledFor(null) }) {
                                    Text(stringResource(Res.string.clear_discount))
                                }
                            }
                        }
                    } else {
                        val isScheduleError = hasAttemptedSubmit && uiState.scheduledFor == null
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isScheduleError) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors(),
                            border = if (isScheduleError) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonBorder(true),
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.select_date) + " & " + stringResource(Res.string.select_time))
                        }
                        if (isScheduleError) {
                            Text(
                                stringResource(Res.string.scheduled_time_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                            )
                        }
                    }

                    // Date picker dialog
                    if (showDatePicker) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = if (selectedDateMillis > 0) selectedDateMillis else null,
                            selectableDates = object : SelectableDates {
                                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                                    return utcTimeMillis >= System.currentTimeMillis() - 86_400_000 // today or future
                                }
                            },
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        selectedDateMillis = millis
                                        showDatePicker = false
                                        showTimePicker = true
                                    }
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.cancel)) }
                            },
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }

                    // Time picker dialog
                    if (showTimePicker) {
                        val timePickerState = rememberTimePickerState(
                            initialHour = selectedHour,
                            initialMinute = selectedMinute,
                        )
                        AlertDialog(
                            onDismissRequest = { showTimePicker = false },
                            title = { Text(stringResource(Res.string.select_time)) },
                            text = { TimePicker(state = timePickerState) },
                            confirmButton = {
                                TextButton(onClick = {
                                    selectedHour = timePickerState.hour
                                    selectedMinute = timePickerState.minute
                                    // Combine date + time into epoch millis
                                    val dateInstant = Instant.fromEpochMilliseconds(selectedDateMillis)
                                    val tz = TimeZone.currentSystemDefault()
                                    val localDate = dateInstant.toLocalDateTime(tz).date
                                    val combined = kotlinx.datetime.LocalDateTime(localDate, kotlinx.datetime.LocalTime(selectedHour, selectedMinute))
                                    val epochMs = combined.toInstant(tz).toEpochMilliseconds()
                                    viewModel.setScheduledFor(epochMs)
                                    showTimePicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(Res.string.cancel)) }
                            },
                        )
                    }
                }
            }

            // Doctor info (pharmacy only)
            if (uiState.businessType == "PHARMACY") {
                item {
                    Text(stringResource(Res.string.doctor_name), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = uiState.doctorName,
                        onValueChange = viewModel::updateDoctorName,
                        placeholder = { Text(stringResource(Res.string.optional_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(Res.string.diagnosis), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = uiState.diagnosis,
                        onValueChange = viewModel::updateDiagnosis,
                        placeholder = { Text(stringResource(Res.string.optional_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            // Payment timing: Pay Now / Pay Later
            item {
                Text(stringResource(Res.string.payment_timing), style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PaymentTiming.entries.forEachIndexed { index, timing ->
                        SegmentedButton(
                            selected = selectedTiming == timing,
                            onClick = { selectedTiming = timing },
                            shape = SegmentedButtonDefaults.itemShape(index, PaymentTiming.entries.size),
                        ) {
                            Text(when (timing) {
                                PaymentTiming.PAY_NOW -> stringResource(Res.string.pay_now)
                                PaymentTiming.PAY_LATER -> stringResource(Res.string.pay_later)
                            })
                        }
                    }
                }
            }

            // Payment method (hidden when PAY_LATER)
            if (selectedTiming == PaymentTiming.PAY_NOW) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    val selectableMethods = PaymentMethod.entries.filter {
                        it != PaymentMethod.SPLIT && (it != PaymentMethod.CREDIT || uiState.businessType == "PHARMACY")
                    }
                    Text(stringResource(Res.string.payment_method_label), style = MaterialTheme.typography.labelMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        selectableMethods.forEachIndexed { index, method ->
                            SegmentedButton(
                                selected = selectedPayment == method,
                                onClick = { selectedPayment = method },
                                shape = SegmentedButtonDefaults.itemShape(index, selectableMethods.size),
                            ) {
                                Text(when (method) {
                                    PaymentMethod.CASH -> stringResource(Res.string.payment_cash)
                                    PaymentMethod.WALLET -> stringResource(Res.string.payment_wallet)
                                    PaymentMethod.CARD -> stringResource(Res.string.payment_card)
                                    PaymentMethod.CREDIT -> stringResource(Res.string.payment_credit)
                                    else -> method.name
                                })
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        hasAttemptedSubmit = true
                        if (uiState.canSubmit) {
                            viewModel.submitOrder(selectedPayment, selectedTiming) { onDismiss(); onOrderCreated(it) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSubmitting && uiState.cart.isNotEmpty(),
                ) {
                    Text(if (uiState.isSubmitting) {
                        when (uiState.businessType) {
                            "PHARMACY" -> stringResource(Res.string.placing_prescription)
                            "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(Res.string.placing_invoice)
                            else -> stringResource(Res.string.placing_order)
                        }
                    } else when (uiState.businessType) {
                        "PHARMACY" -> stringResource(Res.string.place_prescription, CurrencyFormatter.formatDecimal(viewModel.getTotal()))
                        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(Res.string.place_invoice, CurrencyFormatter.formatDecimal(viewModel.getTotal()))
                        else -> stringResource(Res.string.place_order, CurrencyFormatter.formatDecimal(viewModel.getTotal()))
                    }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Drug Interaction Warning
    if (uiState.showDrugInteractionWarning && uiState.drugInteractionResult != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDrugInteractionWarning,
            title = { Text(stringResource(Res.string.drug_interaction_warning_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.drug_interaction_warning_message))
                    uiState.drugInteractionResult!!.interactions.forEach { interaction ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when (interaction.severity) {
                                    "SEVERE", "CONTRAINDICATED" -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "${interaction.itemNameA ?: ""} \u2194 ${interaction.itemNameB ?: ""}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(interaction.description, style = MaterialTheme.typography.bodySmall)
                                interaction.recommendation?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissDrugInteractionWarning()
                        // Re-submit after user acknowledged
                        viewModel.submitOrder(selectedPayment, selectedTiming) { onDismiss(); onOrderCreated(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.drug_interaction_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDrugInteractionWarning) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RecentOrderCard(order: Order) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Header row: channel badge + total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Channel chip
                val channelLabel = when (order.channel) {
                    OrderChannel.DINE_IN -> "Dine-in"
                    OrderChannel.DELIVERY -> "Delivery"
                    OrderChannel.TAKEAWAY -> "Takeaway"
                    OrderChannel.IN_STORE -> "In-Store"
                    OrderChannel.PICKUP_LATER -> "Pickup Later"
                }
                Badge(
                    containerColor = when (order.channel) {
                        OrderChannel.DINE_IN -> MaterialTheme.colorScheme.primaryContainer
                        OrderChannel.DELIVERY -> MaterialTheme.colorScheme.tertiaryContainer
                        OrderChannel.TAKEAWAY -> MaterialTheme.colorScheme.secondaryContainer
                        OrderChannel.IN_STORE -> MaterialTheme.colorScheme.primaryContainer
                        OrderChannel.PICKUP_LATER -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = when (order.channel) {
                        OrderChannel.DINE_IN -> MaterialTheme.colorScheme.onPrimaryContainer
                        OrderChannel.DELIVERY -> MaterialTheme.colorScheme.onTertiaryContainer
                        OrderChannel.TAKEAWAY -> MaterialTheme.colorScheme.onSecondaryContainer
                        OrderChannel.IN_STORE -> MaterialTheme.colorScheme.onPrimaryContainer
                        OrderChannel.PICKUP_LATER -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                ) {
                    Text(
                        channelLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = CurrencyFormatter.formatDecimal(order.total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Item list
            order.items.forEach { item ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${item.quantity}× ${item.itemNameSnapshot}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = CurrencyFormatter.formatDecimal(item.itemPriceSnapshot * item.quantity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    net.marllex.waselak.core.ui.util.VariantDisplayHelper.formatVariantSummary(item.variantOptionsSnapshot)?.let { summary ->
                        Text(text = summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }

            // Address (if delivery/takeaway)
            val address = order.clientAddress
            if (!address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
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
            if (cartItem.variantSelections.isNotEmpty()) {
                Text(
                    text = cartItem.variantSelections.joinToString(", ") { "${it.groupName}: ${it.optionName}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = "${CurrencyFormatter.formatDecimal(cartItem.unitPrice)} each",
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
            text = CurrencyFormatter.formatDecimal(cartItem.totalPrice),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun VariantSelectorDialog(
    item: Item,
    selections: Map<String, net.marllex.waselak.core.model.VariantSelection>,
    onSelect: (groupId: String, groupName: String, optionName: String, priceAdjustment: Double) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalPrice = item.price + selections.values.sumOf { it.priceAdjustment }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.variantGroups.forEach { group ->
                    Text(
                        text = group.name + if (group.required) " *" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(group.options) { option ->
                            val isSelected = selections[group.id]?.optionName == option.name
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelect(group.id, group.name, option.name, option.priceAdjustment) },
                                label = {
                                    Text(buildString {
                                        append(option.name)
                                        if (option.priceAdjustment != 0.0) {
                                            append(" (+${CurrencyFormatter.formatDecimal(option.priceAdjustment)})")
                                        }
                                    })
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.price), style = MaterialTheme.typography.titleMedium)
                    Text(
                        CurrencyFormatter.formatDecimal(totalPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            val hasAllRequired = item.variantGroups.filter { it.required }.all { group ->
                selections.containsKey(group.id)
            }
            Button(
                onClick = onConfirm,
                enabled = hasAllRequired,
            ) {
                Text(stringResource(Res.string.confirm_add, CurrencyFormatter.formatDecimal(totalPrice)))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

// ─── Offers Banner Carousel ──────────────────────────────────────────

private val offerGradients = listOf(
    listOf(Color(0xFF667eea), Color(0xFF764ba2)),
    listOf(Color(0xFFf093fb), Color(0xFFf5576c)),
    listOf(Color(0xFF4facfe), Color(0xFF00f2fe)),
    listOf(Color(0xFF43e97b), Color(0xFF38f9d7)),
    listOf(Color(0xFFfa709a), Color(0xFFfee140)),
    listOf(Color(0xFFa18cd1), Color(0xFFfbc2eb)),
)

@Composable
private fun OffersBannerCarousel(
    offers: List<Offer>,
    onOfferClick: (Offer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { offers.size })

    // Auto-advance every 4 seconds
    LaunchedEffect(offers.size) {
        if (offers.size > 1) {
            while (true) {
                delay(4000)
                val nextPage = (pagerState.currentPage + 1) % offers.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(130.dp),
            pageSpacing = 8.dp,
        ) { page ->
            val offer = offers[page]
            OfferBannerCard(
                offer = offer,
                gradientIndex = page % offerGradients.size,
                onClick = { onOfferClick(offer) },
            )
        }

        // Page indicator dots
        if (offers.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(offers.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferBannerCard(
    offer: Offer,
    gradientIndex: Int,
    onClick: () -> Unit,
) {
    val gradient = offerGradients[gradientIndex]

    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background: image or gradient
            if (!offer.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = offer.imageUrl,
                    contentDescription = offer.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Dark scrim over image for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradient)),
                )
            }

            // Content overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Offer name
                Text(
                    text = offer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Description (if any)
                val desc = offer.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Discount badge
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                        ),
                    ) {
                        Text(
                            text = when (offer.discountType) {
                                "FIXED_PRICE" -> stringResource(
                                    Res.string.offer_discount_fixed,
                                    CurrencyFormatter.formatDecimal(offer.discountValue),
                                )
                                "PERCENT" -> stringResource(
                                    Res.string.offer_discount_percent,
                                    "${offer.discountValue.toInt()}%",
                                )
                                else -> ""
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }

                    // Items count
                    Text(
                        text = stringResource(Res.string.offer_banner_items, offer.items.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
