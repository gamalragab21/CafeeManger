package net.marllex.waselak.feature.manager.stock

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
import androidx.compose.material3.Button
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.stock.generated.resources.Res
import net.marllex.waselak.feature.manager.stock.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.Item
import net.marllex.waselak.core.model.Stock
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel
import net.marllex.waselak.core.common.extensions.formatEpochMs
import net.marllex.waselak.core.common.utils.CurrencyFormatter

/**
 * Helper function to get localized unit string from unit key
 */
@Composable
private fun getLocalizedUnit(unitKey: String): String {
    return stringResource(
        when (unitKey.uppercase()) {
            "PCS", "PIECE" -> Res.string.unit_pcs
            "KG", "KILOGRAM" -> Res.string.unit_kg
            "GRAM" -> Res.string.unit_gram
            "LITERS", "LITER" -> Res.string.unit_liters
            "MILLILITER" -> Res.string.unit_ml
            "PACKS", "PACK" -> Res.string.unit_packs
            else -> Res.string.unit_pcs
        }
    )
}

/**
 * Returns the list of compatible unit keys based on a stock item's base unit.
 * Simplified 6-unit system: GRAM↔KILOGRAM, MILLILITER↔LITER, PIECE, PACK
 */
private fun getCompatibleUnits(baseUnit: String): List<String> {
    return when (baseUnit.uppercase()) {
        "GRAM", "KILOGRAM" -> listOf("GRAM", "KILOGRAM")
        "MILLILITER", "LITER" -> listOf("MILLILITER", "LITER")
        "PIECE" -> listOf("PIECE")
        "PACK" -> listOf("PACK")
        else -> listOf(baseUnit.uppercase())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: StockViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.stock_management)) },
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
            if (uiState.selectedTab != 3) { // No FAB on transactions tab
                FloatingActionButton(
                    onClick = {
                        if (uiState.selectedTab == 4) viewModel.showAddRecipeSheet()
                        else viewModel.showAddDialog()
                    },
                    containerColor = if (uiState.selectedTab == 4) ChartCyan
                    else MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
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
            AddEditStockBottomSheet(uiState = uiState, viewModel = viewModel)
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
        if (uiState.showRecipeSheet) {
            AddEditRecipeBottomSheet(uiState = uiState, viewModel = viewModel)
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
                if (index == 4) viewModel.loadRecipes()
            }
        )

        // Content
        when (uiState.selectedTab) {
            0 -> OverviewTab(uiState, viewModel)
            1 -> ItemsTab(uiState, viewModel)
            2 -> AlertsTab(uiState, viewModel)
            3 -> TransactionsTab(uiState)
            4 -> RecipesTab(uiState, viewModel)
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
            title = stringResource(Res.string.stock_overview),
            icon = Icons.Default.Dashboard,
            color = ChartIndigo
        ),
        TabData(
            title = stringResource(Res.string.items_list),
            icon = Icons.Default.Inventory,
            color = ChartGreen
        ),
        TabData(
            title = stringResource(Res.string.alerts),
            icon = Icons.Default.Warning,
            color = ChartAmber,
            badge = if (alertCount > 0) alertCount else null
        ),
        TabData(
            title = stringResource(Res.string.transactions),
            icon = Icons.Default.Receipt,
            color = ChartPurple
        ),
        TabData(
            title = stringResource(Res.string.recipes),
            icon = Icons.Default.Receipt,
            color = ChartCyan
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
                        tint = MaterialTheme.colorScheme.surface,
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
                    title = stringResource(Res.string.total_items),
                    value = "${uiState.summary.totalItems}",
                    icon = Icons.Outlined.Inventory2,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = stringResource(Res.string.stock_value),
                    value = CurrencyFormatter.formatDecimal(uiState.summary.totalValue),
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
                    title = stringResource(Res.string.low_stock),
                    value = "${uiState.summary.lowStockCount}",
                    icon = Icons.Outlined.TrendingDown,
                    color = StockOut,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = stringResource(Res.string.out_of_stock),
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
                    text = stringResource(Res.string.top_value_items),
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
                text = stringResource(Res.string.stock_health),
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
                    label = stringResource(Res.string.healthy),
                    count = uiState.summary.healthyStockCount,
                )
                LegendItem(
                    color = StockLow,
                    label = stringResource(Res.string.low_stock),
                    count = uiState.summary.lowStockCount,
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.error,
                    label = stringResource(Res.string.out_of_stock),
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
                    text = "${stock.quantity} ${getLocalizedUnit(stock.unit)} × ${CurrencyFormatter.formatDecimal(stock.costPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = CurrencyFormatter.formatDecimal(stock.totalValue),
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
            placeholder = { Text(stringResource(Res.string.search_stock)) },
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
                                    text = stringResource(Res.string.independent_item),
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
                            stock.isOutOfStock -> stringResource(Res.string.out_of_stock)
                            stock.isLowStock -> stringResource(Res.string.low_stock)
                            else -> stringResource(Res.string.healthy)
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
                    (stock.quantity / (stock.minQuantity * 3.0)).toFloat().coerceIn(0f, 1f)
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
                DetailColumn(label = "Cost", value = CurrencyFormatter.formatDecimal(stock.costPrice))
                DetailColumn(label = "Value", value = CurrencyFormatter.formatDecimal(stock.totalValue))
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
                    text = stringResource(Res.string.no_alerts),
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
                        title = stringResource(Res.string.currently_empty),
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
                        title = stringResource(Res.string.needs_reorder),
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
                Text(stringResource(Res.string.add_quantity))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Dialogs
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditStockBottomSheet(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = uiState.editingStock != null
    var itemDropdownExpanded by remember { mutableStateOf(false) }

    val isFormValid = if (uiState.dialogIsIndependent) {
        uiState.dialogCustomItemName.isNotBlank() &&
        uiState.dialogQuantity.isNotBlank() &&
        uiState.dialogCostPrice.isNotBlank()
    } else {
        uiState.dialogSelectedItemId.isNotBlank() &&
        uiState.dialogQuantity.isNotBlank() &&
        uiState.dialogCostPrice.isNotBlank()
    }

    ModalBottomSheet(
        onDismissRequest = viewModel::dismissAddDialog,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (isEditing) stringResource(Res.string.edit_stock)
                    else stringResource(Res.string.add_stock),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Mode selector (only for new items)
                if (!isEditing) {
                    item {
                        Text(
                            text = stringResource(Res.string.item_type),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !uiState.dialogIsIndependent,
                                onClick = { viewModel.toggleDialogMode(false) },
                                label = { Text(stringResource(Res.string.menu_item)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = uiState.dialogIsIndependent,
                                onClick = { viewModel.toggleDialogMode(true) },
                                label = { Text(stringResource(Res.string.independent_item)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Item selector or Text input
                item {
                    if (!isEditing) {
                        if (uiState.dialogIsIndependent) {
                            OutlinedTextField(
                                value = uiState.dialogCustomItemName,
                                onValueChange = viewModel::updateDialogCustomItemName,
                                label = { Text(stringResource(Res.string.item_name)) },
                                placeholder = { Text(stringResource(Res.string.enter_item_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = itemDropdownExpanded,
                                onExpandedChange = { itemDropdownExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = uiState.dialogSelectedItemName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(Res.string.select_item)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = itemDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(12.dp),
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
                                                        "Price: ${CurrencyFormatter.formatDecimal(item.price)}",
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
                                            text = { Text(stringResource(Res.string.all_items_tracked)) },
                                            onClick = { itemDropdownExpanded = false },
                                            enabled = false,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (uiState.editingStock?.isMenuItem == false) {
                            OutlinedTextField(
                                value = uiState.dialogCustomItemName,
                                onValueChange = viewModel::updateDialogCustomItemName,
                                label = { Text(stringResource(Res.string.item_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                        } else {
                            OutlinedTextField(
                                value = uiState.dialogSelectedItemName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(Res.string.select_item)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }

                // Quantity + Min Quantity side by side
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.dialogQuantity,
                            onValueChange = viewModel::updateDialogQuantity,
                            label = { Text(stringResource(Res.string.quantity)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = uiState.dialogMinQuantity,
                            onValueChange = viewModel::updateDialogMinQuantity,
                            label = { Text(stringResource(Res.string.min_quantity)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                // Cost Price
                item {
                    OutlinedTextField(
                        value = uiState.dialogCostPrice,
                        onValueChange = viewModel::updateDialogCostPrice,
                        label = { Text(stringResource(Res.string.cost_price)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // Unit selector
                item {
                    Text(
                        text = stringResource(Res.string.unit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "KILOGRAM" to "⚖",
                            "LITER" to "💧",
                            "PIECE" to "🔢",
                            "PACK" to "📦",
                        ).forEach { (unitKey, icon) ->
                            FilterChip(
                                selected = uiState.dialogUnit == unitKey,
                                onClick = { viewModel.updateDialogUnit(unitKey) },
                                label = { Text("$icon ${getLocalizedUnit(unitKey)}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    }
                }

                // Alert toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.low_stock_alert),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        androidx.compose.material3.Switch(
                            checked = uiState.dialogAlertEnabled,
                            onCheckedChange = viewModel::updateDialogAlertEnabled,
                        )
                    }
                }

                // Total value preview
                item {
                    val qty = uiState.dialogQuantity.toDoubleOrNull() ?: 0.0
                    val price = uiState.dialogCostPrice.toDoubleOrNull() ?: 0.0
                    if (qty > 0 && price > 0) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = StockHealthy.copy(alpha = 0.1f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(Res.string.total_value),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = CurrencyFormatter.formatDecimal(qty * price),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StockHealthy,
                                )
                            }
                        }
                    }
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // Sticky bottom action bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::dismissAddDialog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Button(
                        onClick = viewModel::saveStockItem,
                        enabled = !uiState.isSaving && isFormValid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            if (uiState.isSaving) stringResource(Res.string.saving)
                            else stringResource(Res.string.save)
                        )
                    }
                }
            }
        }
    }
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
                if (isAdd) stringResource(Res.string.add_quantity)
                else stringResource(Res.string.deduct_quantity)
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
                            if (isAdd) stringResource(Res.string.quantity_to_add)
                            else stringResource(Res.string.quantity_to_deduct)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.quantityDialogNote,
                    onValueChange = viewModel::updateQuantityDialogNote,
                    label = { Text(stringResource(Res.string.note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )

                // Preview
                val amount = uiState.quantityDialogAmount.toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    val newQty = if (isAdd) stock.quantity + amount
                    else (stock.quantity - amount).coerceAtLeast(0.0)
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
                enabled = !uiState.isSaving && (uiState.quantityDialogAmount.toDoubleOrNull() ?: 0.0) > 0,
            ) {
                Text(
                    if (uiState.isSaving) stringResource(Res.string.saving)
                    else stringResource(Res.string.confirm)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissQuantityDialog) {
                Text(stringResource(Res.string.cancel))
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
        title = { Text(stringResource(Res.string.delete)) },
        text = { Text(stringResource(Res.string.confirm_delete)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(Res.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
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
                    text = stringResource(Res.string.no_transactions),
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
private fun TransactionCard(transaction: net.marllex.waselak.core.model.StockTransaction) {
    val typeColor = when (transaction.type) {
        net.marllex.waselak.core.model.StockTransactionType.ADD,
        net.marllex.waselak.core.model.StockTransactionType.PURCHASE,
        net.marllex.waselak.core.model.StockTransactionType.RETURN -> StockHealthy
        net.marllex.waselak.core.model.StockTransactionType.DEDUCT,
        net.marllex.waselak.core.model.StockTransactionType.SALE_DIRECT,
        net.marllex.waselak.core.model.StockTransactionType.SALE_RECIPE,
        net.marllex.waselak.core.model.StockTransactionType.WASTE -> StockOut
        net.marllex.waselak.core.model.StockTransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiary
        net.marllex.waselak.core.model.StockTransactionType.ADJUST -> MaterialTheme.colorScheme.primary
    }

    val typeIcon = when (transaction.type) {
        net.marllex.waselak.core.model.StockTransactionType.ADD,
        net.marllex.waselak.core.model.StockTransactionType.PURCHASE,
        net.marllex.waselak.core.model.StockTransactionType.RETURN -> Icons.Outlined.TrendingUp
        net.marllex.waselak.core.model.StockTransactionType.DEDUCT,
        net.marllex.waselak.core.model.StockTransactionType.SALE_DIRECT,
        net.marllex.waselak.core.model.StockTransactionType.SALE_RECIPE,
        net.marllex.waselak.core.model.StockTransactionType.WASTE -> Icons.Outlined.TrendingDown
        net.marllex.waselak.core.model.StockTransactionType.TRANSFER -> Icons.Filled.Remove
        net.marllex.waselak.core.model.StockTransactionType.ADJUST -> Icons.Filled.Edit
    }

    val typeLabel = when (transaction.type) {
        net.marllex.waselak.core.model.StockTransactionType.ADD -> stringResource(Res.string.stock_added)
        net.marllex.waselak.core.model.StockTransactionType.DEDUCT -> stringResource(Res.string.stock_deducted)
        net.marllex.waselak.core.model.StockTransactionType.ADJUST -> stringResource(Res.string.stock_adjusted)
        net.marllex.waselak.core.model.StockTransactionType.PURCHASE -> stringResource(Res.string.stock_purchased)
        net.marllex.waselak.core.model.StockTransactionType.SALE_DIRECT -> stringResource(Res.string.stock_sale_direct)
        net.marllex.waselak.core.model.StockTransactionType.SALE_RECIPE -> stringResource(Res.string.stock_sale_recipe)
        net.marllex.waselak.core.model.StockTransactionType.RETURN -> stringResource(Res.string.stock_returned)
        net.marllex.waselak.core.model.StockTransactionType.WASTE -> "Waste"
        net.marllex.waselak.core.model.StockTransactionType.TRANSFER -> "Transfer"
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
                        net.marllex.waselak.core.model.StockTransactionType.ADD,
                        net.marllex.waselak.core.model.StockTransactionType.PURCHASE,
                        net.marllex.waselak.core.model.StockTransactionType.RETURN -> "+${transaction.quantity}"
                        net.marllex.waselak.core.model.StockTransactionType.DEDUCT,
                        net.marllex.waselak.core.model.StockTransactionType.SALE_DIRECT,
                        net.marllex.waselak.core.model.StockTransactionType.SALE_RECIPE,
                        net.marllex.waselak.core.model.StockTransactionType.WASTE,
                        net.marllex.waselak.core.model.StockTransactionType.TRANSFER -> "-${transaction.quantity}"
                        net.marllex.waselak.core.model.StockTransactionType.ADJUST -> "${transaction.quantity}"
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
                val dateStr = transaction.createdAt.formatEpochMs("MMM dd, HH:mm")
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
// Recipes Tab
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RecipesTab(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    if (uiState.recipesLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }
    } else if (uiState.recipes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.no_recipes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.no_recipes_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::showAddRecipeSheet,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.add_recipe))
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.recipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onEdit = { viewModel.showEditRecipeSheet(recipe) },
                    onDelete = { viewModel.deleteRecipe(recipe.id) },
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: net.marllex.waselak.core.model.Recipe,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

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
                // Recipe icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChartCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        tint = ChartCyan,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = recipe.itemName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (recipe.status) {
                                "ACTIVE" -> StockHealthy.copy(alpha = 0.1f)
                                "DRAFT" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = when (recipe.status) {
                            "ACTIVE" -> stringResource(Res.string.recipe_active)
                            "DRAFT" -> "Draft"
                            "ARCHIVED" -> stringResource(Res.string.recipe_inactive)
                            else -> recipe.status
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (recipe.status) {
                            "ACTIVE" -> StockHealthy
                            "DRAFT" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Yield + cost info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailColumn(
                    label = stringResource(Res.string.recipe_yield),
                    value = "${recipe.yieldQuantity} ${getLocalizedUnit(recipe.yieldUnit)}",
                )
                DetailColumn(
                    label = stringResource(Res.string.recipe_ingredients_count),
                    value = "${recipe.ingredients.size}",
                )
                DetailColumn(
                    label = stringResource(Res.string.recipe_cost),
                    value = CurrencyFormatter.formatDecimal(recipe.totalCost),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand/collapse for ingredients
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.recipe_ingredients),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    recipe.ingredients.forEach { ingredient ->
                        RecipeIngredientRow(ingredient)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit Recipe",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete Recipe",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeIngredientRow(ingredient: net.marllex.waselak.core.model.RecipeIngredient) {
    val hasEnough = ingredient.availableQuantity >= ingredient.quantity

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasEnough)
                StockHealthy.copy(alpha = 0.05f)
            else
                StockOut.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (hasEnough) StockHealthy else StockOut),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ingredient.stockItemName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${ingredient.quantity} ${getLocalizedUnit(ingredient.unit)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${ingredient.availableQuantity})",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasEnough) StockHealthy else StockOut,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Add/Edit Recipe BottomSheet
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditRecipeBottomSheet(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = uiState.editingRecipe != null

    val isFormValid = uiState.recipeSelectedItemId.isNotBlank() &&
        uiState.recipeName.isNotBlank() &&
        (uiState.recipeYieldQuantity.toDoubleOrNull() ?: 0.0) > 0 &&
        uiState.recipeIngredients.any {
            it.stockId.isNotBlank() && (it.quantity.toDoubleOrNull() ?: 0.0) > 0
        }

    ModalBottomSheet(
        onDismissRequest = viewModel::dismissRecipeSheet,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChartCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        tint = ChartCyan,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (isEditing) stringResource(Res.string.edit_recipe)
                    else stringResource(Res.string.add_recipe),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Menu Item selector
                item {
                    RecipeMenuItemSelector(uiState, viewModel, isEditing)
                }

                // Recipe name
                item {
                    OutlinedTextField(
                        value = uiState.recipeName,
                        onValueChange = viewModel::updateRecipeName,
                        label = { Text(stringResource(Res.string.recipe_name)) },
                        placeholder = { Text(stringResource(Res.string.recipe_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // Description
                item {
                    OutlinedTextField(
                        value = uiState.recipeDescription,
                        onValueChange = viewModel::updateRecipeDescription,
                        label = { Text(stringResource(Res.string.recipe_description)) },
                        placeholder = { Text(stringResource(Res.string.recipe_description_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3,
                    )
                }

                // Yield section
                item {
                    RecipeYieldSection(uiState, viewModel)
                }

                // Ingredients header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.recipe_ingredients),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = viewModel::addRecipeIngredient) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(Res.string.add_ingredient))
                        }
                    }
                }

                // Ingredient forms
                itemsIndexed(uiState.recipeIngredients) { index, ingredient ->
                    RecipeIngredientFormCard(
                        index = index,
                        form = ingredient,
                        stockItems = uiState.stockItems,
                        canRemove = uiState.recipeIngredients.size > 1,
                        onSelectStock = { stock -> viewModel.selectIngredientStock(index, stock) },
                        onUpdateQuantity = { qty ->
                            viewModel.updateRecipeIngredient(index, ingredient.copy(quantity = qty))
                        },
                        onUpdateUnit = { unit ->
                            viewModel.updateRecipeIngredient(index, ingredient.copy(unit = unit))
                        },
                        onUpdateFixedQuantity = { fixed ->
                            viewModel.updateRecipeIngredient(index, ingredient.copy(fixedQuantity = fixed))
                        },
                        onRemove = { viewModel.removeRecipeIngredient(index) },
                    )
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // Sticky bottom action bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    // Error message
                    uiState.recipeError?.let { errorMsg ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::dismissRecipeSheet,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(Res.string.cancel))
                        }
                        Button(
                            onClick = viewModel::saveRecipe,
                            enabled = !uiState.recipeSaving && isFormValid,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                if (uiState.recipeSaving) stringResource(Res.string.saving)
                                else stringResource(Res.string.save)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeMenuItemSelector(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
    isEditing: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.recipe_menu_item),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )

        if (isEditing) {
            // Read-only in edit mode
            OutlinedTextField(
                value = uiState.recipeSelectedItemName,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                label = { Text(stringResource(Res.string.select_item)) },
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = uiState.recipeSelectedItemName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.select_item)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    uiState.itemsWithoutRecipe.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(item.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        CurrencyFormatter.formatDecimal(item.price),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                viewModel.selectRecipeItem(item)
                                expanded = false
                            },
                        )
                    }
                    if (uiState.itemsWithoutRecipe.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.all_items_have_recipes)) },
                            onClick = { expanded = false },
                            enabled = false,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeYieldSection(
    uiState: StockViewModel.UiState,
    viewModel: StockViewModel,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.recipe_yield_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = uiState.recipeYieldQuantity,
                    onValueChange = viewModel::updateRecipeYieldQuantity,
                    label = { Text(stringResource(Res.string.quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Text(
                text = stringResource(Res.string.unit),
                style = MaterialTheme.typography.labelMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val unitKeys = listOf("PIECE", "KILOGRAM", "GRAM", "LITER", "MILLILITER", "PACK")
                unitKeys.forEach { unitKey ->
                    FilterChip(
                        selected = uiState.recipeYieldUnit == unitKey,
                        onClick = { viewModel.updateRecipeYieldUnit(unitKey) },
                        label = { Text(getLocalizedUnit(unitKey)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChartCyan,
                            selectedLabelColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeIngredientFormCard(
    index: Int,
    form: RecipeIngredientForm,
    stockItems: List<Stock>,
    canRemove: Boolean,
    onSelectStock: (Stock) -> Unit,
    onUpdateQuantity: (String) -> Unit,
    onUpdateUnit: (String) -> Unit,
    onUpdateFixedQuantity: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var stockDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header row with number and remove button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ChartCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChartCyan,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.ingredient_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Stock item selector
            ExposedDropdownMenuBox(
                expanded = stockDropdownExpanded,
                onExpandedChange = { stockDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = form.stockName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.select_stock_item)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stockDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(
                    expanded = stockDropdownExpanded,
                    onDismissRequest = { stockDropdownExpanded = false },
                ) {
                    stockItems.forEach { stock ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stock.itemName, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${stock.quantity} ${getLocalizedUnit(stock.unit)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // Show unit type badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ChartCyan.copy(alpha = 0.1f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = getLocalizedUnit(stock.baseUnit),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ChartCyan,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectStock(stock)
                                stockDropdownExpanded = false
                            },
                        )
                    }
                    if (stockItems.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.no_stock_items_hint)) },
                            onClick = { stockDropdownExpanded = false },
                            enabled = false,
                        )
                    }
                }
            }

            // Quantity field
            OutlinedTextField(
                value = form.quantity,
                onValueChange = onUpdateQuantity,
                label = { Text(stringResource(Res.string.quantity)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Compatible unit selector
            if (form.stockId.isNotBlank()) {
                val compatibleUnits = getCompatibleUnits(form.baseUnit)
                Text(
                    text = stringResource(Res.string.unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    compatibleUnits.forEach { unitKey ->
                        FilterChip(
                            selected = form.unit.uppercase() == unitKey,
                            onClick = { onUpdateUnit(unitKey) },
                            label = { Text(getLocalizedUnit(unitKey)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChartCyan,
                                selectedLabelColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }

                // Fixed quantity toggle (no yield division)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = form.fixedQuantity,
                        onCheckedChange = onUpdateFixedQuantity,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.fixed_quantity_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                text = stringResource(Res.string.no_stock_items),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
