package net.marllex.waselak.feature.cashier.receipt
import net.marllex.waselak.core.ui.components.WaselakTopAppBar

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
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
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
import androidx.compose.foundation.Image
import coil3.compose.AsyncImage
import net.marllex.waselak.core.ui.components.waslekLogoPainter
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.platform.buildReceiptHtml
import net.marllex.waselak.core.ui.platform.getReceiptLanguage
import net.marllex.waselak.core.ui.platform.rememberPlatformActions
import net.marllex.waselak.core.ui.platform.PrinterSelectionDialogIfNeeded
import net.marllex.waselak.core.ui.platform.rememberReceiptPrinter
import net.marllex.waselak.core.ui.theme.LocalReceiptColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
// QR painter is provided by an expect/actual wrapper (QrKitPainter.kt in this package)
import net.marllex.waselak.feature.cashier.receipt.generated.resources.*

@Composable
private fun localizedChannel(channel: OrderChannel, businessType: String? = null): String =
    net.marllex.waselak.core.ui.components.formatChannelLabel(channel, businessType)

@Composable
private fun localizedPayment(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> stringResource(Res.string.payment_cash)
    PaymentMethod.WALLET -> stringResource(Res.string.payment_wallet)
    PaymentMethod.CARD -> stringResource(Res.string.payment_card)
    PaymentMethod.SPLIT -> stringResource(Res.string.payment_split)
    PaymentMethod.CREDIT -> stringResource(Res.string.payment_credit)
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
    val h12 = if (dt.hour == 0) 12 else if (dt.hour > 12) dt.hour - 12 else dt.hour
    val amPm = if (dt.hour < 12) "AM" else "PM"
    return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${
        dt.dayOfMonth.toString().padStart(2, '0')
    }  ${h12.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')} $amPm"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReceiptScreen(
    onBack: () -> Unit = {},
) {
    val viewModel: ReceiptViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val printer = rememberReceiptPrinter()
    val platformActions = rememberPlatformActions()
    var showQr by remember { mutableStateOf(false) }
    // True when the cashier tapped Print but no ESC/POS printer has
    // been picked yet (Android first-time-setup), OR when they
    // long-pressed Print to switch to a different printer. On other
    // platforms this stays false (the OS handles printer selection).
    var showPrinterPicker by remember { mutableStateOf(false) }
    // True while the "Hold phones together" NFC share dialog is on
    // screen. While shown, the HCE service is armed with the receipt's
    // share URL; dismissing the dialog clears the URL so a later stray
    // tap can't leak the previous receipt.
    var showNfcDialog by remember { mutableStateOf(false) }
    val receiptColors = LocalReceiptColors.current
    // Receipt language — comes from the platform-specific persisted
    // language selector (NOT Compose's Locale.current, which is cached
    // and ignores `applyLanguage` calls). See PrintReceipt.kt's expect
    // declaration. Re-reads on every recomposition so a language switch
    // takes effect on the next print.
    val receiptLang = getReceiptLanguage()

    // Localized strings
    val receiptTitle = stringResource(Res.string.receipt)
    val backStr = stringResource(Res.string.back)
    val vendorBusinessType = uiState.vendor?.businessType ?: "RESTAURANT"
    val orderNumLabel = when (vendorBusinessType) {
        "PHARMACY" -> stringResource(Res.string.order_number_prescription)
        "SUPERMARKET", "GROCERY", "RETAIL" -> stringResource(Res.string.order_number_invoice)
        else -> stringResource(Res.string.order_number_receipt)
    }
    val dateLabel = stringResource(Res.string.date)
    val channelLabel = stringResource(Res.string.channel)
    val paymentLabel = stringResource(Res.string.payment)
    val cashierLabel = stringResource(Res.string.cashier)
    val doctorLabel = stringResource(Res.string.doctor_label)
    val diagnosisLabel = stringResource(Res.string.diagnosis_label)
    val clientLabel = stringResource(Res.string.client_name)
    val phoneLabel = stringResource(Res.string.client_phone)
    val addressLabel = stringResource(Res.string.client_address)
    val itemLabel = stringResource(Res.string.receipt_item)
    val qtyLabel = stringResource(Res.string.receipt_qty)
    val priceLabel = stringResource(Res.string.receipt_price)
    val subtotalLabel = stringResource(Res.string.subtotal)
    val taxLabel = stringResource(Res.string.receipt_tax)
    val deliveryFeeLabel = stringResource(Res.string.receipt_delivery_fee)
    val totalLabel = stringResource(Res.string.total)
    val notesLabel = stringResource(Res.string.receipt_notes)
    val thankYouStr = stringResource(Res.string.thank_you_for_your_visit)
    val printStr = stringResource(Res.string.print)
    val qrStr = stringResource(Res.string.qr)
    val shareStr = stringResource(Res.string.share)
    val doneStr = stringResource(Res.string.done)
    val nfcUnavailableMessage = stringResource(Res.string.nfc_unavailable_message)
    val currency = stringResource(Res.string.receipt_currency)

    Scaffold(
        topBar = {
            WaselakTopAppBar(
                title = receiptTitle,
                onNavigateBack = onBack,
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
                            colors = CardDefaults.elevatedCardColors(containerColor = receiptColors.surface),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Header with Logo
                                val logoPainter = waslekLogoPainter()
                                if (!vendor?.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = vendor?.logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, receiptColors.divider, CircleShape),
                                        contentScale = ContentScale.Crop,
                                        placeholder = logoPainter,
                                        error = logoPainter,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                } else {
                                    Image(
                                        painter = logoPainter,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, receiptColors.divider, CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                Text(
                                    text = vendor?.name ?: stringResource(Res.string.restaurant_fallback),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = receiptColors.textPrimary,
                                )
                                vendor?.address?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = receiptColors.textSecondary,
                                    )
                                }

                                Spacer(Modifier.height(16.dp))
                                DashedDivider(color = receiptColors.divider)
                                Spacer(Modifier.height(16.dp))

                                // Order Info
                                ReceiptInfoSection(bgColor = receiptColors.sectionBg) {
                                    ReceiptDataRow(
                                        orderNumLabel,
                                        order.displayId,
                                        isBold = true,
                                        labelColor = receiptColors.textSecondary,
                                        valueColor = receiptColors.textPrimary,
                                    )
                                    ReceiptDataRow(dateLabel, formatDate(order.createdAt), labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary)
                                    ReceiptDataRow(channelLabel, localizedChannel(order.channel, vendorBusinessType), labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary)
                                    ReceiptDataRow(paymentLabel, localizedPayment(order.paymentMethod), labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary)
                                    ReceiptDataRow(cashierLabel, order.cashierName ?: "-", labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary)
                                    order.doctorName?.let { ReceiptDataRow(doctorLabel, it, labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary) }
                                    order.diagnosis?.let { ReceiptDataRow(diagnosisLabel, it, labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary) }
                                }

                                // Client Info
                                if (order.clientName != null || order.clientPhone != null || order.clientAddress != null) {
                                    Spacer(Modifier.height(12.dp))
                                    ReceiptInfoSection(bgColor = receiptColors.sectionBg) {
                                        order.clientName?.let { ReceiptDataRow(clientLabel, it, labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary) }
                                        order.clientPhone?.let { ReceiptDataRow(phoneLabel, it, labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary) }
                                        order.clientAddress?.let { ReceiptDataRow(addressLabel, it, labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary) }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                DashedDivider(color = receiptColors.divider)
                                Spacer(Modifier.height(16.dp))

                                // Items table header
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(itemLabel, style = MaterialTheme.typography.labelMedium, color = receiptColors.textSecondary, modifier = Modifier.weight(1f))
                                    Text(qtyLabel, style = MaterialTheme.typography.labelMedium, color = receiptColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
                                    Text(priceLabel, style = MaterialTheme.typography.labelMedium, color = receiptColors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = receiptColors.divider, thickness = 0.5.dp)
                                Spacer(Modifier.height(8.dp))

                                order.items.forEach { item ->
                                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(item.itemNameSnapshot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = receiptColors.textPrimary, modifier = Modifier.weight(1f))
                                            Text("${item.quantity}", style = MaterialTheme.typography.bodyMedium, color = receiptColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
                                            Text(formatAmount(item.itemPriceSnapshot * item.quantity, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = receiptColors.textPrimary, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                                        }
                                        net.marllex.waselak.core.ui.util.VariantDisplayHelper.formatVariantSummary(item.variantOptionsSnapshot)?.let { summary ->
                                            Text(text = summary, style = MaterialTheme.typography.bodySmall, color = receiptColors.textSecondary)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                DashedDivider(color = receiptColors.divider)
                                Spacer(Modifier.height(16.dp))

                                // Totals layout:
                                //   Subtotal (always)
                                //   Discount (if any — offer / manual / points)
                                //   Delivery Fee (if > 0)
                                //   Tax (if > 0)
                                //   ─────────
                                //   Total (grand-total band)
                                //
                                // The Discount row is critical when an offer
                                // is applied: without it, customers see
                                // "Subtotal 115" then jump to "Total 100" with
                                // no visible reason for the reduction. The
                                // bilingual label matches the printed receipt.
                                ReceiptDataRow(subtotalLabel, formatAmount(order.subtotal, currency), labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary)
                                Spacer(Modifier.height(4.dp))
                                if (order.discount > 0.0) {
                                    ReceiptDataRow(
                                        "الخصم / Discount",
                                        "-${formatAmount(order.discount, currency)}",
                                        labelColor = receiptColors.textSecondary,
                                        valueColor = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                if (order.deliveryFee > 0.0) {
                                    ReceiptDataRow(deliveryFeeLabel, formatAmount(order.deliveryFee, currency), labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary)
                                    Spacer(Modifier.height(4.dp))
                                }
                                if (order.tax > 0.0) {
                                    ReceiptDataRow(taxLabel, formatAmount(order.tax, currency), labelColor = receiptColors.textSecondary, valueColor = receiptColors.textPrimary)
                                    Spacer(Modifier.height(4.dp))
                                }

                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = receiptColors.divider)
                                Spacer(Modifier.height(12.dp))

                                // Grand Total
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                            RoundedCornerShape(12.dp),
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                            RoundedCornerShape(12.dp),
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(totalLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = receiptColors.textPrimary)
                                    Text(formatAmount(order.total, currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = receiptColors.totalText)
                                }

                                // Refund info
                                if (order.hasReturns) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Refunded (${order.returnedItemCount})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                        Text("-${formatAmount(order.refundedAmount, currency)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Net Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = receiptColors.textPrimary)
                                        Text(formatAmount(order.netTotal, currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = receiptColors.totalText)
                                    }
                                }

                                // Notes
                                order.notes?.let { notes ->
                                    Spacer(Modifier.height(16.dp))
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(receiptColors.sectionBg, RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                    ) {
                                        Text(notesLabel, style = MaterialTheme.typography.labelSmall, color = receiptColors.textSecondary)
                                        Spacer(Modifier.height(2.dp))
                                        Text(notes, style = MaterialTheme.typography.bodySmall, color = receiptColors.textPrimary)
                                    }
                                }

                                Spacer(Modifier.height(20.dp))

                                // (Inline QR code block removed — merchant
                                //  asked to drop the QR from the receipt
                                //  itself. The dedicated "Show QR" action
                                //  button below still surfaces it as a
                                //  separate share dialog.)

                                // Footer
                                Text(
                                    text = thankYouStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = receiptColors.textSecondary,
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
                            maxItemsInEachRow = 3,
                        ) {
                            val btnMod = Modifier.weight(1f).height(48.dp)
                            // Fires the actual print job. Called either
                            // immediately (printer already configured) or
                            // from the picker's onSelected callback after
                            // the cashier picks one (first-time setup).
                            val firePrint: () -> Unit = {
                                printer.printOrder(
                                    order = order,
                                    vendor = vendor,
                                    language = receiptLang,
                                    jobName = "Receipt-${order.id.takeLast(8)}",
                                    qrCodeUrl = null,
                                    copies = 1,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    // Android: if no Bluetooth/USB
                                    // thermal printer is selected yet,
                                    // show the picker first. After the
                                    // cashier picks one, the dialog's
                                    // onSelected callback will call
                                    // firePrint(). Other platforms
                                    // hasPrinterConfigured() returns true
                                    // so we go straight to print.
                                    if (!printer.hasPrinterConfigured()) {
                                        showPrinterPicker = true
                                    } else {
                                        firePrint()
                                    }
                                },
                                modifier = btnMod,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(printStr, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            // "Change printer" — small text button that
                            // forgets the saved printer so the next tap
                            // on Print re-opens the picker. Hidden on
                            // platforms that don't use the picker.
                            if (printer.hasPrinterConfigured()) {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        printer.clearPrinterConfiguration()
                                        showPrinterPicker = true
                                    },
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text(
                                        text = "تغيير الطابعة / Change printer",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (uiState.digitalReceiptEnabled) {
                                OutlinedButton(
                                    onClick = { showQr = true },
                                    modifier = btnMod,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Icon(Icons.Default.QrCode, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(qrStr, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val html = buildReceiptHtml(order, vendor, receiptLang)
                                        platformActions.shareHtmlAsImage(html, "receipt-${order.id.takeLast(8)}")
                                    },
                                    modifier = btnMod,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(shareStr, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                // ── NFC tap-to-share (Android only) ─────────
                                //
                                // The button only renders when:
                                //   1. The device has NFC turned on
                                //      (isNfcAvailable === true) — iOS
                                //      and desktop always return false.
                                //   2. Digital receipts are enabled for
                                //      this vendor — without a share URL
                                //      there's nothing to broadcast.
                                //   3. We already have a non-blank
                                //      shareUrl — same reason.
                                //
                                // Tapping arms the HCE service and shows
                                // the "Hold phones together" dialog. The
                                // OS on the customer's phone handles the
                                // rest — when their NFC reader sees our
                                // emulated NDEF Type-4 Tag it opens the
                                // URL automatically.
                                val nfcShareUrl = uiState.shareUrl
                                val canShareViaNfc = platformActions.isNfcAvailable &&
                                    uiState.digitalReceiptEnabled &&
                                    !nfcShareUrl.isNullOrBlank()
                                if (canShareViaNfc) {
                                    OutlinedButton(
                                        onClick = {
                                            if (platformActions.shareUrlViaNfc(nfcShareUrl!!)) {
                                                showNfcDialog = true
                                            } else {
                                                platformActions.showToast(
                                                    nfcUnavailableMessage,
                                                )
                                            }
                                        },
                                        modifier = btnMod,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Icon(Icons.Default.Nfc, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            stringResource(Res.string.share_via_nfc),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text(doneStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // QR Dialog - shows order ID QR code directly
    if (showQr && uiState.order != null) {
        val order = uiState.order!!
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text(stringResource(Res.string.done)) }
            },
            title = {
                Text(
                    "${receiptTitle} QR",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        Modifier.size(220.dp).padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Image(
                            painter = rememberQrKitPainterMP(data = uiState.shareUrl ?: order.id),
                            contentDescription = "QR",
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "$orderNumLabel${order.displayId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }

    // NFC "Hold phones together" instruction dialog. The HCE service is
    // already armed at this point — this dialog just tells the cashier
    // what to do physically. Dismissing it clears the URL on the
    // service so a later accidental tap can't leak the receipt.
    if (showNfcDialog) {
        AlertDialog(
            onDismissRequest = {
                showNfcDialog = false
                platformActions.stopNfcShare()
            },
            icon = {
                Icon(
                    Icons.Default.Nfc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            },
            title = {
                Text(
                    stringResource(Res.string.nfc_share_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Text(
                    stringResource(Res.string.nfc_share_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Button(onClick = {
                    showNfcDialog = false
                    platformActions.stopNfcShare()
                }) {
                    Text(stringResource(Res.string.nfc_share_dialog_done))
                }
            },
            shape = RoundedCornerShape(20.dp),
        )
    }

    // ── Bluetooth/USB ESC/POS printer picker (Android only) ─────────
    //
    // Shown when the cashier taps Print for the first time, OR after
    // they tap "Change printer". On non-Android platforms the actual
    // is a no-op composable so this call is free.
    val orderForPrint = uiState.order
    val vendorForPrint = uiState.vendor
    PrinterSelectionDialogIfNeeded(
        show = showPrinterPicker,
        onSelected = {
            showPrinterPicker = false
            // Fire the print job now that the cashier has chosen a
            // printer. We re-read uiState.order/vendor at click time
            // rather than capturing them above so a fresh order
            // (after navigation away and back) prints correctly.
            if (orderForPrint != null) {
                printer.printOrder(
                    order = orderForPrint,
                    vendor = vendorForPrint,
                    language = receiptLang,
                    jobName = "Receipt-${orderForPrint.id.takeLast(8)}",
                    qrCodeUrl = null,
                    copies = 1,
                )
            }
        },
        onDismiss = { showPrinterPicker = false },
    )
}

@Composable
private fun ReceiptInfoSection(
    bgColor: Color = LocalReceiptColors.current.sectionBg,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(bgColor, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun DashedDivider(
    modifier: Modifier = Modifier,
    dashWidth: Dp = 6.dp,
    dashGap: Dp = 4.dp,
    thickness: Dp = 1.dp,
    color: Color = LocalReceiptColors.current.divider,
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
private fun ReceiptDataRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    labelColor: Color = LocalReceiptColors.current.textSecondary,
    valueColor: Color = LocalReceiptColors.current.textPrimary,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium, color = valueColor)
    }
}
