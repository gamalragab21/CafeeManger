package net.marllex.cafeemanger.feature.delivery.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.core.ui.components.OrderStatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryStatusScreen(
    onBack: () -> Unit = {},
    onNavigateToMap: (Double?, Double?) -> Unit = { _, _ -> },
    viewModel: DeliveryStatusViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.order_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorView(message = uiState.error!!, onRetry = viewModel::loadOrder)
            uiState.order != null -> {
                val order = uiState.order!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Status
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("#${order.id.takeLast(6).uppercase()}", style = MaterialTheme.typography.titleMedium)
                                OrderStatusChip(status = order.status)
                            }
                        }
                    }

                    // Client info
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.client_information), style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            order.clientName?.let {
                                Text(text = it, style = MaterialTheme.typography.bodyLarge)
                            }
                            order.clientPhone?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(text = " $it", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            order.clientAddress?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(text = " $it", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (order.geoLat != null && order.geoLng != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { onNavigateToMap(order.geoLat, order.geoLng) }, shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                                    Text(" Open in Maps")
                                }
                            }
                        }
                    }

                    // Order items
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.order_items), style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            order.items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("${item.quantity}x ${item.itemNameSnapshot}")
                                    Text(String.format("%.2f", item.itemPriceSnapshot * item.quantity))
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium)
                                Text(String.format("%.2f", order.total), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    order.notes?.let {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Notes", style = MaterialTheme.typography.titleSmall)
                                Text(text = it, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // Action buttons
                    when (order.status) {
                        OrderStatus.ASSIGNED -> {
                            Button(
                                onClick = { viewModel.updateStatus(OrderStatus.OUT_FOR_DELIVERY) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isUpdating,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.DeliveryDining, contentDescription = null)
                                Text(if (uiState.isUpdating) stringResource(R.string.updating) else stringResource(
                                    R.string.start_delivery
                                ))
                            }
                        }
                        OrderStatus.OUT_FOR_DELIVERY -> {
                            Button(
                                onClick = { viewModel.updateStatus(OrderStatus.DELIVERED) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isUpdating,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                Text(if (uiState.isUpdating) stringResource(R.string.updating) else stringResource(
                                    R.string.mark_as_delivered
                                ))
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
