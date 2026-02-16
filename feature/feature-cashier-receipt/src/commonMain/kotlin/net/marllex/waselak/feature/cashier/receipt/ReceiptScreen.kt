package net.marllex.waselak.feature.cashier.receipt

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.ui.components.LoadingIndicator
import org.koin.compose.viewmodel.koinViewModel
import qrgenerator.qrkitpainter.rememberQrKitPainter

@Composable
private fun localizedChannel(channel: OrderChannel): String = when (channel) {
    OrderChannel.DINE_IN -> "Dine In"
    OrderChannel.DELIVERY -> "Delivery"
}

@Composable
private fun localizedPayment(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.WALLET -> "Wallet"
    PaymentMethod.CARD -> "Card"
}

private fun formatAmount(amount: Double, currency: String = "EGP"): String {
    val rounded = kotlin.math.round(amount * 100) / 100.0
    val whole = rounded.toLong()
    val frac = ((rounded - whole) * 100 + 0.5).toInt()
    return "$whole.${frac.toString().padStart(2, '0')} $currency"
}

private fun formatDate(epochMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${
        dt.dayOfMonth.toString().padStart(2, '0')
    }  ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiptScreen(
    onBack: () -> Unit = {},
) {
    val viewModel: ReceiptViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showQr by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.order != null -> {
                val order = uiState.order!!
                val vendor = uiState.vendor

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Header with Logo
                                if (!vendor?.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = vendor?.logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Color(0xFFE7E5E4), CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                Text(
                                    text = vendor?.name ?: "Restaurant",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF1C1917),
                                )
                                vendor?.address?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = Color(0xFF78716C),
                                    )
                                }

                                Spacer(Modifier.height(16.dp))
                                DashedDivider()
                                Spacer(Modifier.height(16.dp))

                                // Order Info
                                ReceiptInfoSection {
                                    ReceiptDataRow(
                                        "Order #",
                                        "#${order.id.takeLast(8).uppercase()}",
                                        isBold = true,
                                    )
                                    ReceiptDataRow("Date", formatDate(order.createdAt))
                                    ReceiptDataRow("Channel", localizedChannel(order.channel))
                                    ReceiptDataRow("Payment", localizedPayment(order.paymentMethod))
                                    ReceiptDataRow("Cashier", order.cashierName ?: "-")
                                }

                                // Client Info
                                if (order.clientName != null || order.clientPhone != null || order.clientAddress != null) {
                                    Spacer(Modifier.height(12.dp))
                                    ReceiptInfoSection {
                                        order.clientName?.let { ReceiptDataRow("Client", it) }
                                        order.clientPhone?.let { ReceiptDataRow("Phone", it) }
                                        order.clientAddress?.let { ReceiptDataRow("Address", it) }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                DashedDivider()
                                Spacer(Modifier.height(16.dp))

                                // Items table header
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Item", style = MaterialTheme.typography.labelMedium, color = Color(0xFF78716C), modifier = Modifier.weight(1f))
                                    Text("Qty", style = MaterialTheme.typography.labelMedium, color = Color(0xFF78716C), textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
                                    Text("Price", style = MaterialTheme.typography.labelMedium, color = Color(0xFF78716C), textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFFE7E5E4), thickness = 0.5.dp)
                                Spacer(Modifier.height(8.dp))

                                order.items.forEach { item ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(item.itemNameSnapshot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF1C1917), modifier = Modifier.weight(1f))
                                        Text("${item.quantity}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF57534E), textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
                                        Text(formatAmount(item.itemPriceSnapshot * item.quantity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF1C1917), textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                DashedDivider()
                                Spacer(Modifier.height(16.dp))

                                // Totals
                                ReceiptDataRow("Subtotal", formatAmount(order.subtotal))
                                Spacer(Modifier.height(4.dp))
                                if (order.tax > 0.0) {
                                    ReceiptDataRow("Tax", formatAmount(order.tax))
                                    Spacer(Modifier.height(4.dp))
                                }
                                if (order.deliveryFee > 0.0) {
                                    ReceiptDataRow("Delivery Fee", formatAmount(order.deliveryFee))
                                    Spacer(Modifier.height(4.dp))
                                }

                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFFE7E5E4))
                                Spacer(Modifier.height(12.dp))

                                // Grand Total
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF0FDFA), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFCCFBF1), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1C1917))
                                    Text(formatAmount(order.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                                }

                                // Notes
                                order.notes?.let { notes ->
                                    Spacer(Modifier.height(16.dp))
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFAFAF9), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                    ) {
                                        Text("Notes", style = MaterialTheme.typography.labelSmall, color = Color(0xFF78716C))
                                        Spacer(Modifier.height(2.dp))
                                        Text(notes, style = MaterialTheme.typography.bodySmall, color = Color(0xFF44403C))
                                    }
                                }

                                Spacer(Modifier.height(20.dp))

                                // Receipt QR Code (link from backend)
                                val receiptUrl = uiState.shareUrl
                                if (!receiptUrl.isNullOrBlank()) {
                                    DashedDivider()
                                    Spacer(Modifier.height(12.dp))
                                    Image(
                                        painter = rememberQrKitPainter(data = receiptUrl),
                                        contentDescription = "Receipt QR Code",
                                        modifier = Modifier.size(100.dp),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Scan to view receipt",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA8A29E),
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                } else if (uiState.isSharing) {
                                    DashedDivider()
                                    Spacer(Modifier.height(12.dp))
                                    CircularProgressIndicator(Modifier.size(32.dp))
                                    Spacer(Modifier.height(12.dp))
                                }

                                // Footer
                                Text(
                                    text = "Thank you for your visit!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFFA8A29E),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Action Buttons
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 2,
                        ) {
                            val btnMod = Modifier.weight(1f).height(48.dp)
                            OutlinedButton(
                                onClick = { showQr = true; viewModel.generateShareLink() },
                                modifier = btnMod,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(Icons.Default.QrCode, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("QR", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { showQr = true; viewModel.generateShareLink() },
                                modifier = btnMod,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text("Done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // QR Dialog
    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text("OK") }
            },
            title = {
                Text(
                    "Share Receipt",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val shareUrl = uiState.shareUrl
                    if (uiState.isSharing || shareUrl == null) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                    } else {
                        Surface(
                            Modifier.size(220.dp).padding(8.dp),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 2.dp,
                        ) {
                            Image(
                                painter = rememberQrKitPainter(data = shareUrl),
                                contentDescription = "QR",
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Scan to view receipt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ReceiptInfoSection(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFAFAF9), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun DashedDivider(
    modifier: Modifier = Modifier,
    dashWidth: Dp = 6.dp,
    dashGap: Dp = 4.dp,
    thickness: Dp = 1.dp,
    color: Color = Color(0xFFD6D3D1),
) {
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(thickness)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = thickness.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashWidth.toPx(), dashGap.toPx()), 0f),
        )
    }
}

@Composable
private fun ReceiptDataRow(label: String, value: String, isBold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF78716C))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium, color = Color(0xFF1C1917))
    }
}
