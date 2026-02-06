package net.marllex.cafeemanger.feature.cashier.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.core.ui.components.OrderStatusChip
import net.marllex.cafeemanger.core.ui.components.PaymentMethodChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onPaymentDone: () -> Unit = {},
    onNavigateToReceipt: (String) -> Unit = {},
    viewModel: PaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payment_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(
                                        R.string.order_number,
                                        order.id.takeLast(6).uppercase()
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                OrderStatusChip(status = order.status)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            PaymentMethodChip(method = order.paymentMethod.name)

                            Spacer(modifier = Modifier.height(12.dp))

                            order.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.item_quantity_name,
                                            item.quantity,
                                            item.itemNameSnapshot
                                        )
                                    )
                                    Text(
                                        stringResource(
                                            R.string.price_format,
                                            item.itemPriceSnapshot * item.quantity
                                        )
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            SummaryRow(
                                label = stringResource(R.string.subtotal),
                                value = order.subtotal,
                            )
                            SummaryRow(
                                label = stringResource(R.string.tax),
                                value = order.tax,
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.total),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.price_format, order.total),
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isProcessing,
                    ) {
                        Text(
                            if (uiState.isProcessing)
                                stringResource(R.string.processing)
                            else
                                stringResource(
                                    R.string.confirm_payment_amount,
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
        Text(stringResource(R.string.price_format, value))
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
            stringResource(R.string.payment_success),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onReceipt, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.view_receipt))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.new_order))
        }
    }
}
