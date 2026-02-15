package net.marllex.waselak.feature.cashier.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.cashier.payment.generated.resources.Res
import net.marllex.waselak.feature.cashier.payment.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.OrderStatusChip
import net.marllex.waselak.core.ui.components.PaymentMethodChip
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onPaymentDone: () -> Unit = {},
    onNavigateToReceipt: (String) -> Unit = {},
    viewModel: PaymentViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.payment_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()

            uiState.paymentCompleted -> PaymentSuccess(
                orderId = uiState.order?.id.orEmpty(),
                onDone = onPaymentDone,
                onReceipt = { onNavigateToReceipt(uiState.order?.id.orEmpty()) },
            )

            uiState.order != null -> {
                val order = uiState.order!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(
                                        Res.string.order_number,
                                        order.id.takeLast(6).uppercase()
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                OrderStatusChip(status = order.status)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            PaymentMethodChip(label = order.paymentMethod.name)

                            Spacer(modifier = Modifier.height(12.dp))

                            order.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        stringResource(
                                            Res.string.item_quantity_name,
                                            item.quantity,
                                            item.itemNameSnapshot
                                        )
                                    )
                                    Text(
                                        stringResource(
                                            Res.string.price_format,
                                            item.itemPriceSnapshot * item.quantity
                                        )
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            SummaryRow(
                                label = stringResource(Res.string.subtotal),
                                value = order.subtotal,
                            )
                            SummaryRow(
                                label = stringResource(Res.string.tax),
                                value = order.tax,
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(Res.string.total),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(Res.string.price_format, order.total),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = viewModel::completePayment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !uiState.isProcessing,
                    ) {
                        Text(
                            if (uiState.isProcessing)
                                stringResource(Res.string.processing)
                            else
                                stringResource(
                                    Res.string.confirm_payment_amount,
                                    order.total
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(stringResource(Res.string.price_format, value))
    }
}

@Composable
private fun PaymentSuccess(
    orderId: String,
    onDone: () -> Unit,
    onReceipt: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            stringResource(Res.string.payment_success),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onReceipt,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(Res.string.view_receipt))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(Res.string.new_order))
        }
    }
}
