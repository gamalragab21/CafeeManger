package net.marllex.waselak.core.ui.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.ui.thermal.PrinterSelectionDialog
import net.marllex.waselak.core.ui.thermal.ReceiptBitmapRenderer
import net.marllex.waselak.core.ui.thermal.ThermalPrinterManager
import net.marllex.waselak.core.ui.thermal.toEscPosRasterBytes

/**
 * Android receipt printer — direct ESC/POS over Bluetooth or USB.
 *
 * Previous implementation went through Android's [PrintManager] which
 * required a third-party Print Service app (RawBT, etc.) for thermal
 * printers and produced a half-empty A4-preview that confused merchants.
 *
 * Current implementation uses the Dantsu ESC/POS library to talk
 * directly to the thermal printer:
 *   1. The merchant picks a printer ONCE via [PrinterSelectionDialog]
 *      (Bluetooth/USB), and the choice is persisted by
 *      [ThermalPrinterManager].
 *   2. Subsequent [printOrder] calls open a connection, format the
 *      receipt as ESC/POS markup (see [toEscPosMarkup]), and send the
 *      job — no system dialog, no preview, just paper.
 *   3. If the saved printer is no longer reachable (out of BT range,
 *      USB unplugged) we show a toast + the picker auto-reopens via
 *      the Compose layer.
 */
actual class ReceiptPrinter(private val context: Context) {

    private val manager = ThermalPrinterManager(context)

    // In-flight guard: prevents concurrent print attempts from stomping
    // on each other's Bluetooth socket. Without it, every rapid double-
    // tap on Print starts a new Thread that races for the printer's
    // RFCOMM channel — the printer can only serve one at a time, so
    // simultaneous connects produce `read ret: -1` errors AND leave the
    // printer in a stuck half-connected state that fails until power
    // cycle. Volatile + check-and-set is enough; we never expose this
    // to Kotlin coroutines so no Mutex needed.
    @Volatile
    private var printInFlight = false

    actual fun printHtml(htmlContent: String, jobName: String) {
        // Legacy HTML path is kept as a no-op stub. The HTML-driven
        // share-as-image flow uses platformActions.shareHtmlAsImage
        // directly, so nothing actually calls this method anymore.
        Log.w(TAG, "printHtml is deprecated on Android — use printOrder")
    }

    actual fun printOrder(
        order: Order,
        vendor: Vendor?,
        language: String,
        jobName: String,
        qrCodeUrl: String?,
        copies: Int,
    ) {
        val saved = manager.getSavedPrinter()
        if (saved == null) {
            // Caller (the Compose screen) should have checked
            // hasPrinterConfigured() first and shown the picker. If
            // we end up here it's a programmer error — surface it as
            // a toast rather than printing nothing silently.
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "اختر طابعة أولاً / Select a printer first", Toast.LENGTH_LONG).show()
            }
            return
        }

        // Reject overlapping print attempts. Without this, every double-
        // tap on Print spins up a parallel Thread, all racing for the
        // single RFCOMM channel the printer exposes. The printer
        // typically accepts the first and rejects (or worse, gets
        // stuck on) the rest. We let the in-flight one finish.
        synchronized(this) {
            if (printInFlight) {
                Log.i(TAG, "printOrder: ignoring tap — a print is already in flight")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "جاري الطباعة… / Printing…", Toast.LENGTH_SHORT).show()
                }
                return
            }
            printInFlight = true
        }

        val model = buildReceiptModel(order, vendor, language)
        val n = copies.coerceIn(1, 10)
        val bitmapWidth = if (saved.paperWidthMm <= 58) 384 else 576

        // Bluetooth + USB I/O + HTTP logo fetch + bitmap rendering all
        // block; do the whole pipeline off the main thread.
        Thread {
            var connection: DeviceConnection? = null
            try {
                // ── Logo fetch (best-effort, 5 s timeout) ─────────────
                val logoBitmap = model.vendorLogoUrl?.let { url ->
                    Log.i(TAG, "printOrder: fetching vendor logo from $url")
                    ReceiptBitmapRenderer.fetchLogoOrNull(url).also {
                        Log.i(TAG, "printOrder: logo fetched=${it != null} (${it?.width}x${it?.height})")
                    }
                }
                // ── Render receipt as bitmap, convert to raster bytes ─
                val bitmap = ReceiptBitmapRenderer(
                    widthPx = bitmapWidth,
                    logo = logoBitmap,
                    isRtl = model.isArabic,
                ).render(model)
                val rasterBody = bitmap.toEscPosRasterBytes()
                val init = byteArrayOf(0x1B, 0x40)                                  // ESC @
                // GS ( E pL pH fn [a] n — function 5: set print density.
                //   pL/pH = 0x03 0x00 (3 data bytes)
                //   fn    = 0x05      (function 5)
                //   a     = 0x00      (group selector — typically 0)
                //   n     = 0x64      (100% density)
                val maxDensity = byteArrayOf(0x1D, 0x28, 0x45, 0x03, 0x00, 0x05, 0x00, 0x64.toByte())
                val feed = byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A)                       // feed past cutter
                // GS V A n — feed n dots then FULL cut (was 0x42 B = partial).
                // Full cut + 50-dot feed (~6 mm) gives a clean separation
                // between copies — each receipt drops as its own paper slip
                // instead of staying attached to the next one.
                val cut = byteArrayOf(0x1D, 0x56, 0x41, 0x32)                        // GS V A 50
                val rawBytes = init + maxDensity + rasterBody + feed + cut
                Log.i(TAG, "printOrder: rendered bitmap ${bitmap.width}x${bitmap.height}, ${rawBytes.size} ESC/POS bytes total (max density)")

                Log.i(TAG, "printOrder: opening connection to ${saved.name} (${saved.type}:${saved.address})")
                connection = manager.openConnection(saved)
                if (connection == null) {
                    Log.w(TAG, "Saved printer unreachable: $saved")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "الطابعة غير متاحة / Printer not reachable",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@Thread
                }

                connection.connect()
                Log.i(TAG, "printOrder: BT socket open; sending ${rawBytes.size} bytes in one shot × $n cop(y/ies)")
                // Single-shot write per copy. The previous implementation
                // chunked the raster bytes into 64-byte fragments with a
                // 40 ms sleep between each, which on a ~10 KB receipt
                // produced ~150 chunks × 40 ms = 6+ seconds of idle gaps.
                // The printer's mechanism would print a strip, stall
                // waiting for the next chunk, print, stall — the visible
                // "4–5 stuttering prints" symptom. BluetoothSocket has its
                // own TCP-like flow control so the buffered OutputStream
                // happily takes the full payload in one call and the
                // printer prints continuously without pausing.
                repeat(n) { copyIdx ->
                    Log.d(TAG, "printOrder: sending copy ${copyIdx + 1}/$n")
                    connection.write(rawBytes)
                    connection.send()
                    if (copyIdx < n - 1) Thread.sleep(500)
                }
                Log.i(TAG, "printOrder: done; $n copy(ies) sent")
            } catch (e: EscPosConnectionException) {
                Log.e(TAG, "Printer connection error", e)
                showError("فشل الاتصال بالطابعة / Connection failed")
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected print failure", e)
                showError("فشلت الطباعة / Print failed")
            } finally {
                try { connection?.disconnect() } catch (e: Throwable) {
                    Log.w(TAG, "connection.disconnect failed: ${e.message}")
                }
                Log.d(TAG, "printOrder: connection disconnected")
                printInFlight = false
            }
        }.start()
    }

    actual fun hasPrinterConfigured(): Boolean = manager.getSavedPrinter() != null

    actual fun clearPrinterConfiguration() = manager.clearSavedPrinter()

    private fun showError(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "WaselakPrint"
    }
}

@Composable
actual fun rememberReceiptPrinter(): ReceiptPrinter {
    val context = LocalContext.current
    return remember(context) { ReceiptPrinter(context) }
}

actual fun getReceiptLanguage(): String {
    val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val raw = if (locales.isEmpty) {
        java.util.Locale.getDefault().language
    } else {
        locales[0]?.language ?: "ar"
    }
    return if (raw == "en") "en" else "ar"
}

@Composable
actual fun PrinterSelectionDialogIfNeeded(
    show: Boolean,
    onSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val manager = remember(context) { ThermalPrinterManager(context) }
    PrinterSelectionDialog(
        manager = manager,
        onSelected = { _ -> onSelected() },
        onDismiss = onDismiss,
    )
}
