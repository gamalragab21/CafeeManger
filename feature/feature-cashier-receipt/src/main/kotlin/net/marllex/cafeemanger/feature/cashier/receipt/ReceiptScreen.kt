package net.marllex.cafeemanger.feature.cashier.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.print.PrintHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderChannel
import net.marllex.cafeemanger.core.model.PaymentMethod
import net.marllex.cafeemanger.core.model.Vendor
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
//  Localized helpers (composable)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun localizedChannel(channel: OrderChannel): String = when (channel) {
    OrderChannel.DINE_IN -> stringResource(R.string.channel_dine_in)
    OrderChannel.DELIVERY -> stringResource(R.string.channel_delivery)
}

@Composable
private fun localizedPayment(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> stringResource(R.string.payment_cash)
    PaymentMethod.WALLET -> stringResource(R.string.payment_wallet)
    PaymentMethod.CARD -> stringResource(R.string.payment_card)
}

@Composable
private fun localizedAmount(amount: Double): String {
    val currency = stringResource(R.string.receipt_currency)
    return String.format("%.2f %s", amount, currency)
}

// ═══════════════════════════════════════════════════════════════════
//  Main Screen
// ═══════════════════════════════════════════════════════════════════

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                val context = LocalContext.current
                val ready = !uiState.isLoading

                LaunchedEffect(showQr) {
                    if (showQr && uiState.shareUrl == null && !uiState.isSharing) {
                        viewModel.generateShareLink()
                    }
                }
                LaunchedEffect(uiState.shareUrl, pendingShare) {
                    if (pendingShare && uiState.shareUrl != null && ready) {
                        val bmp = renderReceiptBitmap(context, order, vendor)
                        shareImage(context, bmp)
                        pendingShare = false
                    }
                }
                LaunchedEffect(pendingPrint) {
                    if (pendingPrint && ready) {
                        val bmp = renderReceiptBitmap(context, order, vendor)
                        try {
                            val printHelper = PrintHelper(context).apply {
                                scaleMode = PrintHelper.SCALE_MODE_FIT
                            }
                            printHelper.printBitmap(
                                "${context.getString(R.string.receipt)} - #${order.id.takeLast(6)}",
                                bmp,
                            )
                        } catch (_: Exception) { }
                        pendingPrint = false
                    }
                }

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
                        // ═══════════════════════════════════════════
                        //  Receipt Card
                        // ═══════════════════════════════════════════
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
                                // ── Header with Logo ──
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
                                    text = vendor?.name ?: stringResource(R.string.restaurant_fallback),
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

                                // ── Order Info ──
                                ReceiptInfoSection {
                                    ReceiptDataRow(
                                        stringResource(R.string.order_number_receipt),
                                        "#${order.id.takeLast(8).uppercase()}",
                                        isBold = true,
                                    )
                                    ReceiptDataRow(stringResource(R.string.date), formatDate(order.createdAt))
                                    ReceiptDataRow(stringResource(R.string.channel), localizedChannel(order.channel))
                                    ReceiptDataRow(stringResource(R.string.payment), localizedPayment(order.paymentMethod))
                                    ReceiptDataRow(stringResource(R.string.cashier), order.cashierName ?: "-")
                                }

                                // ── Client Info ──
                                if (order.clientName != null || order.clientPhone != null || order.clientAddress != null) {
                                    Spacer(Modifier.height(12.dp))
                                    ReceiptInfoSection {
                                        order.clientName?.let { ReceiptDataRow(stringResource(R.string.client_name), it) }
                                        order.clientPhone?.let { ReceiptDataRow(stringResource(R.string.client_phone), it) }
                                        order.clientAddress?.let { ReceiptDataRow(stringResource(R.string.client_address), it) }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                DashedDivider()
                                Spacer(Modifier.height(16.dp))

                                // ── Items table header ──
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.receipt_item), style = MaterialTheme.typography.labelMedium, color = Color(0xFF78716C), modifier = Modifier.weight(1f))
                                    Text(stringResource(R.string.receipt_qty), style = MaterialTheme.typography.labelMedium, color = Color(0xFF78716C), textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
                                    Text(stringResource(R.string.receipt_price), style = MaterialTheme.typography.labelMedium, color = Color(0xFF78716C), textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
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
                                        Text(localizedAmount(item.itemPriceSnapshot * item.quantity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF1C1917), textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                DashedDivider()
                                Spacer(Modifier.height(16.dp))

                                // ── Totals ──
                                ReceiptDataRow(stringResource(R.string.subtotal), localizedAmount(order.subtotal))
                                Spacer(Modifier.height(4.dp))
                                if (order.tax > 0.0) {
                                    ReceiptDataRow(stringResource(R.string.receipt_tax), localizedAmount(order.tax))
                                    Spacer(Modifier.height(4.dp))
                                }
                                if (order.deliveryFee > 0.0) {
                                    ReceiptDataRow(stringResource(R.string.receipt_delivery_fee), localizedAmount(order.deliveryFee))
                                    Spacer(Modifier.height(4.dp))
                                }

                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFFE7E5E4))
                                Spacer(Modifier.height(12.dp))

                                // ── Grand Total ──
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF0FDFA), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFCCFBF1), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(stringResource(R.string.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1C1917))
                                    Text(localizedAmount(order.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                                }

                                // ── Notes ──
                                order.notes?.let { notes ->
                                    Spacer(Modifier.height(16.dp))
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFAFAF9), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                    ) {
                                        Text(stringResource(R.string.receipt_notes), style = MaterialTheme.typography.labelSmall, color = Color(0xFF78716C))
                                        Spacer(Modifier.height(2.dp))
                                        Text(notes, style = MaterialTheme.typography.bodySmall, color = Color(0xFF44403C))
                                    }
                                }

                                Spacer(Modifier.height(20.dp))

                                // ── Digital Menu QR ──
                                val menuUrl = vendor?.digitalMenuUrl
                                    ?: vendor?.id?.let { "${net.marllex.cafeemanger.core.network.BuildConfig.BASE_URL.trimEnd('/')}/menu/$it" }
                                if (!menuUrl.isNullOrBlank()) {
                                    DashedDivider()
                                    Spacer(Modifier.height(12.dp))
                                    val menuQr = remember(menuUrl) { generateQrBitmap(menuUrl, 256) }
                                    Image(menuQr.asImageBitmap(), contentDescription = null, modifier = Modifier.size(80.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.receipt_scan_qr),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA8A29E),
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }

                                // ── Footer ──
                                Text(
                                    text = stringResource(R.string.thank_you_for_your_visit),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFFA8A29E),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // ═══════════════════════════════════════════
                        //  Action Buttons
                        // ═══════════════════════════════════════════
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3,
                        ) {
                            val btnMod = Modifier.weight(1f).height(48.dp)
                            OutlinedButton(onClick = { pendingPrint = true }, modifier = btnMod, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.print), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { showQr = true; viewModel.generateShareLink() }, modifier = btnMod, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Default.QrCode, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.qr), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { pendingShare = true; viewModel.generateShareLink() }, enabled = ready, modifier = btnMod, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.share), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    //  QR Dialog
    // ═══════════════════════════════════════════
    if (showQr) {
        val scanLabel = stringResource(R.string.receipt_scan_qr)
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text(stringResource(android.R.string.ok)) }
            },
            title = {
                Text(stringResource(R.string.share_receipt), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val shareUrl = uiState.shareUrl
                    val isLoading = uiState.isSharing || shareUrl == null
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                    } else {
                        Surface(Modifier.size(220.dp).padding(8.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                            val qr = remember(shareUrl) { generateQrBitmap(shareUrl, 512) }
                            Image(qr.asImageBitmap(), contentDescription = stringResource(R.string.qr), modifier = Modifier.fillMaxSize().padding(12.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(scanLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Reusable Receipt Components
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReceiptInfoSection(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFAFAF9), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
fun DashedDivider(
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
fun ReceiptDataRow(label: String, value: String, isBold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF78716C))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium, color = Color(0xFF1C1917))
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Bitmap renderer – full receipt for Print & Share
//  Uses Canvas so the ENTIRE receipt is always captured regardless
//  of scroll position or screen size.
// ═══════════════════════════════════════════════════════════════════

private fun renderReceiptBitmap(context: Context, order: Order, vendor: Vendor?): Bitmap {
    val width = 620
    val pad = 44
    val contentW = width - pad * 2
    val lineH = 38
    val isRtl = context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL

    val normalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1C1917")
        textSize = 26f
        typeface = Typeface.DEFAULT
    }
    val boldPaint = TextPaint(normalPaint).apply { typeface = Typeface.DEFAULT_BOLD }
    val smallPaint = TextPaint(normalPaint).apply { textSize = 22f; color = android.graphics.Color.parseColor("#78716C") }
    val titlePaint = TextPaint(normalPaint).apply { textSize = 36f; typeface = Typeface.DEFAULT_BOLD }
    val totalPaint = TextPaint(normalPaint).apply { textSize = 32f; typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.parseColor("#0D9488") }
    val footerPaint = TextPaint(normalPaint).apply { textSize = 22f; color = android.graphics.Color.parseColor("#A8A29E") }

    fun str(resId: Int) = context.getString(resId)
    fun amt(v: Double) = String.format("%.2f %s", v, str(R.string.receipt_currency))

    fun channelStr(c: OrderChannel) = when (c) {
        OrderChannel.DINE_IN -> str(R.string.channel_dine_in)
        OrderChannel.DELIVERY -> str(R.string.channel_delivery)
    }
    fun paymentStr(p: PaymentMethod) = when (p) {
        PaymentMethod.CASH -> str(R.string.payment_cash)
        PaymentMethod.WALLET -> str(R.string.payment_wallet)
        PaymentMethod.CARD -> str(R.string.payment_card)
    }

    val align = if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL

    fun staticLayout(text: String, paint: TextPaint, w: Int = contentW, alignment: Layout.Alignment = align): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, w).setAlignment(alignment).build()

    // --- Pre-calculate height ---
    var h = pad // top padding
    // header
    val headerLayout = staticLayout(vendor?.name ?: str(R.string.restaurant_fallback), titlePaint, alignment = Layout.Alignment.ALIGN_CENTER)
    h += headerLayout.height + 8
    vendor?.address?.let {
        h += staticLayout(it, smallPaint, alignment = Layout.Alignment.ALIGN_CENTER).height + 8
    }
    h += 40 // divider + spacing

    // metadata rows
    val metaRows = mutableListOf(
        str(R.string.order_number_receipt) to "#${order.id.takeLast(8).uppercase()}",
        str(R.string.date) to formatDate(order.createdAt),
        str(R.string.channel) to channelStr(order.channel),
        str(R.string.payment) to paymentStr(order.paymentMethod),
        str(R.string.cashier) to (order.cashierName ?: "-"),
    )
    if (order.clientName != null) metaRows.add(str(R.string.client_name) to order.clientName!!)
    if (order.clientPhone != null) metaRows.add(str(R.string.client_phone) to order.clientPhone!!)
    if (order.clientAddress != null) metaRows.add(str(R.string.client_address) to order.clientAddress!!)
    h += metaRows.size * lineH + 20

    h += 40 // divider
    // items header + items
    h += lineH // header row
    order.items.forEach { item ->
        val nameLayout = staticLayout(item.itemNameSnapshot, normalPaint, contentW - 200)
        h += maxOf(lineH, nameLayout.height + 10)
    }
    h += 40 // divider

    // totals
    h += lineH // subtotal
    if (order.tax > 0.0) h += lineH
    if (order.deliveryFee > 0.0) h += lineH
    h += 20 // spacing
    h += lineH + 20 // grand total

    // notes
    order.notes?.let { notes ->
        h += 20
        h += staticLayout(str(R.string.receipt_notes), smallPaint).height
        h += staticLayout(notes, normalPaint).height + 20
    }

    // digital menu QR
    val menuUrl = vendor?.digitalMenuUrl
        ?: vendor?.id?.let { "${net.marllex.cafeemanger.core.network.BuildConfig.BASE_URL.trimEnd('/')}/menu/$it" }
    val qrSize = 180
    if (!menuUrl.isNullOrBlank()) {
        h += 20 // divider
        h += qrSize + 10
        h += staticLayout(str(R.string.receipt_scan_qr), footerPaint, alignment = Layout.Alignment.ALIGN_CENTER).height + 20
    }

    // thank you
    h += 30
    h += staticLayout(str(R.string.thank_you_for_your_visit), footerPaint, alignment = Layout.Alignment.ALIGN_CENTER).height
    h += pad // bottom padding

    // --- Draw ---
    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    var y = pad.toFloat()

    fun drawTextLayout(layout: StaticLayout, x: Float = pad.toFloat()) {
        canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
        y += layout.height
    }

    fun drawDashed(yy: Float) {
        val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#D6D3D1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        }
        canvas.drawLine(pad.toFloat(), yy, (width - pad).toFloat(), yy, dp)
    }

    fun drawRow(label: String, value: String, paint: TextPaint = normalPaint, valuePaint: TextPaint = paint) {
        val lW = paint.measureText(label)
        val vW = valuePaint.measureText(value)
        if (isRtl) {
            canvas.drawText(value, pad.toFloat(), y + 26f, valuePaint)
            canvas.drawText(label, (width - pad - lW), y + 26f, paint)
        } else {
            canvas.drawText(label, pad.toFloat(), y + 26f, paint)
            canvas.drawText(value, (width - pad - vW), y + 26f, valuePaint)
        }
        y += lineH
    }

    // Header
    drawTextLayout(headerLayout, pad.toFloat())
    y += 8
    vendor?.address?.let {
        drawTextLayout(staticLayout(it, smallPaint, alignment = Layout.Alignment.ALIGN_CENTER), pad.toFloat())
        y += 8
    }

    y += 12; drawDashed(y); y += 28

    // Metadata
    metaRows.forEach { (label, value) -> drawRow(label, value, smallPaint, normalPaint) }
    y += 12; drawDashed(y); y += 28

    // Items header
    drawRow(str(R.string.receipt_item), str(R.string.receipt_price), smallPaint, smallPaint)

    // Items
    order.items.forEach { item ->
        val nameText = "${item.quantity}x ${item.itemNameSnapshot}"
        val priceText = amt(item.itemPriceSnapshot * item.quantity)
        val priceW = normalPaint.measureText(priceText)
        val nameW = contentW - priceW.toInt() - 20
        val nameLayout = staticLayout(nameText, normalPaint, nameW)

        if (isRtl) {
            canvas.drawText(priceText, pad.toFloat(), y + 26f, normalPaint)
            canvas.save(); canvas.translate((width - pad - nameW).toFloat(), y); nameLayout.draw(canvas); canvas.restore()
        } else {
            canvas.save(); canvas.translate(pad.toFloat(), y); nameLayout.draw(canvas); canvas.restore()
            canvas.drawText(priceText, (width - pad - priceW), y + 26f, normalPaint)
        }
        y += maxOf(lineH.toFloat(), (nameLayout.height + 10).toFloat())
    }

    y += 12; drawDashed(y); y += 28

    // Totals
    drawRow(str(R.string.subtotal), amt(order.subtotal), smallPaint, normalPaint)
    if (order.tax > 0.0) drawRow(str(R.string.receipt_tax), amt(order.tax), smallPaint, normalPaint)
    if (order.deliveryFee > 0.0) drawRow(str(R.string.receipt_delivery_fee), amt(order.deliveryFee), smallPaint, normalPaint)

    y += 10
    // Grand total with teal background
    val totalBgPaint = Paint().apply { color = android.graphics.Color.parseColor("#F0FDFA"); style = Paint.Style.FILL }
    val totalBorderPaint = Paint().apply { color = android.graphics.Color.parseColor("#CCFBF1"); style = Paint.Style.STROKE; strokeWidth = 2f }
    val totalRectTop = y
    val totalRectBottom = y + lineH + 16
    canvas.drawRoundRect(pad.toFloat(), totalRectTop, (width - pad).toFloat(), totalRectBottom, 20f, 20f, totalBgPaint)
    canvas.drawRoundRect(pad.toFloat(), totalRectTop, (width - pad).toFloat(), totalRectBottom, 20f, 20f, totalBorderPaint)
    y += 8
    val totalLabel = str(R.string.total)
    val totalValue = amt(order.total)
    val tLabelPaint = TextPaint(boldPaint).apply { textSize = 30f }
    if (isRtl) {
        canvas.drawText(totalValue, (pad + 16).toFloat(), y + 28f, totalPaint)
        canvas.drawText(totalLabel, (width - pad - 16 - tLabelPaint.measureText(totalLabel)), y + 28f, tLabelPaint)
    } else {
        canvas.drawText(totalLabel, (pad + 16).toFloat(), y + 28f, tLabelPaint)
        canvas.drawText(totalValue, (width - pad - 16 - totalPaint.measureText(totalValue)), y + 28f, totalPaint)
    }
    y = totalRectBottom + 10

    // Notes
    order.notes?.let { notes ->
        y += 10
        val notesLabel = staticLayout(str(R.string.receipt_notes), smallPaint)
        drawTextLayout(notesLabel, pad.toFloat())
        y += 4
        val notesBody = staticLayout(notes, normalPaint)
        drawTextLayout(notesBody, pad.toFloat())
        y += 10
    }

    // Digital menu QR code
    if (!menuUrl.isNullOrBlank()) {
        y += 10
        drawDashed(y)
        y += 10
        val qrBmp = generateQrBitmap(menuUrl, qrSize)
        val qrX = (width - qrSize) / 2f
        canvas.drawBitmap(qrBmp, qrX, y, null)
        y += qrSize + 6
        drawTextLayout(
            staticLayout(str(R.string.receipt_scan_qr), footerPaint, alignment = Layout.Alignment.ALIGN_CENTER),
            pad.toFloat(),
        )
        y += 10
    }

    // Thank you
    y += 16
    drawTextLayout(
        staticLayout(str(R.string.thank_you_for_your_visit), footerPaint, alignment = Layout.Alignment.ALIGN_CENTER),
        pad.toFloat(),
    )

    return bmp
}

// ═══════════════════════════════════════════════════════════════════
//  Utility Functions
// ═══════════════════════════════════════════════════════════════════

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { idx ->
        if (bitMatrix[idx % size, idx / size]) Color.Black.toArgb() else Color.White.toArgb()
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}

private fun shareImage(context: Context, bitmap: Bitmap) {
    @Suppress("DEPRECATION")
    val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "receipt-${System.currentTimeMillis()}", null) ?: return
    val uri = Uri.parse(path)
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "image/png"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()).format(Date(epochMs))
