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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.marllex.cafeemanger.core.model.OrderStatus
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
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun ChannelChip(
    channel: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (channel) {
        "DINE_IN" -> DineInColor to Color.White
        "DELIVERY" -> DeliveryChannelColor to Color.White
        else -> Color.Gray to Color.White
    }

    Text(
        text = when (channel) {
            "DINE_IN" -> "Dine-In"
            "DELIVERY" -> "Delivery"
            else -> channel
        },
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = bgColor,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun PaymentMethodChip(
    method: String,
    modifier: Modifier = Modifier
) {
    val label = when (method) {
        "CASH" -> "Cash"
        "WALLET" -> "Wallet"
        "CARD" -> "Card"
        else -> method
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun getStatusColors(status: OrderStatus): Pair<Color, Color> {
    return when (status) {
        OrderStatus.CREATED -> StatusCreated to StatusCreated
        OrderStatus.CONFIRMED -> StatusConfirmed to StatusConfirmed
        OrderStatus.IN_PREPARATION -> StatusInPreparation to StatusInPreparation
        OrderStatus.SERVED -> StatusServed to StatusServed
        OrderStatus.READY -> StatusReady to StatusReady
        OrderStatus.ASSIGNED -> StatusAssigned to StatusAssigned
        OrderStatus.OUT_FOR_DELIVERY -> StatusOutForDelivery to StatusOutForDelivery
        OrderStatus.DELIVERED -> StatusDelivered to StatusDelivered
        OrderStatus.COMPLETED -> StatusCompleted to StatusCompleted
        OrderStatus.CANCELED -> StatusCanceled to StatusCanceled
    }
}

private fun formatStatusLabel(status: OrderStatus): String {
    return status.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
}
