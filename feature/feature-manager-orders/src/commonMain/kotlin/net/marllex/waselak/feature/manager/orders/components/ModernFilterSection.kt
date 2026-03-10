package net.marllex.waselak.feature.manager.orders.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.orders.generated.resources.Res
import net.marllex.waselak.feature.manager.orders.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.ui.components.formatStatusLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.ui.theme.*
import net.marllex.waselak.core.common.extensions.formatEpochMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernFilterSection(
    selectedChannel: String?,
    selectedStatus: String?,
    selectedCashierId: String?,
    selectedDeliveryUserId: String?,
    selectedTableId: String?,
    fromDate: Long?,
    toDate: Long?,
    cashiers: List<net.marllex.waselak.core.model.User>,
    deliveryUsers: List<net.marllex.waselak.core.model.User>,
    tables: List<net.marllex.waselak.core.model.Table>,
    hasActiveFilters: Boolean,
    onChannelSelected: (String?) -> Unit,
    onStatusSelected: (String?) -> Unit,
    onCashierSelected: (String?) -> Unit,
    onDeliverySelected: (String?) -> Unit,
    onTableSelected: (String?) -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit,
    onClearAll: () -> Unit,
    onShowDatePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Filter Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasActiveFilters)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasActiveFilters)
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                else
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                        )
                                    )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = if (hasActiveFilters)
                                Color.White
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(Res.string.filters),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hasActiveFilters)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        if (hasActiveFilters) {
                            val activeCount = listOfNotNull(
                                selectedChannel,
                                selectedStatus,
                                selectedCashierId,
                                selectedDeliveryUserId,
                                selectedTableId,
                                fromDate
                            ).size
                            Text(
                                text = "$activeCount ${stringResource(Res.string.active_filters)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.tap_to_filter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasActiveFilters) {
                        IconButton(
                            onClick = onClearAll,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.clear_filters),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationAngle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Expanded Filter Content with Vertical Scrolling
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp) // Max height to enable scrolling
                    .verticalScroll(rememberScrollState()) // Enable vertical scrolling
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Channel Filter
                FilterGroup(
                    title = stringResource(Res.string.channel),
                    icon = Icons.Default.Store
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        item {
                            ModernFilterChip(
                                label = stringResource(Res.string.all),
                                selected = selectedChannel == null,
                                onClick = { onChannelSelected(null) }
                            )
                        }
                        item {
                            ModernFilterChip(
                                label = stringResource(Res.string.channel_dine_in),
                                selected = selectedChannel == "DINE_IN",
                                onClick = { onChannelSelected("DINE_IN") },
                                icon = Icons.Default.Restaurant
                            )
                        }
                        item {
                            ModernFilterChip(
                                label = stringResource(Res.string.channel_delivery),
                                selected = selectedChannel == "DELIVERY",
                                onClick = { onChannelSelected("DELIVERY") },
                                icon = Icons.Default.DeliveryDining
                            )
                        }
                        item {
                            ModernFilterChip(
                                label = stringResource(Res.string.channel_takeaway),
                                selected = selectedChannel == "TAKEAWAY",
                                onClick = { onChannelSelected("TAKEAWAY") },
                                icon = Icons.Default.ShoppingBag
                            )
                        }
                        item {
                            ModernFilterChip(
                                label = stringResource(Res.string.channel_in_store),
                                selected = selectedChannel == "IN_STORE",
                                onClick = { onChannelSelected("IN_STORE") },
                                icon = Icons.Default.Store
                            )
                        }
                        item {
                            ModernFilterChip(
                                label = stringResource(Res.string.channel_pickup_later),
                                selected = selectedChannel == "PICKUP_LATER",
                                onClick = { onChannelSelected("PICKUP_LATER") },
                                icon = Icons.Default.Schedule
                            )
                        }
                    }
                }

                // Status Filter
                val visibleStatuses = when (selectedChannel) {
                    "DINE_IN" -> OrderStatus.getAvailableStatuses(OrderChannel.DINE_IN)
                    "DELIVERY" -> OrderStatus.getAvailableStatuses(OrderChannel.DELIVERY)
                    "TAKEAWAY" -> OrderStatus.getAvailableStatuses(OrderChannel.TAKEAWAY)
                    "IN_STORE" -> OrderStatus.getAvailableStatuses(OrderChannel.IN_STORE)
                    "PICKUP_LATER" -> OrderStatus.getAvailableStatuses(OrderChannel.PICKUP_LATER)
                    else -> OrderStatus.entries.toList()
                }

                FilterGroup(
                    title = stringResource(Res.string.status),
                    icon = Icons.Default.CheckCircle
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        item {
                            ModernFilterChip(
                                label = stringResource(Res.string.all),
                                selected = selectedStatus == null,
                                onClick = { onStatusSelected(null) }
                            )
                        }
                        items(visibleStatuses) { status ->
                            ModernFilterChip(
                                label = formatStatusLabel(status),
                                selected = selectedStatus == status.name,
                                onClick = { onStatusSelected(status.name) }
                            )
                        }
                    }
                }

                // Date Filter
                FilterGroup(
                    title = stringResource(Res.string.date_filter),
                    icon = Icons.Default.CalendarMonth
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        val now = Clock.System.now().toEpochMilliseconds()
                        val todayStart = Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                            .atStartOfDayIn(TimeZone.currentSystemDefault())
                            .toEpochMilliseconds()

                        item {
                            ModernFilterChip(
                                label = stringResource(Res.string.today),
                                selected = fromDate == todayStart,
                                onClick = { onDateRangeSelected(todayStart, now) },
                                icon = Icons.Default.Today
                            )
                        }
                        item {
                            ModernFilterChip(
                                label = if (fromDate != null && fromDate != todayStart) {
                                    "${fromDate.formatEpochMs("MMM dd")} - ${(toDate ?: now).formatEpochMs("MMM dd")}"
                                } else {
                                    stringResource(Res.string.custom_date)
                                },
                                selected = fromDate != null && fromDate != todayStart,
                                onClick = onShowDatePicker,
                                icon = Icons.Default.DateRange
                            )
                        }
                        if (fromDate != null) {
                            item {
                                ModernFilterChip(
                                    label = stringResource(Res.string.all_dates),
                                    selected = false,
                                    onClick = { onDateRangeSelected(null, null) }
                                )
                            }
                        }
                    }
                }

                // People Filter (Cashier & Delivery)
                if (cashiers.isNotEmpty() || deliveryUsers.isNotEmpty()) {
                    FilterGroup(
                        title = stringResource(Res.string.filter_by_person),
                        icon = Icons.Default.Person
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cashiers.isNotEmpty()) {
                                PersonDropdownFilter(
                                    label = stringResource(Res.string.cashier),
                                    selectedId = selectedCashierId,
                                    users = cashiers,
                                    allLabel = stringResource(Res.string.all_cashiers),
                                    onSelected = onCashierSelected,
                                    icon = Icons.Default.PointOfSale
                                )
                            }
                            if (deliveryUsers.isNotEmpty()) {
                                PersonDropdownFilter(
                                    label = stringResource(Res.string.delivery),
                                    selectedId = selectedDeliveryUserId,
                                    users = deliveryUsers,
                                    allLabel = stringResource(Res.string.all_delivery),
                                    onSelected = onDeliverySelected,
                                    icon = Icons.Default.DeliveryDining
                                )
                            }
                        }
                    }
                }

                // Table Filter
                if (tables.isNotEmpty()) {
                    FilterGroup(
                        title = stringResource(Res.string.table_filter),
                        icon = Icons.Default.TableBar
                    ) {
                        TableDropdownFilter(
                            selectedTableId = selectedTableId,
                            tables = tables,
                            allLabel = stringResource(Res.string.all_tables),
                            onSelected = onTableSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun ModernFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surface,
        border = if (!selected)
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        else null,
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PersonDropdownFilter(
    label: String,
    selectedId: String?,
    users: List<net.marllex.waselak.core.model.User>,
    allLabel: String,
    onSelected: (String?) -> Unit,
    icon: ImageVector
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedUser = users.find { it.id == selectedId }

    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (selectedId != null)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (selectedId != null)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selectedId != null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedUser?.name ?: allLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedId != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
                leadingIcon = {
                    Icon(Icons.Default.SelectAll, contentDescription = null)
                }
            )
            HorizontalDivider()
            users.forEach { user ->
                DropdownMenuItem(
                    text = { Text(user.name) },
                    onClick = {
                        expanded = false
                        onSelected(user.id)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun TableDropdownFilter(
    selectedTableId: String?,
    tables: List<net.marllex.waselak.core.model.Table>,
    allLabel: String,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTable = tables.find { it.id == selectedTableId }

    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (selectedTableId != null)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (selectedTableId != null)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.TableBar,
                        contentDescription = null,
                        tint = if (selectedTableId != null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.table_filter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedTable?.let { "#${it.number}" } ?: allLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedTableId != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
                leadingIcon = {
                    Icon(Icons.Default.SelectAll, contentDescription = null)
                }
            )
            HorizontalDivider()
            tables.forEach { table ->
                DropdownMenuItem(
                    text = { Text("#${table.number}") },
                    onClick = {
                        expanded = false
                        onSelected(table.id)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.TableBar, contentDescription = null)
                    }
                )
            }
        }
    }
}
