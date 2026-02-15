package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.ui.theme.*
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.*

@Composable
fun OrderStatusChip(
    status: OrderStatus,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val chipColor = getStatusColor(status)
    val displayLabel = label ?: formatStatusLabel(status)

    Text(
        text = displayLabel,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = chipColor,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipColor.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun ChannelChip(
    channel: String,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val chipColor = when (channel) {
        "DINE_IN" -> DineInColor
        "DELIVERY" -> DeliveryChannelColor
        else -> Color.Gray
    }
    val displayLabel = label ?: when (channel) {
        "DINE_IN" -> stringResource(Res.string.channel_dine_in)
        "DELIVERY" -> stringResource(Res.string.channel_delivery)
        else -> channel
    }

    Text(
        text = displayLabel,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = chipColor,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipColor.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun PaymentMethodChip(
    label: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

fun getStatusColor(status: OrderStatus): Color {
    return when (status) {
        OrderStatus.CREATED -> StatusCreated
        OrderStatus.IN_PREPARATION -> StatusInPreparation
        OrderStatus.READY -> StatusReady
        OrderStatus.ON_TABLE -> StatusOnTable
        OrderStatus.ASSIGNED -> StatusAssigned
        OrderStatus.OUT_FOR_DELIVERY -> StatusOutForDelivery
        OrderStatus.DELIVERED -> StatusDelivered
        OrderStatus.COMPLETED -> StatusCompleted
        OrderStatus.CANCELED -> StatusCanceled
    }
}

/**
 * Returns a localized display label for the given [OrderStatus].
 * Can be used in filter chips, status labels, etc.
 */
@Composable
fun formatStatusLabel(status: OrderStatus): String {
    return when (status) {
        OrderStatus.CREATED -> stringResource(Res.string.status_created)
        OrderStatus.IN_PREPARATION -> stringResource(Res.string.status_in_preparation)
        OrderStatus.READY -> stringResource(Res.string.status_ready)
        OrderStatus.ON_TABLE -> stringResource(Res.string.status_on_table)
        OrderStatus.ASSIGNED -> stringResource(Res.string.status_assigned)
        OrderStatus.OUT_FOR_DELIVERY -> stringResource(Res.string.status_out_for_delivery)
        OrderStatus.DELIVERED -> stringResource(Res.string.status_delivered)
        OrderStatus.COMPLETED -> stringResource(Res.string.status_completed)
        OrderStatus.CANCELED -> stringResource(Res.string.status_canceled)
    }
}
