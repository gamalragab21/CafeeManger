package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.cancelled
import waselak.core.core_ui.generated.resources.no_orders_this_shift
import waselak.core.core_ui.generated.resources.orders_count
import waselak.core.core_ui.generated.resources.payment_card
import waselak.core.core_ui.generated.resources.payment_cash
import waselak.core.core_ui.generated.resources.payment_wallet
import waselak.core.core_ui.generated.resources.refunded
import waselak.core.core_ui.generated.resources.retry
import waselak.core.core_ui.generated.resources.shift_summary
import waselak.core.core_ui.generated.resources.signout
import waselak.core.core_ui.generated.resources.total_revenue
import waselak.core.core_ui.generated.resources.shift_summary_today
import waselak.core.core_ui.generated.resources.shift_summary_session
import waselak.core.core_ui.generated.resources.installments

data class ShiftSummaryUiModel(
    val totalRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val cashRevenue: Double = 0.0,
    val walletRevenue: Double = 0.0,
    val cardRevenue: Double = 0.0,
    val cashOrders: Int = 0,
    val walletOrders: Int = 0,
    val cardOrders: Int = 0,
    val cancelledTotal: Double = 0.0,
    val cancelledCount: Int = 0,
    val refundedTotal: Double = 0.0,
    val refundedCount: Int = 0,
    val installmentPayments: Double = 0.0,
    val installmentPaymentCount: Int = 0,
)

private fun formatAmount(amount: Double): String {
    val whole = amount.toLong()
    val frac = ((amount - whole) * 100).toLong()
    return "$whole.${frac.toString().padStart(2, '0')} EGP"
}

enum class ShiftSummaryTab { TODAY, SESSION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftSummaryBottomSheet(
    shiftSummary: ShiftSummaryUiModel?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onTabChanged: (ShiftSummaryTab) -> Unit = {},
    onSignOut: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(ShiftSummaryTab.TODAY) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header
            Text(
                text = stringResource(Res.string.shift_summary),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            // Tab Row: Today / Session
            TabRow(
                selectedTabIndex = if (selectedTab == ShiftSummaryTab.TODAY) 0 else 1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTab == ShiftSummaryTab.TODAY,
                    onClick = {
                        selectedTab = ShiftSummaryTab.TODAY
                        onTabChanged(ShiftSummaryTab.TODAY)
                    },
                    text = { Text(stringResource(Res.string.shift_summary_today)) },
                )
                Tab(
                    selected = selectedTab == ShiftSummaryTab.SESSION,
                    onClick = {
                        selectedTab = ShiftSummaryTab.SESSION
                        onTabChanged(ShiftSummaryTab.SESSION)
                    },
                    text = { Text(stringResource(Res.string.shift_summary_session)) },
                )
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = onRetry) {
                                Text(stringResource(Res.string.retry))
                            }
                        }
                    }
                }

                shiftSummary == null -> {
                    // Data not yet loaded — show loading
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    val hasOrders = shiftSummary.totalOrders > 0 ||
                        shiftSummary.cancelledCount > 0 ||
                        shiftSummary.refundedCount > 0 ||
                        shiftSummary.installmentPaymentCount > 0

                    if (!hasOrders) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.no_orders_this_shift),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // Total Revenue - prominent
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(Res.string.total_revenue),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatAmount(shiftSummary.totalRevenue),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.orders_count, shiftSummary.totalOrders),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        // Payment method breakdown
                        PaymentRow(
                            icon = Icons.Rounded.Payments,
                            label = stringResource(Res.string.payment_cash),
                            count = shiftSummary.cashOrders,
                            amount = shiftSummary.cashRevenue,
                        )
                        PaymentRow(
                            icon = Icons.Rounded.Wallet,
                            label = stringResource(Res.string.payment_wallet),
                            count = shiftSummary.walletOrders,
                            amount = shiftSummary.walletRevenue,
                        )
                        PaymentRow(
                            icon = Icons.Rounded.CreditCard,
                            label = stringResource(Res.string.payment_card),
                            count = shiftSummary.cardOrders,
                            amount = shiftSummary.cardRevenue,
                        )

                        // Installment payments
                        if (shiftSummary.installmentPaymentCount > 0) {
                            PaymentRow(
                                icon = Icons.Rounded.Schedule,
                                label = stringResource(Res.string.installments),
                                count = shiftSummary.installmentPaymentCount,
                                amount = shiftSummary.installmentPayments,
                            )
                        }

                        // Cancelled & Refunded
                        if (shiftSummary.cancelledCount > 0 || shiftSummary.refundedCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))

                            if (shiftSummary.cancelledCount > 0) {
                                PaymentRow(
                                    icon = Icons.Rounded.Block,
                                    label = stringResource(Res.string.cancelled),
                                    count = shiftSummary.cancelledCount,
                                    amount = shiftSummary.cancelledTotal,
                                    isNegative = true,
                                )
                            }
                            if (shiftSummary.refundedCount > 0) {
                                PaymentRow(
                                    icon = Icons.Rounded.Replay,
                                    label = stringResource(Res.string.refunded),
                                    count = shiftSummary.refundedCount,
                                    amount = shiftSummary.refundedTotal,
                                    isNegative = true,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sign Out button (only shown when onSignOut is provided)
            if (onSignOut != null) {
                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.signout))
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(
    icon: ImageVector,
    label: String,
    count: Int,
    amount: Double,
    isNegative: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isNegative) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isNegative) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = stringResource(Res.string.orders_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (isNegative) "-${formatAmount(amount)}" else formatAmount(amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isNegative) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
