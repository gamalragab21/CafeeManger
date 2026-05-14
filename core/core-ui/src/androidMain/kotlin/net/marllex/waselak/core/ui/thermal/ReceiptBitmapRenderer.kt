package net.marllex.waselak.core.ui.thermal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Log
import net.marllex.waselak.core.ui.platform.ReceiptModel
import java.net.HttpURLConnection
import java.net.URL

/**
 * Render a [ReceiptModel] to a monochrome Bitmap suitable for ESC/POS
 * raster printing (`GS v 0`).
 *
 * Visual hierarchy:
 *   1. Logo (centred, scaled to 60% of paper width)
 *   2. Vendor block — bold, sized for branding but compact
 *   3. Order ID — biggest single element on the page
 *   4. Order info / client rows — classic two-column "label … value"
 *   5. Items — name on its own line, "× qty …… price" row beneath
 *   6. Totals + grand total in columns
 *   7. Refunds, notes, footer
 *
 * Two element types are mixed:
 *   • [TextElement] — full-width line (centre / start / end aligned).
 *   • [RowElement]  — label on leading edge, value on trailing edge.
 *     In RTL mode the leading/trailing positions are mirrored so Arabic
 *     readers see labels on the right and values on the left, matching
 *     how Arabic receipts are normally printed.
 *
 * RTL: when [isRtl] is true (Arabic mode), StaticLayouts get an RTL
 * text-direction heuristic so multi-line Arabic wraps correctly, and
 * RowElements draw label/value flipped horizontally.
 */
class ReceiptBitmapRenderer(
    private val widthPx: Int = 576,
    private val logo: Bitmap? = null,
    private val isRtl: Boolean = false,
) {

    // ── Paints ───────────────────────────────────────────────────────
    private val pBody = paint(24f, bold = false)
    private val pBodyBold = paint(24f, bold = true)
    private val pVendor = paint(36f, bold = true)
    private val pOrderId = paint(44f, bold = true)
    private val pGrandTotal = paint(30f, bold = true)

    fun render(model: ReceiptModel): Bitmap {
        val elements = buildElements(model)

        val innerWidth = widthPx - H_PADDING * 2
        val maxLogoWidth = (widthPx * 0.6f).toInt()
        val scaledLogo = logo?.let { monochromeLogo(scaleToWidth(it, maxLogoWidth)) }
        val logoHeight = scaledLogo?.let { it.height + LOGO_BOTTOM_PADDING } ?: 0
        val textHeight = elements.sumOf { it.height(innerWidth) }
        val totalHeight = V_PADDING * 2 + logoHeight + textHeight

        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = V_PADDING.toFloat()
        scaledLogo?.let {
            val xOffset = ((widthPx - it.width) / 2).toFloat()
            canvas.drawBitmap(it, xOffset, y, null)
            y += it.height + LOGO_BOTTOM_PADDING
        }
        for (el in elements) {
            canvas.save()
            canvas.translate(H_PADDING.toFloat(), 0f)
            y = el.draw(canvas, innerWidth, y)
            canvas.restore()
        }
        return bitmap
    }

    // ── Element types ────────────────────────────────────────────────

    private sealed interface Element {
        fun height(width: Int): Int
        fun draw(canvas: Canvas, width: Int, yStart: Float): Float
    }

    /** Full-width line, any alignment, may wrap to multiple lines. */
    private inner class TextElement(
        val text: String,
        val paint: TextPaint,
        val align: Layout.Alignment,
    ) : Element {
        private fun layout(width: Int): StaticLayout {
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(align)
                .setLineSpacing(1f, 1f)
                .setIncludePad(false)
            // Force RTL text direction for Arabic mode so multi-line
            // Arabic wraps right-to-left and isolated digits embed in
            // the correct visual position.
            builder.setTextDirection(
                if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR,
            )
            return builder.build()
        }

        override fun height(width: Int): Int = layout(width).height
        override fun draw(canvas: Canvas, width: Int, yStart: Float): Float {
            val l = layout(width)
            canvas.save()
            canvas.translate(0f, yStart)
            l.draw(canvas)
            canvas.restore()
            return yStart + l.height
        }
    }

    /**
     * Two-column row: label aligned to the leading edge, value to the
     * trailing edge, on the same baseline.
     *
     * In RTL mode the positions are mirrored: label goes flush-right,
     * value goes flush-left — that's how Arabic receipts read.
     */
    private inner class RowElement(
        val label: String,
        val value: String,
        val labelPaint: TextPaint,
        val valuePaint: TextPaint = labelPaint,
    ) : Element {
        override fun height(width: Int): Int {
            val fm = labelPaint.fontMetrics
            return ((fm.bottom - fm.top) + 2f).toInt()
        }

        override fun draw(canvas: Canvas, width: Int, yStart: Float): Float {
            val fm = labelPaint.fontMetrics
            val baseline = yStart - fm.top
            if (isRtl) {
                // Arabic: label on the right, value on the left.
                val labelWidth = labelPaint.measureText(label)
                canvas.drawText(label, width - labelWidth, baseline, labelPaint)
                canvas.drawText(value, 0f, baseline, valuePaint)
            } else {
                // English: label on the left, value on the right.
                canvas.drawText(label, 0f, baseline, labelPaint)
                val valueWidth = valuePaint.measureText(value)
                canvas.drawText(value, width - valueWidth, baseline, valuePaint)
            }
            return yStart + height(width)
        }
    }

    // ── Element list builder ─────────────────────────────────────────

    private fun buildElements(model: ReceiptModel): List<Element> {
        val out = mutableListOf<Element>()
        val center = Layout.Alignment.ALIGN_CENTER
        val natural = Layout.Alignment.ALIGN_NORMAL // Locale-aware (left for LTR, right for RTL)
        val thinDivider = "─".repeat(30)
        val thickDivider = "═".repeat(18)

        fun blank(px: Int = 6) = TextElement(" ", paint(px.toFloat()), center)

        // ── Vendor block ─────────────────────────────────────────────
        out += TextElement(model.vendorName, pVendor, center)
        model.vendorAddress?.let { out += TextElement(it, pBody, center) }
        model.vendorPhone?.let {
            val label = if (model.isArabic) "هاتف" else "Tel"
            out += TextElement("$label: $it", pBodyBold, center)
        }
        out += blank()
        out += TextElement(thickDivider, pBodyBold, center)

        // ── Order ID — biggest single element ────────────────────────
        out += TextElement(
            "${model.orderIdLabel} ${model.orderIdValue}",
            pOrderId,
            center,
        )
        out += TextElement(thinDivider, pBody, center)

        // ── Order info rows (two-column, RTL-aware) ──────────────────
        for ((label, value) in model.orderRows) {
            out += RowElement(label, value, pBody, pBodyBold)
        }

        // ── Client info ──────────────────────────────────────────────
        if (model.clientRows.isNotEmpty()) {
            out += TextElement(thinDivider, pBody, center)
            for ((label, value) in model.clientRows) {
                out += RowElement(label, value, pBody, pBodyBold)
            }
        }

        // ── Items ────────────────────────────────────────────────────
        out += TextElement(thickDivider, pBodyBold, center)
        for (item in model.items) {
            // Item name flows on its own line, naturally aligned to
            // the leading edge of the paper for the current language.
            out += TextElement(item.name, pBodyBold, natural)
            out += RowElement("  × ${item.qty}", item.price, pBody)
        }
        out += TextElement(thinDivider, pBody, center)

        // ── Totals ───────────────────────────────────────────────────
        for ((label, value) in model.totalsRows) {
            out += RowElement(label, value, pBody, pBodyBold)
        }
        out += blank(4)
        out += RowElement(
            model.grandTotalLabel,
            model.grandTotalValue,
            pGrandTotal,
        )
        out += TextElement(thickDivider, pBodyBold, center)

        // ── Refunds + net total ──────────────────────────────────────
        if (model.refundRows.isNotEmpty()) {
            for ((label, value) in model.refundRows) {
                out += RowElement(label, value, pBody, pBodyBold)
            }
        }
        if (model.netTotalLabel != null && model.netTotalValue != null) {
            out += RowElement(model.netTotalLabel!!, model.netTotalValue!!, pBodyBold)
            out += TextElement(thinDivider, pBody, center)
        }

        // ── Notes ────────────────────────────────────────────────────
        if (model.notesLabel != null && model.notesText != null) {
            out += blank()
            out += TextElement("${model.notesLabel}:", pBodyBold, natural)
            out += TextElement(model.notesText!!, pBody, natural)
        }

        // ── Footer ───────────────────────────────────────────────────
        out += blank()
        out += TextElement(model.footerThanks, pBodyBold, center)
        // Branding line — small, centered, just under the thanks. Always
        // present, no toggle. Kept on its own paint at 18px so it reads
        // as a fine-print credit rather than competing with the thanks.
        out += blank(8)
        out += TextElement(model.poweredBy, paint(18f, bold = false), center)
        out += blank(16)
        return out
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun paint(size: Float, bold: Boolean = false): TextPaint =
        TextPaint().apply {
            textSize = size
            color = Color.BLACK
            isAntiAlias = true
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun scaleToWidth(src: Bitmap, targetWidth: Int): Bitmap {
        if (src.width == targetWidth) return src
        val scale = targetWidth.toFloat() / src.width
        val targetHeight = (src.height * scale).toInt()
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
    }

    /**
     * Pre-binarise a logo to pure black/white so it survives the
     * monochrome raster conversion. The default [toEscPosRasterBytes]
     * threshold of 128 turns mid-gray and lighter pixels into "no
     * print" — light-coloured logos would come out almost invisible.
     * We threshold here at 180 (more permissive) so even faint pixels
     * in the source produce visible ink. Transparent PNG pixels are
     * treated as white (paper background).
     */
    private fun monochromeLogo(src: Bitmap, threshold: Int = 180): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = Color.alpha(p)
            val gray = if (a < 128) 255
            else (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
            pixels[i] = if (gray < threshold) Color.BLACK else Color.WHITE
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    companion object {
        private const val V_PADDING = 12
        // Horizontal padding on left + right of the content. Pushes
        // labels in from the paper edge so the receipt has breathing
        // room. 28 px ≈ 3.5 mm on 80 mm paper — visible margin but
        // doesn't waste line budget.
        private const val H_PADDING = 28
        private const val LOGO_BOTTOM_PADDING = 12

        /**
         * Best-effort synchronous logo fetch. Returns null on any error
         * (timeout, invalid URL, decode failure, etc) — the receipt
         * still prints fine without a logo.
         *
         * Safe to call on the print Thread (it's already off the main
         * thread). 5-second timeouts on connect + read so a slow CDN
         * doesn't block the print indefinitely.
         *
         * Diagnostics: every step logs at INFO with the URL + outcome
         * so the merchant-facing log says exactly why a logo didn't
         * print when one was expected.
         */
        fun fetchLogoOrNull(url: String): Bitmap? = try {
            Log.i(TAG, "Logo fetch: GET $url")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            val responseCode = conn.responseCode
            Log.i(TAG, "Logo fetch: HTTP $responseCode")
            if (responseCode !in 200..299) {
                conn.disconnect()
                Log.w(TAG, "Logo fetch: non-2xx, skipping")
                null
            } else {
                conn.inputStream.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp == null) {
                        Log.w(TAG, "Logo fetch: decodeStream returned null (corrupt or unsupported format)")
                    } else {
                        Log.i(TAG, "Logo fetch: decoded ${bmp.width}x${bmp.height}")
                    }
                    bmp
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Logo fetch failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

        private const val TAG = "ReceiptBitmapRenderer"
    }
}

/**
 * Convert a monochrome bitmap into ESC/POS raster bytes (`GS v 0`).
 *
 * Each scan-line of the bitmap becomes `(width+7)/8` bytes, with each
 * byte representing 8 horizontal pixels (MSB = leftmost). A pixel
 * counts as "on" (1) when its grayscale value is below the threshold.
 *
 * Some XP-class printer firmwares only honour up to ~ 256 rows per
 * `GS v 0` call before resetting the print buffer; we therefore split
 * the bitmap into [BAND_HEIGHT]-row bands and emit one `GS v 0` per
 * band. The print head receives a continuous strip of paper — band
 * boundaries are invisible.
 */
fun Bitmap.toEscPosRasterBytes(threshold: Int = 128): ByteArray {
    val widthBytes = (width + 7) / 8
    val out = java.io.ByteArrayOutputStream()

    var rowsRemaining = height
    var rowStart = 0
    while (rowsRemaining > 0) {
        val bandHeight = minOf(rowsRemaining, BAND_HEIGHT)
        val xL = widthBytes and 0xFF
        val xH = (widthBytes shr 8) and 0xFF
        val yL = bandHeight and 0xFF
        val yH = (bandHeight shr 8) and 0xFF
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL.toByte(), xH.toByte(), yL.toByte(), yH.toByte()))

        for (row in rowStart until rowStart + bandHeight) {
            for (byteCol in 0 until widthBytes) {
                var b = 0
                for (bit in 0 until 8) {
                    val col = byteCol * 8 + bit
                    if (col < width) {
                        val pixel = getPixel(col, row)
                        val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                        if (gray < threshold) {
                            b = b or (1 shl (7 - bit))
                        }
                    }
                }
                out.write(b)
            }
        }

        rowStart += bandHeight
        rowsRemaining -= bandHeight
    }
    return out.toByteArray()
}

private const val BAND_HEIGHT = 96
