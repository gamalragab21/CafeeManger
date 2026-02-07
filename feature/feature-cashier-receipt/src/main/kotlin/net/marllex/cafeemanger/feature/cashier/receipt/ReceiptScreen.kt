package net.marllex.cafeemanger.feature.cashier.receipt

import android.content.Context
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.print.PrintHelper
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
fun ReceiptScreen(
    onBack: () -> Unit = {},
    viewModel: ReceiptViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showQr by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf(false) }
    var pendingPrint by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
            uiState.isLoading -> {
                LoadingIndicator()
            }
            uiState.order != null -> {
                val order = uiState.order!!
                val vendor = uiState.vendor
                val context = LocalContext.current
                // Allow print/share even if vendor is still loading; we'll render with whatever is available
                val ready = order != null && !uiState.isLoading

                // Ensure share link requested when QR dialog is opened
                LaunchedEffect(showQr) {
                    if (showQr && uiState.shareUrl == null && !uiState.isSharing) {
                        viewModel.generateShareLink()
                    }
                }

                LaunchedEffect(uiState.shareUrl, pendingShare) {
                    if (pendingShare && uiState.shareUrl != null && ready) {
                        val bmp = renderReceiptBitmap(context, order, vendor, uiState.shareUrl)
                        shareImage(context, bmp)
                        pendingShare = false
                    }
                }
                LaunchedEffect(pendingPrint) {
                    if (pendingPrint && ready) {
                        val bmp = renderReceiptBitmap(context, order, vendor, uiState.shareUrl)
                        try {
                            PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }
                                .printBitmap("receipt-${order.id.takeLast(6)}", bmp)
                        } catch (_: Exception) {
                        }
                        pendingPrint = false
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
                                .widthIn(max = 480.dp), // Industry standard for 80mm digital receipts
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                            shape = RectangleShape
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Header: Increased prominence
                                Text(
                                    text = vendor?.name
                                        ?: stringResource(R.string.restaurant_fallback),
                                    style = MaterialTheme.typography.headlineMedium, // Slightly larger
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 32.sp
                                )

                                vendor?.address?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
                                    )
                                }

                                DashedDivider(modifier = Modifier.padding(vertical = 16.dp))

                                // Metadata: Using more professional labeling
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ReceiptDataRow(
                                        stringResource(R.string.order_number),
                                        order.id.takeLast(8).uppercase(),
                                        isBold = true
                                    )
                                    ReceiptDataRow(
                                        stringResource(R.string.channel),
                                        order.channel.name
                                    )
                                    ReceiptDataRow(
                                        stringResource(R.string.payment),
                                        order.paymentMethod.name
                                    )
                                    ReceiptDataRow(
                                        stringResource(R.string.date),
                                        formatDate(order.createdAt)
                                    )
                                    ReceiptDataRow(
                                        stringResource(R.string.cashier),
                                        order.cashierName ?: "-"
                                    )
                                }

                                DashedDivider(modifier = Modifier.padding(vertical = 16.dp))

                                // Item List: Better item-price separation
                                order.items.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "${item.quantity}x ${item.itemNameSnapshot}",
                                            style = MaterialTheme.typography.bodyLarge, // Larger for readability
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 16.dp)
                                        )
                                        Text(
                                            text = formatAmount(item.itemPriceSnapshot * item.quantity),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                DashedDivider(modifier = Modifier.padding(vertical = 16.dp))

                                // Totals
                                ReceiptDataRow(
                                    stringResource(R.string.subtotal),
                                    formatAmount(order.subtotal)
                                )
                                ReceiptDataRow(
                                    stringResource(R.string.tax),
                                    formatAmount(order.tax)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.total),
                                        style = MaterialTheme.typography.headlineSmall, // Professional bold total
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = formatAmount(order.total),
                                        style = MaterialTheme.typography.headlineSmall,
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

                        Spacer(modifier = Modifier.height(32.dp))

                        // --- Action Buttons: Fixed for Screen Compatibility ---
                        // Remove individual weights and use specific minWidths so FlowRow can wrap correctly
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 480.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            // Helper for consistent button sizing
                            val actionButtonModifier = Modifier
                                .weight(1f) // Weight works here because we wrap the whole thing in a constrained widthIn
                                .height(48.dp)

                            OutlinedButton(
                                onClick = { pendingPrint = true },
                                modifier = actionButtonModifier,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.print),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { showQr = true; viewModel.generateShareLink() },
                                modifier = actionButtonModifier,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.QrCode, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.qr),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { pendingShare = true; viewModel.generateShareLink() },
                                enabled = ready,
                                modifier = actionButtonModifier,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.share),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 480.dp)
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                stringResource(R.string.done),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // QR dialog
    if (showQr) {
        val shareUrl = uiState.shareUrl
        val isLoading = uiState.isSharing || uiState.shareUrl == null
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.share_receipt),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                            Text(text = "Generating Link...", style = MaterialTheme.typography.bodySmall)
                        }
                        shareUrl != null -> {
                            // 1. Wrap QR in a Surface to give it a clean white background
                            // (QR codes scan better with a consistent background)
                            Surface(
                                modifier = Modifier
                                    .size(240.dp) // Fixed square size for stability
                                    .padding(8.dp),
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 2.dp
                            ) {
                                val qr = remember(shareUrl) { generateQrBitmap(shareUrl, 512) } // Higher res for display
                                Image(
                                    bitmap = qr.asImageBitmap(),
                                    contentDescription = stringResource(R.string.qr),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp) // Padding inside the white box
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 2. Add an expiration or info note
                            Text(
                                text = "Scan to view digital receipt",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.share_receipt),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
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
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
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

private fun shareImage(context: Context, bitmap: Bitmap) {
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

private fun renderReceiptBitmap(
    context: Context,
    order: Order,
    vendor: Vendor?,
    shareUrl: String?
): Bitmap {
    val width = 600
    val padding = 40f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 26f // Slightly smaller for better fit
        typeface = Typeface.MONOSPACE
    }
    val textPaint = TextPaint(paint)

    // Helper for multiline text (Header and Item Names)
    fun getStaticLayout(text: String, bold: Boolean = false, textAlign: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, customWidth: Int = (width - (padding * 2)).toInt()): StaticLayout {
        textPaint.typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, customWidth)
            .setAlignment(textAlign)
            .setLineSpacing(0f, 1.1f)
            .build()
    }

    // 1. Prepare Layouts and Calculate Exact Height
    val vendorName = vendor?.name ?: context.getString(R.string.restaurant_fallback)
    val headerLayouts = listOfNotNull(
        getStaticLayout(vendorName, bold = true, textAlign = Layout.Alignment.ALIGN_CENTER),
        vendor?.address?.let { getStaticLayout(it, textAlign = Layout.Alignment.ALIGN_CENTER) }
    )

    // Initial height with padding and header
    var calculatedHeight = padding * 2
    headerLayouts.forEach { calculatedHeight += it.height + 10 }

    // Metadata, Dividers, Items, Totals, and QR
    val metadataCount = 3
    val dividerCount = 3
    calculatedHeight += (metadataCount * 45) + (dividerCount * 40) + 40 // Spacing

    // Exact height for items
    val itemLayouts = order.items.map { item ->
        val availableNameWidth = width - (padding * 2) - 180f // Reserve space for Qty and Price
        getStaticLayout(item.itemNameSnapshot, customWidth = availableNameWidth.toInt())
    }
    itemLayouts.forEach { calculatedHeight += maxOf(45f, it.height.toFloat() + 10f) }

    calculatedHeight += (2 * 50) // Totals
    if (shareUrl != null) calculatedHeight += 320 // QR Space

    // 2. Start Drawing
    val bmp = Bitmap.createBitmap(width, calculatedHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    var currentY = padding

    // Draw Header
    headerLayouts.forEach {
        canvas.save()
        canvas.translate(padding, currentY)
        it.draw(canvas)
        canvas.restore()
        currentY += it.height + 15
    }

    fun drawDashedLine() {
        currentY += 10
        val dashPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }
        canvas.drawLine(padding, currentY, width - padding, currentY, dashPaint)
        currentY += 30
    }

    drawDashedLine()

    // Metadata details
    fun drawDataRow(label: String, value: String, isBold: Boolean = false) {
        textPaint.typeface = if (isBold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        canvas.drawText(label, padding, currentY + paint.textSize, textPaint)
        val valueWidth = textPaint.measureText(value)
        canvas.drawText(value, width - padding - valueWidth, currentY + paint.textSize, textPaint)
        currentY += 45
    }

    drawDataRow(context.getString(R.string.order_number), order.id.takeLast(8).uppercase())
    drawDataRow(context.getString(R.string.date), formatDate(order.createdAt))
    drawDataRow(context.getString(R.string.payment), order.paymentMethod.name)

    drawDashedLine()

// 1. Define strict column boundaries
    val qtyColWidth = 70f        // Width for "1x "
    val priceColWidth = 160f     // Width for "000.00 EGP"
    val nameColWidth = width - (padding * 2) - qtyColWidth - priceColWidth // The "Safe Zone" for text

// 2. The Drawing Loop
    order.items.forEach { item ->
        val qtyText = "${item.quantity}x"
        val nameText = item.itemNameSnapshot
        val priceText = formatAmount(item.itemPriceSnapshot * item.quantity)

        // Create layout ONLY for the name, restricted to the nameColWidth
        val nameLayout = StaticLayout.Builder.obtain(
            nameText, 0, nameText.length, textPaint, nameColWidth.toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL) // Forces Left alignment
            .setLineSpacing(0f, 1.1f)
            .build()

        // --- Column 1: Quantity (Far Left) ---
        // Drawn at 'padding'
        canvas.drawText(qtyText, padding, currentY + paint.textSize, textPaint)

        // --- Column 2: Item Name (Middle) ---
        canvas.save()
        // Move the "pen" to start after the Quantity column
        canvas.translate(padding + qtyColWidth, currentY)
        nameLayout.draw(canvas)
        canvas.restore()

        // --- Column 3: Price (Far Right) ---
        val measuredPriceWidth = textPaint.measureText(priceText)
        // Drawn at width minus padding minus its own width
        canvas.drawText(
            priceText,
            width - padding - measuredPriceWidth,
            currentY + paint.textSize,
            textPaint
        )

        // Move the Y pointer down based on how many lines the name took
        // We use maxOf to ensure we move down at least one full line height
        val rowHeight = maxOf(45f, nameLayout.height.toFloat() + 10f)
        currentY += rowHeight
    }
    drawDashedLine()

    // Totals
    drawDataRow(context.getString(R.string.subtotal), formatAmount(order.subtotal))
    drawDataRow(context.getString(R.string.total), formatAmount(order.total), isBold = true)

    // QR Code
    if (shareUrl != null) {
        currentY += 20
        val qrSize = 250
        val qr = generateQrBitmap(shareUrl, qrSize)
        canvas.drawBitmap(qr, (width - qrSize) / 2f, currentY, null)
    }

    return bmp
}

private fun formatAmount(amount: Double): String = String.format("%.2f EGP", amount)
private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
