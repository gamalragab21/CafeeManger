package net.marllex.waselak.core.ui.thermal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections

/**
 * Owns the merchant's preferred ESC/POS printer.
 *
 * Public surface:
 *   • [discoverPrinters] — scan currently-paired Bluetooth + plugged-in
 *     USB printers; returns a flat list the picker UI renders.
 *   • [getSavedPrinter] / [savePrinter] / [clearSavedPrinter] —
 *     persistence layer. The cashier picks a printer ONCE; subsequent
 *     prints go silently. They can change it later via the "Settings →
 *     Printer" entry or a long-press on the Print button.
 *   • [openConnection] — turn a saved [PrinterConfig] back into a live
 *     [DeviceConnection] just before printing.
 *
 * Persistence schema (SharedPreferences "waselak_printer"):
 *   - "type":    "bluetooth" | "usb"
 *   - "address": MAC for Bluetooth, device path for USB
 *   - "name":    user-friendly label (last seen) — for the status row only
 *   - "width_mm": 80 or 58 — paper width in mm, drives ESC/POS char/line
 *
 * Why preference cache instead of relying on the library's own state:
 *   The Dantsu library re-scans on every instantiation, which is fine —
 *   but the *which* printer to use is a merchant choice that needs to
 *   survive app restarts and shouldn't require a re-scan dialog every
 *   time the cashier hits Print.
 */
class ThermalPrinterManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Discovery ─────────────────────────────────────────────────────

    /**
     * Build a flat list of every Bluetooth + USB ESC/POS device the
     * library can see right now. Doesn't filter — the user picks one.
     *
     * Exceptions during enumeration are swallowed per source (BT may
     * be off, USB host may be unsupported) so the picker dialog can
     * still render the other source's devices.
     */
    fun discoverPrinters(): List<DiscoveredPrinter> {
        val out = mutableListOf<DiscoveredPrinter>()

        // ── Bluetooth (already paired) ────────────────────────────
        try {
            BluetoothPrintersConnections().list?.forEach { conn ->
                out += DiscoveredPrinter(
                    type = "bluetooth",
                    address = conn.device.address,
                    name = conn.device.name ?: conn.device.address,
                )
            }
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT denied on Android 12+ — the picker UI
            // catches this and prompts for runtime permission.
            Log.w(TAG, "BT enumeration denied: ${e.message}")
        } catch (e: Throwable) {
            Log.w(TAG, "BT enumeration failed: ${e.message}")
        }

        // ── USB host (currently plugged in) ───────────────────────
        try {
            UsbPrintersConnections(context).list?.forEach { conn ->
                val dev = conn.device
                out += DiscoveredPrinter(
                    type = "usb",
                    address = dev.deviceName,
                    name = dev.productName ?: "USB printer ${dev.deviceName.substringAfterLast('/')}",
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "USB enumeration failed: ${e.message}")
        }

        return out
    }

    // ── Persistence ───────────────────────────────────────────────────

    fun getSavedPrinter(): PrinterConfig? {
        val type = prefs.getString(KEY_TYPE, null) ?: return null
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        return PrinterConfig(
            type = type,
            address = address,
            name = prefs.getString(KEY_NAME, address) ?: address,
            paperWidthMm = prefs.getInt(KEY_WIDTH_MM, DEFAULT_PAPER_WIDTH_MM),
        )
    }

    fun savePrinter(config: PrinterConfig) {
        prefs.edit()
            .putString(KEY_TYPE, config.type)
            .putString(KEY_ADDRESS, config.address)
            .putString(KEY_NAME, config.name)
            .putInt(KEY_WIDTH_MM, config.paperWidthMm)
            .apply()
    }

    fun clearSavedPrinter() {
        prefs.edit().clear().apply()
    }

    // ── Connection ────────────────────────────────────────────────────

    /**
     * Open a live [DeviceConnection] for the given saved config.
     *
     * Returns null when:
     *   • Bluetooth saved but the paired device is no longer in range / paired
     *   • USB saved but the cable is unplugged
     *   • Permissions denied
     *
     * Caller should fall back to the picker dialog when this returns null
     * so the merchant can pick a different printer instead of seeing an
     * opaque error.
     */
    fun openConnection(config: PrinterConfig): DeviceConnection? {
        return when (config.type) {
            "bluetooth" -> try {
                // Find the paired device by MAC + wrap it in our custom
                // WaselakBluetoothConnection (insecure-RFCOMM-first +
                // reflection fallback). The stock library connection's
                // secure-only handshake fails on most generic ESC/POS
                // thermal printers — see WaselakBluetoothConnection
                // for the full story.
                val device = BluetoothPrintersConnections().list?.firstOrNull {
                    it.device.address == config.address
                }?.device
                device?.let { WaselakBluetoothConnection(it) }
            } catch (e: Throwable) {
                Log.w(TAG, "BT openConnection failed: ${e.message}")
                null
            }
            "usb" -> try {
                UsbPrintersConnections(context).list?.firstOrNull {
                    it.device.deviceName == config.address
                }
            } catch (e: Throwable) {
                Log.w(TAG, "USB openConnection failed: ${e.message}")
                null
            }
            else -> null
        }
    }

    companion object {
        private const val TAG = "ThermalPrinterMgr"
        private const val PREFS_NAME = "waselak_printer"
        private const val KEY_TYPE = "type"
        private const val KEY_ADDRESS = "address"
        private const val KEY_NAME = "name"
        private const val KEY_WIDTH_MM = "width_mm"
        const val DEFAULT_PAPER_WIDTH_MM = 80
    }
}

/**
 * Persistent printer choice. Survives app restarts.
 */
data class PrinterConfig(
    val type: String,       // "bluetooth" or "usb"
    val address: String,    // MAC (BT) or device path (USB)
    val name: String,       // user-friendly label for status row
    val paperWidthMm: Int = ThermalPrinterManager.DEFAULT_PAPER_WIDTH_MM,
)

/**
 * One device row in the printer-picker UI.
 */
data class DiscoveredPrinter(
    val type: String,
    val address: String,
    val name: String,
) {
    fun toConfig(paperWidthMm: Int = ThermalPrinterManager.DEFAULT_PAPER_WIDTH_MM) =
        PrinterConfig(type = type, address = address, name = name, paperWidthMm = paperWidthMm)
}
