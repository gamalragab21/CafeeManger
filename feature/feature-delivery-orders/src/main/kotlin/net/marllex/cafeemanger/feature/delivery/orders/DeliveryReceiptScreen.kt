package net.marllex.cafeemanger.feature.delivery.orders

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.Vendor
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeliveryReceiptScreen(
    onBack: () -> Unit = {},
    viewModel: DeliveryReceiptViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.order != null -> {
                val order = uiState.order!!
                val vendor = uiState.vendor
                val dateStr = formatDate(order.createdAt)
                // Allow sharing/QR even if vendor is still null; bitmap handles null gracefully
                val ready = !uiState.isLoading
                var showQr by remember { mutableStateOf(false) }
                var pendingShare by remember { mutableStateOf(false) }

                // Trigger share once link is ready
                androidx.compose.runtime.LaunchedEffect(uiState.shareUrl, pendingShare) {
                    if (pendingShare && uiState.shareUrl != null && ready) {
                        val bmp = renderReceiptBitmap(order, vendor, uiState.shareUrl)
                        shareImage(context, bmp)
                        pendingShare = false
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // --- The "Paper" Receipt Card ---
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 500.dp), // Prevents receipt from looking weirdly wide on tablets
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                            shape = RectangleShape // Receipt look
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Header
                                Text(
                                    text = vendor?.name ?: "Restaurant",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                vendor?.address?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }

                                DashedDivider(
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )

                                // Metadata Rows (Helper function for cleaner code)
                                ReceiptDataRow(
                                    "Order #",
                                    order.id.takeLast(8).uppercase(),
                                    isBold = true
                                )
                                ReceiptDataRow(
                                    "Channel",
                                    order.channel.name
                                )
                                ReceiptDataRow(
                                    "Payment",
                                    order.paymentMethod.name
                                )
                                ReceiptDataRow(
                                    "Date",
                                    formatDate(order.createdAt)
                                )
                                ReceiptDataRow("Cashier", order.cashierName ?: "-")

                                DashedDivider(modifier = Modifier.padding(vertical = 16.dp))

                                // Item List
                                order.items.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.quantity}x ${item.itemNameSnapshot}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = formatAmount(item.itemPriceSnapshot * item.quantity),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace // Monospace for prices looks pro
                                        )
                                    }
                                }

                                DashedDivider(modifier = Modifier.padding(vertical = 16.dp))

                                // Totals
                                ReceiptDataRow(
                                    stringResource(R.string.subtotal),
                                    formatAmount(order.subtotal)
                                )
                                ReceiptDataRow("Tax", formatAmount(order.tax))

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.total),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = formatAmount(order.total),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.thank_you_for_your_visit),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // --- Action Buttons Section ---
                        // Using FlowRow to prevent text squishing on small phones
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 500.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            val btnModifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 100.dp)

                            OutlinedButton(onClick = {
                                showQr = true; viewModel.generateShareLink()
                            }, modifier = btnModifier) {
                                Icon(Icons.Default.QrCode, null)
                                Spacer(Modifier.width(4.dp))
                                Text("QR")
                            }
                            OutlinedButton(
                                onClick = { pendingShare = true; viewModel.generateShareLink() },
                                enabled = ready,
                                modifier = btnModifier
                            ) {
                                Icon(Icons.Default.Share, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 500.dp)
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                stringResource(R.string.done),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp)) // Extra scroll padding
                    }
                }

            }
        }
    }
    var showQr by remember { mutableStateOf(false) }

    // QR dialog
    if (showQr) {
        val shareUrl = uiState.shareUrl
        val isLoading = uiState.isSharing || uiState.shareUrl == null
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showQr = false }) {
                    androidx.compose.material3.Text(stringResource(android.R.string.ok))
                }
            },
            text = {
                if (isLoading) {
                    net.marllex.cafeemanger.core.ui.components.LoadingIndicator(modifier = Modifier.fillMaxWidth())
                } else if (shareUrl != null) {
                    val qr = generateQrBitmap(shareUrl, 300)
                    androidx.compose.foundation.Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            },
            title = { androidx.compose.material3.Text(stringResource(R.string.share_receipt)) }
        )
    }
}


@Composable
fun DashedDivider(
    modifier: Modifier = Modifier,
    dashWidth: Dp = 8.dp,
    dashGap: Dp = 4.dp,
    thickness: Dp = 1.dp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outlineVariant
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
    ) {
        val pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dashWidth.toPx(), dashGap.toPx()),
            phase = 0f
        )

        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = thickness.toPx(),
            pathEffect = pathEffect
        )
    }
}

@Composable
fun ReceiptDataRow(label: String, value: String, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}


private fun renderReceiptBitmap(order: Order, vendor: Vendor?, shareUrl: String?): Bitmap {
    val width = 600 // Standard 80mm thermal printer width is usually ~576px-600px
    val padding = 40
    val lineHeight = 40
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }

    // Helper to calculate height dynamically based on text wrapping
    val textPaint = TextPaint(paint)
    fun getStaticLayout(
        text: String,
        bold: Boolean = false,
        textAlign: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        textPaint.typeface =
            if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width - (padding * 2))
            .setAlignment(textAlign)
            .setLineSpacing(0f, 1f)
            .build()
    }

    // --- Pre-calculate Height ---
    var totalHeight = padding
    val headerLayouts = listOfNotNull(
        vendor?.name?.let {
            getStaticLayout(
                it,
                bold = true,
                textAlign = Layout.Alignment.ALIGN_CENTER
            )
        },
        vendor?.address?.let { getStaticLayout(it, textAlign = Layout.Alignment.ALIGN_CENTER) }
    )
    headerLayouts.forEach { totalHeight += it.height + 10 }

    totalHeight += (lineHeight * 10) // Metadata and spacing
    totalHeight += order.items.size * (lineHeight + 10) // Rough estimate for items
    totalHeight += (lineHeight * 5) // Totals
    if (shareUrl != null) totalHeight += 400 // QR Space

    // --- Start Drawing ---
    val bmp = Bitmap.createBitmap(width, totalHeight + padding, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    var y = padding.toFloat()

    // Draw Header
    headerLayouts.forEach {
        canvas.save()
        canvas.translate(padding.toFloat(), y)
        it.draw(canvas)
        canvas.restore()
        y += it.height + 10
    }

    // Function for dashed line in Bitmap
    fun drawDashedLine(currentY: Float) {
        val dashPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }
        canvas.drawLine(
            padding.toFloat(),
            currentY,
            (width - padding).toFloat(),
            currentY,
            dashPaint
        )
    }

    y += 20
    drawDashedLine(y)
    y += 40

    // Metadata details
    val metadata = listOf(
        "Order #" to order.id.takeLast(8).uppercase(),
        "Date" to formatDate(order.createdAt),
        "Payment" to order.paymentMethod.name
    )

    metadata.forEach { (label, value) ->
        canvas.drawText(label, padding.toFloat(), y, paint)
        val valueWidth = paint.measureText(value)
        canvas.drawText(value, width - padding - valueWidth, y, paint)
        y += lineHeight
    }

    y += 20
    drawDashedLine(y)
    y += 40

    // Items
    order.items.forEach { item ->
        val qtyText = "${item.quantity}x "
        val nameText = item.itemNameSnapshot
        val priceText = formatAmount(item.itemPriceSnapshot * item.quantity)

        canvas.drawText(qtyText, padding.toFloat(), y, paint)

        // Wrap long item names so they don't hit the price
        val availableNameWidth = width - (padding * 2) - 150f
        val nameLayout = StaticLayout.Builder.obtain(
            nameText,
            0,
            nameText.length,
            textPaint,
            availableNameWidth.toInt()
        ).build()

        canvas.save()
        canvas.translate(padding + paint.measureText(qtyText), y - 25f) // Adjust Y for baseline
        nameLayout.draw(canvas)
        canvas.restore()

        canvas.drawText(priceText, width - padding - paint.measureText(priceText), y, paint)
        y += maxOf(lineHeight.toFloat(), (nameLayout.height + 10).toFloat())
    }

    y += 20
    drawDashedLine(y)
    y += 40

    // Totals
    fun drawTotalLine(label: String, value: String, isBold: Boolean = false) {
        paint.typeface =
            if (isBold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        canvas.drawText(label, padding.toFloat(), y, paint)
        canvas.drawText(value, width - padding - paint.measureText(value), y, paint)
        y += lineHeight
    }

    drawTotalLine(
        "Subtotal",
        formatAmount(order.subtotal)
    )
    drawTotalLine(
        "Total",
        formatAmount(order.total), isBold = true
    )

    // QR Code
    if (shareUrl != null) {
        y += 40
        val qrSize = 250
        val qr = generateQrBitmap(shareUrl, qrSize)
        canvas.drawBitmap(qr, ((width - qrSize) / 2).toFloat(), y, null)
    }

    return bmp
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { idx ->
        val x = idx % size
        val y = idx / size
        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}

private fun shareImage(context: android.content.Context, bitmap: Bitmap) {
    val path = MediaStore.Images.Media.insertImage(
        context.contentResolver,
        bitmap,
        "receipt-${System.currentTimeMillis()}",
        null
    )
    val uri = Uri.parse(path)
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "image/png"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}

private fun formatAmountRaw(amount: Double): String = String.format("%.2f EGP", amount)

private fun formatAmount(amount: Double): String = String.format("%.2f EGP", amount)
private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
