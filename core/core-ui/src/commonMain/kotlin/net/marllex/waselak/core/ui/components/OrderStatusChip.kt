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
import net.marllex.waselak.core.model.PaymentStatus
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
        "TAKEAWAY" -> TakeawayColor
        "IN_STORE" -> InStoreColor
        "PICKUP_LATER" -> PickupLaterColor
        else -> Color.Gray
    }
    val displayLabel = label ?: when (channel) {
        "DINE_IN" -> stringResource(Res.string.channel_dine_in)
        "DELIVERY" -> stringResource(Res.string.channel_delivery)
        "TAKEAWAY" -> stringResource(Res.string.channel_takeaway)
        "IN_STORE" -> stringResource(Res.string.channel_in_store)
        "PICKUP_LATER" -> stringResource(Res.string.channel_pickup_later)
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

@Composable
fun PaymentStatusChip(
    status: PaymentStatus,
    modifier: Modifier = Modifier
) {
    val chipColor = getPaymentStatusColor(status)
    val displayLabel = formatPaymentStatusLabel(status)

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

fun getPaymentStatusColor(status: PaymentStatus): Color {
    return when (status) {
        PaymentStatus.PENDING -> PaymentPending
        PaymentStatus.PAID -> PaymentPaid
        PaymentStatus.PARTIALLY_PAID -> PaymentPartiallyPaid
        PaymentStatus.REFUNDED -> PaymentRefunded
        PaymentStatus.FAILED -> PaymentFailed
    }
}

@Composable
fun formatPaymentStatusLabel(status: PaymentStatus): String {
    return when (status) {
        PaymentStatus.PENDING -> stringResource(Res.string.payment_status_pending)
        PaymentStatus.PAID -> stringResource(Res.string.payment_status_paid)
        PaymentStatus.PARTIALLY_PAID -> stringResource(Res.string.payment_status_partially_paid)
        PaymentStatus.REFUNDED -> stringResource(Res.string.payment_status_refunded)
        PaymentStatus.FAILED -> stringResource(Res.string.payment_status_failed)
    }
}

fun getStatusColor(status: OrderStatus): Color {
    return when (status) {
        OrderStatus.CREATED -> StatusCreated
        OrderStatus.IN_PROGRESS -> StatusInPreparation
        OrderStatus.IN_PREPARATION -> StatusInPreparation
        OrderStatus.READY -> StatusReady
        OrderStatus.SERVED -> StatusServed
        OrderStatus.ASSIGNED -> StatusAssigned
        OrderStatus.OUT_FOR_DELIVERY -> StatusOutForDelivery
        OrderStatus.DELIVERED -> StatusDelivered
        OrderStatus.DELIVERY_FAILED -> StatusDeliveryFailed
        OrderStatus.RETURNED -> StatusReturned
        OrderStatus.PICKED_UP -> StatusPickedUp
        OrderStatus.COMPLETED -> StatusCompleted
        OrderStatus.CANCELED -> StatusCanceled
        OrderStatus.REFUNDED -> StatusRefunded
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
        OrderStatus.IN_PROGRESS -> stringResource(Res.string.status_in_preparation)
        OrderStatus.IN_PREPARATION -> stringResource(Res.string.status_in_preparation)
        OrderStatus.READY -> stringResource(Res.string.status_ready)
        OrderStatus.SERVED -> stringResource(Res.string.status_served)
        OrderStatus.ASSIGNED -> stringResource(Res.string.status_assigned)
        OrderStatus.OUT_FOR_DELIVERY -> stringResource(Res.string.status_out_for_delivery)
        OrderStatus.DELIVERED -> stringResource(Res.string.status_delivered)
        OrderStatus.DELIVERY_FAILED -> stringResource(Res.string.status_delivery_failed)
        OrderStatus.RETURNED -> stringResource(Res.string.status_returned)
        OrderStatus.PICKED_UP -> stringResource(Res.string.status_picked_up)
        OrderStatus.COMPLETED -> stringResource(Res.string.status_completed)
        OrderStatus.CANCELED -> stringResource(Res.string.status_canceled)
        OrderStatus.REFUNDED -> stringResource(Res.string.status_refunded)
    }
}

/**
 * Returns the localized channel label based on business type.
 * Use this everywhere instead of hardcoding channel names.
 */
@Composable
fun formatChannelLabel(channel: net.marllex.waselak.core.model.OrderChannel, businessType: String? = null): String =
    when (channel) {
        net.marllex.waselak.core.model.OrderChannel.DINE_IN -> when (businessType) {
            "PHARMACY" -> stringResource(Res.string.channel_direct_dispense)
            else -> stringResource(Res.string.channel_dine_in)
        }
        net.marllex.waselak.core.model.OrderChannel.DELIVERY -> stringResource(Res.string.channel_delivery)
        net.marllex.waselak.core.model.OrderChannel.TAKEAWAY -> stringResource(Res.string.channel_takeaway)
        net.marllex.waselak.core.model.OrderChannel.IN_STORE -> stringResource(Res.string.channel_in_store)
        net.marllex.waselak.core.model.OrderChannel.PICKUP_LATER -> stringResource(Res.string.channel_pickup_later)
    }
