package net.marllex.cafeemanger.feature.cashier.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.view.View
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.print.PrintHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiptScreen(
    onBack: () -> Unit = {},
    viewModel: ReceiptViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var showQr by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf(false) }
    var pendingPrint by remember { mutableStateOf(false) }
    var receiptRect by remember { mutableStateOf<Rect?>(null) }
    val rootView = LocalView.current

    Scaffold(
//        topBar = {
////            TopAppBar(
////                title = { Text(stringResource(R.string.receipt)) },
////                navigationIcon = {
////                    IconButton(onClick = onBack) {
////                        Icon(
////                            Icons.AutoMirrored.Filled.ArrowBack,
////                            contentDescription = stringResource(R.string.back)
////                        )
////                    }
////                },
////                colors = TopAppBarDefaults.topAppBarColors(
////                    containerColor = MaterialTheme.colorScheme.primaryContainer,
////                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
////                ),
////            )
//        },
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

                fun captureReceiptBitmap(): Bitmap {
                    val fullBitmap = rootView.drawToBitmap()
                    val rect = receiptRect ?: return fullBitmap

                    val left = rect.left.coerceAtLeast(0)
                    val top = rect.top.coerceAtLeast(0)
                    val width = rect.width().coerceAtMost(fullBitmap.width - left)
                    val height = rect.height().coerceAtMost(fullBitmap.height - top)

                    return Bitmap.createBitmap(fullBitmap, left, top, width, height)
                }


                // Ensure share link requested when QR dialog is opened
                LaunchedEffect(showQr) {
                    if (showQr && uiState.shareUrl == null && !uiState.isSharing) {
                        viewModel.generateShareLink()
                    }
                }

                LaunchedEffect(uiState.shareUrl, pendingShare) {
                    if (pendingShare && uiState.shareUrl != null && ready) {
                        val bmp = captureReceiptBitmap()
                        shareImage(context, bmp)
                        pendingShare = false
                    }
                }
                LaunchedEffect(pendingPrint) {
                    if (pendingPrint && ready) {
                        val bmp = captureReceiptBitmap()
                        try {
                            val printHelper = PrintHelper(context).apply {
                                // FIT ensures the receipt maintains its aspect ratio and doesn't cut off
                                scaleMode = PrintHelper.SCALE_MODE_FIT
                            }
                            // Provide a clear job name for the Android Print Spooler
                            val jobName = "${context.getString(R.string.cashier)} - Order ${
                                order.id.takeLast(6)
                            }"

                            printHelper.printBitmap(jobName, bmp)

                        } catch (_: Exception) {
                        }
                        pendingPrint = false
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // --- Action Buttons on Top ---
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 520.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            val actionButtonModifier = Modifier
                                .weight(1f)
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- The "Paper" Receipt Card ---
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 560.dp)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    val size = coords.size
                                    receiptRect = Rect(
                                        pos.x.roundToInt(),
                                        pos.y.roundToInt(),
                                        (pos.x + size.width).roundToInt(),
                                        (pos.y + size.height).roundToInt()
                                    )
                                },
                            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
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
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 40.sp
                                )

                                vendor?.address?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
                                    )
                                }

                                DashedDivider(modifier = Modifier.padding(vertical = 16.dp))

                                // Metadata: Using more professional labeling
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ReceiptDataRow(
                                        stringResource(R.string.order_number_receipt),
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
                                    if (order.clientName != null || order.clientPhone != null || order.clientAddress != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ReceiptDataRow(
                                            stringResource(R.string.client_name),
                                            order.clientName ?: "-"
                                        )
                                        ReceiptDataRow(
                                            stringResource(R.string.client_phone),
                                            order.clientPhone ?: "-"
                                        )
                                        order.clientAddress?.let {
                                            ReceiptDataRow(
                                                stringResource(R.string.client_address),
                                                it
                                            )
                                        }
                                    }
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
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 16.dp)
                                        )
                                        Text(
                                            text = formatAmount(item.itemPriceSnapshot * item.quantity),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
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
                                    stringResource(R.string.recipt_tax),
                                    formatAmount(order.deliveryFee.takeIf { it > 0.0 } ?: order.tax)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.total),
                                        style = MaterialTheme.typography.headlineLarge, // Bigger total
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = formatAmount(order.total),
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.thank_you_for_your_visit),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

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
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}


private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { idx ->
        val x = idx % size
        val y = idx / size
        if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb()
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



private fun formatAmount(amount: Double): String = String.format("%.2f EGP", amount)
private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
