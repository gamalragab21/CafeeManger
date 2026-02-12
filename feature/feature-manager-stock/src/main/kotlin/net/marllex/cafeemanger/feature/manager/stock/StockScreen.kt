package net.marllex.cafeemanger.feature.manager.stock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.marllex.cafeemanger.core.model.Item
import net.marllex.cafeemanger.core.model.Stock
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.core.ui.theme.StockHealthy
import net.marllex.cafeemanger.core.ui.theme.StockLow
import net.marllex.cafeemanger.core.ui.theme.StockOut

/**
 * Helper function to get localized unit string from unit key
 */
@Composable
private fun getLocalizedUnit(unitKey: String): String {
    return stringResource(
        when (unitKey) {
            "pcs" -> R.string.unit_pcs
            "kg" -> R.string.unit_kg
            "liters" -> R.string.unit_liters
            "boxes" -> R.string.unit_boxes
            "packs" -> R.string.unit_packs
            else -> R.string.unit_pcs
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: StockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stock_management)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    if (uiState.alertItems.isNotEmpty()) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${uiState.alertItems.size}")
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Alerts",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showAddDialog,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Stock")
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.stockItems.isEmpty() -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadData,
            )
            else -> StockContent(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
        }

        // Dialogs
        if (uiState.showAddDialog) {
            AddEditStockDialog(uiState = uiState, viewModel = viewModel)
        }
        if (uiState.showQuantityDialog) {
            QuantityDialog(uiState = uiState, viewModel = viewModel)
        }
        if (uiState.showDeleteDialog) {
            DeleteConfirmDialog(
                stockName = uiState.deletingStock?.itemName ?: "",
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::dismissDeleteDialog,
            )
        }
    }
}

@Composable
private fun StockContent(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Modern Tab Selector with Cards
        ModernTabSelector(
            selectedTab = uiState.selectedTab,
            alertCount = uiState.alertItems.size,
            onTabSelected = { index ->
                viewModel.selectTab(index)
                if (index == 3) viewModel.loadTransactions()
            }
        )

        // Content
        when (uiState.selectedTab) {
            0 -> OverviewTab(uiState, viewModel)
            1 -> ItemsTab(uiState, viewModel)
            2 -> AlertsTab(uiState, viewModel)
            3 -> TransactionsTab(uiState)
        }
    }
}

@Composable
private fun ModernTabSelector(
    selectedTab: Int,
    alertCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Tab data with icons and colors
    val tabs = listOf(
        TabData(
            title = stringResource(R.string.stock_overview),
            icon = Icons.Default.Dashboard,
            color = Color(0xFF6366F1) // Indigo
        ),
        TabData(
            title = stringResource(R.string.items_list),
            icon = Icons.Default.Inventory,
            color = Color(0xFF10B981) // Green
        ),
        TabData(
            title = stringResource(R.string.alerts),
            icon = Icons.Default.Warning,
            color = Color(0xFFF59E0B), // Amber
            badge = if (alertCount > 0) alertCount else null
        ),
        TabData(
            title = stringResource(R.string.transactions),
            icon = Icons.Default.Receipt,
            color = Color(0xFF8B5CF6) // Purple
        )
    )

    // Horizontal scrollable tab row for different screen sizes
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(tabs) { index, tab ->
            ModernTabCard(
                tab = tab,
                isSelected = selectedTab == index,
                onClick = { onTabSelected(index) }
            )
        }
    }
}

private data class TabData(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val badge: Int? = null
)

@Composable
private fun ModernTabCard(
    tab: TabData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        animationSpec = tween(300)
    )
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .height(100.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                tab.color.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation
        ),
        border = if (isSelected)
            BorderStroke(2.dp, tab.color)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon with badge
                Box {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected)
                                    tab.color.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) tab.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Badge for alerts
                    tab.badge?.let { count ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (count > 99) "99+" else count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Title
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) tab.color else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(tab.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Overview Tab
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun OverviewTab(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Summary Cards Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCard(
                    title = stringResource(R.string.total_items),
                    value = "${uiState.summary.totalItems}",
                    icon = Icons.Outlined.Inventory2,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = stringResource(R.string.stock_value),
                    value = String.format("%.2f", uiState.summary.totalValue),
                    icon = Icons.Outlined.TrendingUp,
                    color = StockHealthy,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCard(
                    title = stringResource(R.string.low_stock),
                    value = "${uiState.summary.lowStockCount}",
                    icon = Icons.Outlined.TrendingDown,
                    color = StockOut,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = stringResource(R.string.out_of_stock),
                    value = "${uiState.summary.outOfStockCount}",
                    icon = Icons.Filled.Warning,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Stock Health
        item {
            StockHealthCard(uiState)
        }

        // Top Value Items
        if (uiState.topValueItems.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.top_value_items),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(uiState.topValueItems) { stock ->
                TopValueItemRow(stock)
            }
        }

        // Empty state
        if (uiState.stockItems.isEmpty()) {
            item {
                EmptyStockView()
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StockHealthCard(uiState: StockViewModel.UiState) {
    val total = uiState.summary.totalItems.toFloat().coerceAtLeast(1f)
    val healthy = uiState.summary.healthyStockCount.toFloat()
    val low = uiState.summary.lowStockCount.toFloat()
    val outOf = uiState.summary.outOfStockCount.toFloat()

    val healthyPct by animateFloatAsState(targetValue = healthy / total, label = "healthy")
    val lowPct by animateFloatAsState(targetValue = low / total, label = "low")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stock_health),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Stacked progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
            ) {
                if (healthyPct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(healthyPct.coerceAtLeast(0.01f))
                            .height(12.dp)
                            .background(StockHealthy),
                    )
                }
                if (lowPct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(lowPct.coerceAtLeast(0.01f))
                            .height(12.dp)
                            .background(StockLow),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LegendItem(
                    color = StockHealthy,
                    label = stringResource(R.string.healthy),
                    count = uiState.summary.healthyStockCount,
                )
                LegendItem(
                    color = StockLow,
                    label = stringResource(R.string.low_stock),
                    count = uiState.summary.lowStockCount,
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.error,
                    label = stringResource(R.string.out_of_stock),
                    count = uiState.summary.outOfStockCount,
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopValueItemRow(stock: Stock) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock.itemName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${stock.quantity} ${getLocalizedUnit(stock.unit)} × ${String.format("%.2f", stock.costPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = String.format("%.2f", stock.totalValue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = StockHealthy,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Items Tab
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ItemsTab(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    Column {
        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            placeholder = { Text(stringResource(R.string.search_stock)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (uiState.filteredStockItems.isEmpty()) {
            EmptyStockView()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.filteredStockItems, key = { it.id }) { stock ->
                    StockItemCard(
                        stock = stock,
                        onEdit = { viewModel.showEditDialog(stock) },
                        onAddQty = { viewModel.showAddQuantityDialog(stock) },
                        onDeductQty = { viewModel.showDeductQuantityDialog(stock) },
                        onDelete = { viewModel.showDeleteConfirmation(stock) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StockItemCard(
    stock: Stock,
    onEdit: () -> Unit,
    onAddQty: () -> Unit,
    onDeductQty: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            stock.isOutOfStock -> StockOut
            stock.isLowStock -> StockOut
            else -> StockHealthy
        },
        label = "status",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stock.itemName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Independent item badge
                        if (!stock.isMenuItem) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.independent_item),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Status chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = when {
                            stock.isOutOfStock -> stringResource(R.string.out_of_stock)
                            stock.isLowStock -> stringResource(R.string.low_stock)
                            else -> stringResource(R.string.healthy)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quantity progress bar
            val progress by animateFloatAsState(
                targetValue = if (stock.minQuantity > 0)
                    (stock.quantity.toFloat() / (stock.minQuantity * 3f)).coerceIn(0f, 1f)
                else 1f,
                label = "progress",
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailColumn(label = "Qty", value = "${stock.quantity} ${getLocalizedUnit(stock.unit)}")
                DetailColumn(label = "Min", value = "${stock.minQuantity}")
                DetailColumn(label = "Cost", value = String.format("%.2f", stock.costPrice))
                DetailColumn(label = "Value", value = String.format("%.2f", stock.totalValue))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onAddQty) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add Quantity",
                        tint = StockHealthy,
                    )
                }
                IconButton(onClick = onDeductQty) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = "Deduct Quantity",
                        tint = StockOut,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Alerts Tab
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AlertsTab(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    if (uiState.alertItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = StockHealthy,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_alerts),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Out of stock section
            if (uiState.outOfStockItems.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.currently_empty),
                        icon = Icons.Filled.Warning,
                        color = MaterialTheme.colorScheme.error,
                        count = uiState.outOfStockItems.size,
                    )
                }
                items(uiState.outOfStockItems) { stock ->
                    AlertItemCard(
                        stock = stock,
                        isOutOfStock = true,
                        onAddQty = { viewModel.showAddQuantityDialog(stock) },
                    )
                }
            }

            // Low stock section
            if (uiState.lowStockItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(
                        title = stringResource(R.string.needs_reorder),
                        icon = Icons.Outlined.TrendingDown,
                        color = StockOut,
                        count = uiState.lowStockItems.size,
                    )
                }
                items(uiState.lowStockItems) { stock ->
                    AlertItemCard(
                        stock = stock,
                        isOutOfStock = false,
                        onAddQty = { viewModel.showAddQuantityDialog(stock) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, color: Color, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun AlertItemCard(
    stock: Stock,
    isOutOfStock: Boolean,
    onAddQty: () -> Unit,
) {
    val bgColor = if (isOutOfStock)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    else
        StockOut.copy(alpha = 0.08f)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock.itemName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isOutOfStock) "Qty: 0 ${getLocalizedUnit(stock.unit)}"
                    else "Qty: ${stock.quantity} / ${stock.minQuantity} ${getLocalizedUnit(stock.unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAddQty) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add_quantity))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Dialogs
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditStockDialog(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    val isEditing = uiState.editingStock != null
    var itemDropdownExpanded by remember { mutableStateOf(false) }

    // Check if form is valid
    val isFormValid = if (uiState.dialogIsIndependent) {
        uiState.dialogCustomItemName.isNotBlank() &&
        uiState.dialogQuantity.isNotBlank() &&
        uiState.dialogCostPrice.isNotBlank()
    } else {
        uiState.dialogSelectedItemId.isNotBlank() &&
        uiState.dialogQuantity.isNotBlank() &&
        uiState.dialogCostPrice.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = viewModel::dismissAddDialog,
        title = {
            Text(
                if (isEditing) stringResource(R.string.edit_stock)
                else stringResource(R.string.add_stock)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Mode selector (only for new items)
                if (!isEditing) {
                    Text(
                        text = stringResource(R.string.item_type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !uiState.dialogIsIndependent,
                            onClick = { viewModel.toggleDialogMode(false) },
                            label = { Text(stringResource(R.string.menu_item)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = uiState.dialogIsIndependent,
                            onClick = { viewModel.toggleDialogMode(true) },
                            label = { Text(stringResource(R.string.independent_item)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Item selector or Text input based on mode
                if (!isEditing) {
                    if (uiState.dialogIsIndependent) {
                        // Text input for independent items
                        OutlinedTextField(
                            value = uiState.dialogCustomItemName,
                            onValueChange = viewModel::updateDialogCustomItemName,
                            label = { Text(stringResource(R.string.item_name)) },
                            placeholder = { Text(stringResource(R.string.enter_item_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // Dropdown for menu items
                        ExposedDropdownMenuBox(
                            expanded = itemDropdownExpanded,
                            onExpandedChange = { itemDropdownExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = uiState.dialogSelectedItemName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.select_item)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = itemDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = itemDropdownExpanded,
                                onDismissRequest = { itemDropdownExpanded = false },
                            ) {
                                uiState.unTrackedItems.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(item.name, fontWeight = FontWeight.Medium)
                                                Text(
                                                    "Price: ${String.format("%.2f", item.price)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectItem(item)
                                            itemDropdownExpanded = false
                                        },
                                    )
                                }
                                if (uiState.unTrackedItems.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.all_items_tracked)) },
                                        onClick = { itemDropdownExpanded = false },
                                        enabled = false,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Editing - show name (editable for independent, readonly for menu)
                    if (uiState.editingStock?.isMenuItem == false) {
                        OutlinedTextField(
                            value = uiState.dialogCustomItemName,
                            onValueChange = viewModel::updateDialogCustomItemName,
                            label = { Text(stringResource(R.string.item_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = uiState.dialogSelectedItemName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_item)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                        )
                    }
                }

                // Quantity
                OutlinedTextField(
                    value = uiState.dialogQuantity,
                    onValueChange = viewModel::updateDialogQuantity,
                    label = { Text(stringResource(R.string.quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Min Quantity
                OutlinedTextField(
                    value = uiState.dialogMinQuantity,
                    onValueChange = viewModel::updateDialogMinQuantity,
                    label = { Text(stringResource(R.string.min_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Cost Price
                OutlinedTextField(
                    value = uiState.dialogCostPrice,
                    onValueChange = viewModel::updateDialogCostPrice,
                    label = { Text(stringResource(R.string.cost_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Unit selector
                Text(stringResource(R.string.unit), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val unitKeys = listOf("pcs", "kg", "liters", "boxes", "packs")
                    unitKeys.forEach { unitKey ->
                        FilterChip(
                            selected = uiState.dialogUnit == unitKey,
                            onClick = { viewModel.updateDialogUnit(unitKey) },
                            label = { Text(getLocalizedUnit(unitKey)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                        )
                    }
                }

                // Alert toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.low_stock_alert),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.material3.Switch(
                        checked = uiState.dialogAlertEnabled,
                        onCheckedChange = viewModel::updateDialogAlertEnabled,
                    )
                }

                // Total value preview
                val qty = uiState.dialogQuantity.toIntOrNull() ?: 0
                val price = uiState.dialogCostPrice.toDoubleOrNull() ?: 0.0
                if (qty > 0 && price > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = StockHealthy.copy(alpha = 0.1f),
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.total_value),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = String.format("%.2f", qty * price),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StockHealthy,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::saveStockItem,
                enabled = !uiState.isSaving && isFormValid,
            ) {
                Text(
                    if (uiState.isSaving) stringResource(R.string.saving)
                    else stringResource(R.string.save)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissAddDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun QuantityDialog(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    val isAdd = uiState.quantityDialogIsAdd
    val stock = uiState.quantityDialogStock ?: return

    AlertDialog(
        onDismissRequest = viewModel::dismissQuantityDialog,
        title = {
            Text(
                if (isAdd) stringResource(R.string.add_quantity)
                else stringResource(R.string.deduct_quantity)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Item info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stock.itemName, fontWeight = FontWeight.Medium)
                        Text(
                            "Current: ${stock.quantity} ${getLocalizedUnit(stock.unit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.quantityDialogAmount,
                    onValueChange = viewModel::updateQuantityDialogAmount,
                    label = {
                        Text(
                            if (isAdd) stringResource(R.string.quantity_to_add)
                            else stringResource(R.string.quantity_to_deduct)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.quantityDialogNote,
                    onValueChange = viewModel::updateQuantityDialogNote,
                    label = { Text(stringResource(R.string.note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )

                // Preview
                val amount = uiState.quantityDialogAmount.toIntOrNull() ?: 0
                if (amount > 0) {
                    val newQty = if (isAdd) stock.quantity + amount
                    else (stock.quantity - amount).coerceAtLeast(0)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAdd) StockHealthy.copy(alpha = 0.1f)
                            else StockOut.copy(alpha = 0.1f),
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("New Quantity", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "$newQty ${getLocalizedUnit(stock.unit)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdd) StockHealthy else StockOut,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::confirmQuantityChange,
                enabled = !uiState.isSaving && (uiState.quantityDialogAmount.toIntOrNull() ?: 0) > 0,
            ) {
                Text(
                    if (uiState.isSaving) stringResource(R.string.saving)
                    else stringResource(R.string.confirm)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissQuantityDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    stockName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.confirm_delete)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════
// Transactions Tab
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TransactionsTab(uiState: StockViewModel.UiState) {
    if (uiState.transactionsLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }
    } else if (uiState.transactions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.transactions, key = { it.id }) { transaction ->
                TransactionCard(transaction)
            }
        }
    }
}

@Composable
private fun TransactionCard(transaction: net.marllex.cafeemanger.core.model.StockTransaction) {
    val typeColor = when (transaction.type) {
        net.marllex.cafeemanger.core.model.StockTransactionType.ADD -> StockHealthy
        net.marllex.cafeemanger.core.model.StockTransactionType.DEDUCT -> StockOut
        net.marllex.cafeemanger.core.model.StockTransactionType.ADJUST -> MaterialTheme.colorScheme.primary
    }

    val typeIcon = when (transaction.type) {
        net.marllex.cafeemanger.core.model.StockTransactionType.ADD -> Icons.Outlined.TrendingUp
        net.marllex.cafeemanger.core.model.StockTransactionType.DEDUCT -> Icons.Outlined.TrendingDown
        net.marllex.cafeemanger.core.model.StockTransactionType.ADJUST -> Icons.Filled.Edit
    }

    val typeLabel = when (transaction.type) {
        net.marllex.cafeemanger.core.model.StockTransactionType.ADD -> stringResource(R.string.stock_added)
        net.marllex.cafeemanger.core.model.StockTransactionType.DEDUCT -> stringResource(R.string.stock_deducted)
        net.marllex.cafeemanger.core.model.StockTransactionType.ADJUST -> stringResource(R.string.stock_adjusted)
    }

    Card(
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
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    typeIcon,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.itemName ?: "Unknown Item",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = typeColor,
                )
                transaction.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Quantity change
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when (transaction.type) {
                        net.marllex.cafeemanger.core.model.StockTransactionType.ADD -> "+${transaction.quantity}"
                        net.marllex.cafeemanger.core.model.StockTransactionType.DEDUCT -> "-${transaction.quantity}"
                        net.marllex.cafeemanger.core.model.StockTransactionType.ADJUST -> "${transaction.quantity}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = typeColor,
                )
                Text(
                    text = "${transaction.previousQuantity} → ${transaction.newQuantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Format date
                val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(transaction.createdAt))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Empty State
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EmptyStockView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Inventory,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_stock_items),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
