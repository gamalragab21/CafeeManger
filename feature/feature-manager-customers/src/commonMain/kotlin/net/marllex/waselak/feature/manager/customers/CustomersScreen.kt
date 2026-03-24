package net.marllex.waselak.feature.manager.customers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.common.extensions.formatEpochMs
import net.marllex.waselak.core.common.utils.CurrencyFormatter
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.CustomerAddress
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.PointsTransaction
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.FeatureNotAvailableView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.theme.*
import net.marllex.waselak.feature.manager.customers.generated.resources.Res
import net.marllex.waselak.feature.manager.customers.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: CustomersViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.customers),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::loadCustomers,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        when {
            uiState.showFeatureNotAvailable -> FeatureNotAvailableView(
                message = uiState.featureNotAvailableMessage,
            )
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.customers.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadCustomers,
            )
            else -> BoxWithConstraints(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                val isTablet = maxWidth >= 600.dp

                if (isTablet) {
                    TabletLayout(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                } else {
                    PhoneLayout(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                }
            }
        }

    }
}

// ═══════════════════════════════════════════════════════════════════
// Phone Layout (single column)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneLayout(
    uiState: CustomersViewModel.UiState,
    viewModel: CustomersViewModel,
) {
    var showDetailDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = viewModel::search,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Sort chips
        SortChipsRow(
            selectedSort = uiState.sortBy,
            onSortSelected = viewModel::setSortBy,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Loyalty filter chips
        LoyaltyFilterChipsRow(
            selectedFilter = uiState.loyaltyFilter,
            onFilterSelected = viewModel::setLoyaltyFilter,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Customer list with pull-to-refresh
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (uiState.customers.isEmpty()) {
                EmptyCustomersView()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.customers, key = { it.id }) { customer ->
                        CustomerCard(
                            customer = customer,
                            isSelected = uiState.selectedCustomer?.id == customer.id,
                            onClick = {
                                viewModel.selectCustomer(customer)
                                showDetailDialog = true
                            },
                        )
                    }
                }
            }
        }
    }

    // Detail bottom sheet (phone mode)
    if (showDetailDialog && uiState.selectedCustomer != null) {
        CustomerDetailBottomSheet(
            customer = uiState.selectedCustomer!!,
            orders = uiState.selectedCustomerOrders,
            pointsHistory = uiState.pointsHistory,
            discountOrders = uiState.discountOrders,
            isLoadingPointsHistory = uiState.isLoadingPointsHistory,
            isLoadingDiscountOrders = uiState.isLoadingDiscountOrders,
            onDismiss = {
                showDetailDialog = false
                viewModel.selectCustomer(null)
            },
            onDelete = {
                showDeleteDialog = true
            },
        )
    }

    // Delete confirmation bottom sheet
    if (showDeleteDialog && uiState.selectedCustomer != null) {
        DeleteCustomerBottomSheet(
            onConfirm = {
                viewModel.deleteCustomer(uiState.selectedCustomer!!.id)
                showDeleteDialog = false
                showDetailDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Tablet Layout (two columns: list + detail)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletLayout(
    uiState: CustomersViewModel.UiState,
    viewModel: CustomersViewModel,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left: Customer list
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::search,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            SortChipsRow(
                selectedSort = uiState.sortBy,
                onSortSelected = viewModel::setSortBy,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Loyalty filter chips
            LoyaltyFilterChipsRow(
                selectedFilter = uiState.loyaltyFilter,
                onFilterSelected = viewModel::setLoyaltyFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.customers.isEmpty()) {
                    EmptyCustomersView()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.customers, key = { it.id }) { customer ->
                            CustomerCard(
                                customer = customer,
                                isSelected = uiState.selectedCustomer?.id == customer.id,
                                onClick = { viewModel.selectCustomer(customer) },
                            )
                        }
                    }
                }
            }
        }

        // Right: Detail pane
        Box(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (uiState.selectedCustomer != null) {
                CustomerDetailPane(
                    customer = uiState.selectedCustomer!!,
                    orders = uiState.selectedCustomerOrders,
                    pointsHistory = uiState.pointsHistory,
                    discountOrders = uiState.discountOrders,
                    isLoadingPointsHistory = uiState.isLoadingPointsHistory,
                    isLoadingDiscountOrders = uiState.isLoadingDiscountOrders,
                    onDelete = { showDeleteDialog = true },
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.People,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(Res.string.customer_details),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation bottom sheet
    if (showDeleteDialog && uiState.selectedCustomer != null) {
        DeleteCustomerBottomSheet(
            onConfirm = {
                viewModel.deleteCustomer(uiState.selectedCustomer!!.id)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Search Bar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(Res.string.search_customers)) },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
        modifier = modifier,
    )
}

// ═══════════════════════════════════════════════════════════════════
// Sort Chips Row
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortChipsRow(
    selectedSort: CustomerSortBy,
    onSortSelected: (CustomerSortBy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = listOf(
        CustomerSortBy.ORDER_COUNT_DESC to Res.string.sort_order_count,
        CustomerSortBy.TOTAL_SPENT_DESC to Res.string.sort_total_spent,
        CustomerSortBy.LAST_ORDER_DESC to Res.string.sort_last_order,
        CustomerSortBy.NAME_ASC to Res.string.sort_name,
    )

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (sort, labelRes) ->
            FilterChip(
                selected = selectedSort == sort,
                onClick = { onSortSelected(sort) },
                label = { Text(stringResource(labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Loyalty Filter Chips Row
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LoyaltyFilterChipsRow(
    selectedFilter: LoyaltyFilter?,
    onFilterSelected: (LoyaltyFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onFilterSelected(null) },
                label = { Text(stringResource(Res.string.filter_all)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                ),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == LoyaltyFilter.HAS_POINTS,
                onClick = { onFilterSelected(LoyaltyFilter.HAS_POINTS) },
                label = { Text(stringResource(Res.string.filter_has_points)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                ),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == LoyaltyFilter.NO_POINTS,
                onClick = { onFilterSelected(LoyaltyFilter.NO_POINTS) },
                label = { Text(stringResource(Res.string.filter_no_points)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                ),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Customer Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CustomerCard(
    customer: Customer,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (customer.name?.firstOrNull() ?: customer.phone.lastOrNull() ?: '?')
                        .uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                // Name
                Text(
                    text = customer.name ?: customer.phone,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Phone (only show if name is set)
                if (customer.name != null) {
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Order count badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = ChartIndigo,
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${customer.orderCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ChartIndigo,
                        )
                    }

                    // Total spent
                    Text(
                        text = CurrencyFormatter.formatDecimal(customer.totalSpent),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = StockHealthy,
                    )
                }
            }

            // Last order date
            if (customer.lastOrderAt != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = customer.lastOrderAt!!.formatEpochMs("MMM dd"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Customer Detail Dialog (phone mode)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDetailBottomSheet(
    customer: Customer,
    orders: List<Order>,
    pointsHistory: List<PointsTransaction>,
    discountOrders: List<Order>,
    isLoadingPointsHistory: Boolean,
    isLoadingDiscountOrders: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.customer_details),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(Res.string.delete_customer),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            CustomerDetailContent(
                customer = customer,
                orders = orders,
                pointsHistory = pointsHistory,
                discountOrders = discountOrders,
                isLoadingPointsHistory = isLoadingPointsHistory,
                isLoadingDiscountOrders = isLoadingDiscountOrders,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Customer Detail Pane (tablet mode)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CustomerDetailPane(
    customer: Customer,
    orders: List<Order>,
    pointsHistory: List<PointsTransaction>,
    discountOrders: List<Order>,
    isLoadingPointsHistory: Boolean,
    isLoadingDiscountOrders: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.customer_details),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(Res.string.delete_customer),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            CustomerDetailContent(
                customer = customer,
                orders = orders,
                pointsHistory = pointsHistory,
                discountOrders = discountOrders,
                isLoadingPointsHistory = isLoadingPointsHistory,
                isLoadingDiscountOrders = isLoadingDiscountOrders,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Customer Detail Content (shared between dialog and pane)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CustomerDetailContent(
    customer: Customer,
    orders: List<Order>,
    pointsHistory: List<PointsTransaction>,
    discountOrders: List<Order>,
    isLoadingPointsHistory: Boolean,
    isLoadingDiscountOrders: Boolean,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(Res.string.tab_info),
        stringResource(Res.string.tab_points),
        stringResource(Res.string.tab_discounts),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Customer header card (always visible)
        CustomerHeaderCard(customer = customer)

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab content
        when (selectedTab) {
            0 -> InfoTabContent(customer = customer, orders = orders)
            1 -> PointsTabContent(
                customer = customer,
                pointsHistory = pointsHistory,
                isLoading = isLoadingPointsHistory,
            )
            2 -> DiscountsTabContent(
                discountOrders = discountOrders,
                isLoading = isLoadingDiscountOrders,
            )
        }
    }
}

@Composable
private fun CustomerHeaderCard(customer: Customer) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Name + Avatar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = customer.name ?: customer.phone,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = customer.phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatColumn(
                    label = stringResource(Res.string.sort_order_count),
                    value = "${customer.orderCount}",
                    color = ChartIndigo,
                )
                StatColumn(
                    label = stringResource(Res.string.sort_total_spent),
                    value = CurrencyFormatter.formatDecimal(customer.totalSpent),
                    color = StockHealthy,
                )
                StatColumn(
                    label = stringResource(Res.string.points_balance_label),
                    value = "${customer.pointsBalance}",
                    color = ChartAmber,
                )
                StatColumn(
                    label = stringResource(Res.string.sort_last_order),
                    value = customer.lastOrderAt?.formatEpochMs("MMM dd") ?: "-",
                    color = ChartPurple,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Info Tab Content
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun InfoTabContent(
    customer: Customer,
    orders: List<Order>,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Notes
        if (!customer.notes.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ChartAmber.copy(alpha = 0.08f),
                    ),
                ) {
                    Text(
                        text = customer.notes!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // Addresses section
        item {
            SectionHeader(
                title = stringResource(Res.string.addresses),
                icon = Icons.Outlined.LocationOn,
            )
        }

        if (customer.addresses.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.no_addresses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            items(customer.addresses, key = { it.id }) { address ->
                AddressCard(address = address)
            }
        }

        // Recent orders section
        item {
            SectionHeader(
                title = stringResource(Res.string.recent_orders),
                icon = Icons.Outlined.Receipt,
            )
        }

        if (orders.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.no_recent_orders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            items(orders, key = { it.id }) { order ->
                OrderCompactCard(order = order)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Points Tab Content
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PointsTabContent(
    customer: Customer,
    pointsHistory: List<PointsTransaction>,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Points balance card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = ChartAmber.copy(alpha = 0.12f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = ChartAmber,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${customer.pointsBalance}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = ChartAmber,
                )
                Text(
                    text = stringResource(Res.string.points_balance_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionHeader(
            title = stringResource(Res.string.loyalty_points_history),
            icon = Icons.Outlined.Star,
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            pointsHistory.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.no_points_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pointsHistory, key = { it.id }) { transaction ->
                        PointsTransactionCard(transaction = transaction)
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsTransactionCard(
    transaction: PointsTransaction,
    modifier: Modifier = Modifier,
) {
    val isEarn = transaction.type.equals("EARN", ignoreCase = true)
    val typeColor = if (isEarn) StockHealthy else StockOut
    val typeLabel = if (isEarn) stringResource(Res.string.points_earned_label) else stringResource(Res.string.points_redeemed_label)
    val pointsText = if (isEarn) "+${transaction.points}" else "-${transaction.points}"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = typeColor.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(typeColor.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = typeColor,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Description + date
            Column(modifier = Modifier.weight(1f)) {
                if (!transaction.description.isNullOrBlank()) {
                    Text(
                        text = transaction.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = transaction.createdAt.formatEpochMs("MMM dd, yyyy"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Points amount
            Text(
                text = pointsText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = typeColor,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Discounts Tab Content
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DiscountsTabContent(
    discountOrders: List<Order>,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(Res.string.discount_history),
            icon = Icons.Outlined.Receipt,
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            discountOrders.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.no_discount_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(discountOrders, key = { it.id }) { order ->
                        DiscountOrderCard(order = order)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscountOrderCard(
    order: Order,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Order icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(StockOut.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Receipt,
                    contentDescription = null,
                    tint = StockOut,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.order_number, order.id.takeLast(6)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = order.createdAt.formatEpochMs("MMM dd, yyyy"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!order.discountReason.isNullOrBlank()) {
                    Text(
                        text = order.discountReason!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Discount + Total
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(
                        Res.string.discount_amount_label,
                        CurrencyFormatter.formatDecimal(order.discount),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = StockOut,
                )
                Text(
                    text = CurrencyFormatter.formatDecimal(order.total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Address Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AddressCard(
    address: CustomerAddress,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ChartRose,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (address.label != null) {
                        Text(
                            text = address.label!!,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (address.isDefault) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.default_address),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Text(
                    text = address.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (address.deliveryFee != null && address.deliveryFee!! > 0.0) {
                    Text(
                        text = CurrencyFormatter.formatDecimal(address.deliveryFee!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = ChartAmber,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Order Compact Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun OrderCompactCard(
    order: Order,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (order.status) {
        net.marllex.waselak.core.model.OrderStatus.COMPLETED -> StockHealthy
        net.marllex.waselak.core.model.OrderStatus.CANCELED -> StockOut
        else -> ChartAmber
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Order icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Receipt,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.order_number, order.id.takeLast(6)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = order.createdAt.formatEpochMs("MMM dd, hh:mm a"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Status + Total
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatDecimal(order.total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = order.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Delete Confirmation Dialog
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteCustomerBottomSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.delete_customer),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.delete_customer_confirm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(Res.string.close))
                }
                androidx.compose.material3.Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(Res.string.delete_customer))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Empty State
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EmptyCustomersView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.People,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.no_customers),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.no_customers_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
