package net.marllex.waselak.kds.display

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import net.marllex.waselak.core.ui.components.WaselakTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.marllex.waselak.core.model.KdsOrder
import net.marllex.waselak.core.model.KdsOrderItem
import net.marllex.waselak.core.ui.components.EmptyView
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LanguageSelector
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.util.VariantDisplayHelper
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

private fun formatElapsedTime(minutes: Long): String {
    val hrs = minutes / 60
    val mins = minutes % 60
    val secs = 0L // Backend sends whole minutes, show :00 for seconds
    return if (hrs > 0) "%d:%02d:%02d".format(hrs, mins, secs)
    else "%02d:%02d".format(mins, secs)
}

private val ColorPending = Color(0xFFFF9800)
private val ColorCooking = Color(0xFF2196F3)
private val ColorReady = Color(0xFF4CAF50)
private val ColorServed = Color(0xFF9E9E9E)
private val ColorUrgent = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdsDisplayScreen(
    viewModel: KdsDisplayViewModel = koinViewModel(),
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }

    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(Res.string.close))
                }
            },
            text = { LanguageSelector() },
        )
    }

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = stringResource(Res.string.kitchen_display),
                isLoading = uiState.isLoading,
                onRefresh = viewModel::load,
                actions = {
                    // Summary badges
                    SummaryBadge(stringResource(Res.string.pending), uiState.summary.pending, ColorPending)
                    Spacer(Modifier.width(4.dp))
                    SummaryBadge(stringResource(Res.string.cooking), uiState.summary.cooking, ColorCooking)
                    Spacer(Modifier.width(4.dp))
                    SummaryBadge(stringResource(Res.string.ready), uiState.summary.ready, ColorReady)
                    Spacer(Modifier.width(8.dp))

                    // Station filter
                    val stations = uiState.orders.flatMap { o -> o.items.mapNotNull { it.kitchenStation } }.distinct()
                    if (stations.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = uiState.selectedStation == null,
                                onClick = { viewModel.onStationFilter(null) },
                                label = { Text(stringResource(Res.string.all)) },
                            )
                            stations.forEach { station ->
                                FilterChip(
                                    selected = uiState.selectedStation == station,
                                    onClick = { viewModel.onStationFilter(station) },
                                    label = { Text(station) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(Icons.Default.Language, contentDescription = null)
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            when {
                uiState.isLoading && uiState.orders.isEmpty() -> LoadingIndicator()
                uiState.error != null && uiState.orders.isEmpty() -> ErrorView(
                    message = uiState.error!!,
                    onRetry = viewModel::load,
                )
                uiState.orders.isEmpty() -> EmptyView(stringResource(Res.string.no_active_orders))
                else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = when {
                        maxWidth > 1600.dp -> 4
                        maxWidth > 1200.dp -> 3
                        maxWidth > 800.dp -> 2
                        else -> 1
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        items(uiState.orders, key = { it.orderId }) { order ->
                            KdsOrderCard(
                                order = order,
                                onItemStatusChange = { itemId, status ->
                                    viewModel.updateItemStatus(itemId, status)
                                },
                                onMarkAllReady = {
                                    viewModel.markAllReady(
                                        order.orderId,
                                        order.items.filter { it.isPending || it.isCooking }.map { it.id },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBadge(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun KdsOrderCard(
    order: KdsOrder,
    onItemStatusChange: (String, String) -> Unit,
    onMarkAllReady: () -> Unit,
) {
    val isUrgent = order.elapsedMinutes > 15
    val borderColor = when {
        isUrgent -> ColorUrgent
        order.hasPendingItems -> ColorPending
        order.hasCookingItems -> ColorCooking
        order.allReady -> ColorReady
        else -> ColorServed
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            // Top color bar
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(borderColor))

            Column(modifier = Modifier.padding(16.dp)) {
                // Header: Order # + Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    // Order number + channel
                    Column {
                        Text(
                            "#${order.orderNumber}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                order.channel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            order.tableNumber?.let {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        stringResource(Res.string.table_label, it),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // Timer
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isUrgent) ColorUrgent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isUrgent) ColorUrgent else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    formatElapsedTime(order.elapsedMinutes),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUrgent) ColorUrgent else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 22.sp,
                                )
                            }
                        }
                    }
                }

                // Client name
                order.clientName?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }

                // Notes
                order.notes?.let {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    ) {
                        Text(
                            stringResource(Res.string.note_prefix, it),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                // Items
                order.items.forEach { item ->
                    KdsItemRow(
                        item = item,
                        onStatusChange = { status -> onItemStatusChange(item.id, status) },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Mark All Ready button
                if (!order.allReady && !order.allServed) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onMarkAllReady,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorReady),
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.all_ready), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun KdsItemRow(item: KdsOrderItem, onStatusChange: (String) -> Unit) {
    val statusColor = when {
        item.isPending -> ColorPending
        item.isCooking -> ColorCooking
        item.isReady -> ColorReady
        item.isServed -> ColorServed
        else -> Color.Transparent
    }
    val bgColor = statusColor.copy(alpha = 0.08f)
    val nextStatus = when {
        item.isPending -> "COOKING"
        item.isCooking -> "READY"
        item.isReady -> "SERVED"
        else -> null
    }
    val buttonLabel = when {
        item.isPending -> stringResource(Res.string.start)
        item.isCooking -> stringResource(Res.string.ready)
        item.isReady -> stringResource(Res.string.served)
        else -> ""
    }
    val buttonColor = when {
        item.isPending -> ColorPending
        item.isCooking -> ColorCooking
        item.isReady -> ColorReady
        else -> ColorServed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Quantity badge
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${item.quantity}x",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Item details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.itemName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            }
            VariantDisplayHelper.formatVariantSummary(item.variantOptions)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.width(8.dp))

        // Status button or served check
        if (nextStatus != null) {
            Button(
                onClick = { onStatusChange(nextStatus) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(buttonLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        } else {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(Res.string.served),
                modifier = Modifier.size(28.dp),
                tint = ColorServed,
            )
        }
    }
}
