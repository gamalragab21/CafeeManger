package net.marllex.cafeemanger.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.ui.R
import net.marllex.cafeemanger.core.ui.theme.*

@Composable
fun OrderStatusChip(
    status: OrderStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = getStatusColors(status)

    Text(
        text = formatStatusLabel(status),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun formatStatusLabel(status: OrderStatus): String {
    return when (status) {
        OrderStatus.CREATED -> stringResource(R.string.status_created)
        OrderStatus.IN_PREPARATION -> stringResource(R.string.status_in_preparation)
        OrderStatus.READY -> stringResource(R.string.status_ready)
        OrderStatus.ASSIGNED -> stringResource(R.string.status_assigned)
        OrderStatus.OUT_FOR_DELIVERY -> stringResource(R.string.status_out_for_delivery)
        OrderStatus.DELIVERED -> stringResource(R.string.status_delivered)
        OrderStatus.COMPLETED -> stringResource(R.string.status_completed)
        OrderStatus.CANCELED -> stringResource(R.string.status_canceled)
    }
}

@Composable
fun ChannelChip(
    channel: String,
    modifier: Modifier = Modifier
) {
    val chipColor = when (channel) {
        "DINE_IN" -> DineInColor
        "DELIVERY" -> DeliveryChannelColor
        else -> Color.Gray
    }

    Text(
        text = when (channel) {
            "DINE_IN" -> stringResource(R.string.channel_dine_in)
            "DELIVERY" -> stringResource(R.string.channel_delivery)
            else -> channel
        },
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
    method: String,
    modifier: Modifier = Modifier
) {
    val label = when (method) {
        "CASH" -> stringResource(R.string.payment_cash)
        "WALLET" -> stringResource(R.string.payment_wallet)
        "CARD" -> stringResource(R.string.payment_card)
        else -> method
    }

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

@Composable
private fun getStatusColors(status: OrderStatus): Pair<Color, Color> {
    return when (status) {
        OrderStatus.CREATED -> StatusCreated to StatusCreated
        OrderStatus.IN_PREPARATION -> StatusInPreparation to StatusInPreparation
        OrderStatus.READY -> StatusReady to StatusReady
        OrderStatus.ASSIGNED -> StatusAssigned to StatusAssigned
        OrderStatus.OUT_FOR_DELIVERY -> StatusOutForDelivery to StatusOutForDelivery
        OrderStatus.DELIVERED -> StatusDelivered to StatusDelivered
        OrderStatus.COMPLETED -> StatusCompleted to StatusCompleted
        OrderStatus.CANCELED -> StatusCanceled to StatusCanceled
    }
}

