package net.marllex.waselak.feature.manager.staff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.BoxWithConstraints
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.staff.generated.resources.Res
import net.marllex.waselak.feature.manager.staff.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.model.DeliveryAvailability
import net.marllex.waselak.core.model.DeliveryOrderSummary
import net.marllex.waselak.core.model.DeliveryPersonStatus
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.FeatureNotAvailableView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.OrderStatusChip
import org.koin.compose.viewmodel.koinViewModel
import net.marllex.waselak.core.common.extensions.formatEpochMs
import net.marllex.waselak.core.common.utils.CurrencyFormatter

@Composable
fun DeliveryDashboardScreen(
    viewModel: DeliveryDashboardViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    BoxWithConstraints {
    val isTablet = maxWidth >= 600.dp

    when {
        uiState.showFeatureNotAvailable -> FeatureNotAvailableView(
            message = uiState.featureNotAvailableMessage,
        )
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null && uiState.deliveryPersons.isEmpty() -> ErrorView(
            message = uiState.error!!,
            onRetry = viewModel::loadDashboard,
        )
        uiState.deliveryPersons.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.DeliveryDining,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.no_delivery_persons),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isTablet) Modifier.widthIn(max = 840.dp) else Modifier),
                    contentPadding = PaddingValues(if (isTablet) 24.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.deliveryPersons, key = { it.userId }) { person ->
                        DeliveryPersonCard(person = person)
                    }
                }
            }
        }
    }
    } // BoxWithConstraints

}

@Composable
private fun DeliveryPersonCard(person: DeliveryPersonStatus) {
    var expanded by remember { mutableStateOf(false) }
    val isAvailable = person.status == DeliveryAvailability.AVAILABLE
    val statusColor = if (isAvailable) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (person.activeOrders.isNotEmpty()) expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.DeliveryDining,
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = statusColor,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Phone,
                            null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = person.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Status chip
                Text(
                    text = if (isAvailable) {
                        stringResource(Res.string.available)
                    } else {
                        stringResource(Res.string.busy)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )

                if (person.activeOrders.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Order count
            if (person.activeOrders.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.active_orders_count, person.activeOrderCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp, bottom = 8.dp),
                )
            } else {
                Text(
                    text = stringResource(Res.string.no_active_orders),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp, bottom = 8.dp),
                )
            }

            // Expandable active orders
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    person.activeOrders.forEach { order ->
                        ActiveOrderRow(order = order)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOrderRow(order: DeliveryOrderSummary) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${order.orderId.takeLast(6).uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                OrderStatusChip(status = order.status)
            }
            if (order.clientName != null) {
                Text(
                    text = order.clientName?:"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (order.clientAddress != null) {
                Text(
                    text = order.clientAddress?:"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = CurrencyFormatter.formatDecimal(order.total),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = order.createdAt.formatEpochMs("hh:mm a"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
