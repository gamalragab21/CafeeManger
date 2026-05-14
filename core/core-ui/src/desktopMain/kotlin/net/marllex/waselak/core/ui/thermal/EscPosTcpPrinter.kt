package net.marllex.waselak.core.ui.thermal

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.prefs.Preferences

/**
 * Persisted desktop thermal-printer settings.
 *
 * Stored in [Preferences] (the JVM's per-user settings store — actual
 * backend is the macOS plist / Windows registry / Linux .java/.userPrefs).
 * No separate config file or DB — keeps the install lightweight.
 *
 * Currently we only support TCP/network printers (port 9100 is the de
 * facto standard for ESC/POS over Ethernet/WiFi). Bluetooth on JVM
 * desktop requires BlueCove + a paired OS profile and is a separate
 * follow-up; we punt on it for now since network printers are far more
 * common in real merchant deployments.
 */
data class DesktopPrinterConfig(
    val host: String,
    val port: Int = 9100,
    val widthMm: Int = 80,
)

object DesktopPrinterPrefs {
    private const val NODE = "net/marllex/waselak/thermal"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_WIDTH = "width_mm"

    private val prefs: Preferences get() = Preferences.userRoot().node(NODE)

    fun load(): DesktopPrinterConfig? {
        val host = prefs.get(KEY_HOST, null)?.takeIf { it.isNotBlank() } ?: return null
        return DesktopPrinterConfig(
            host = host,
            port = prefs.getInt(KEY_PORT, 9100),
            widthMm = prefs.getInt(KEY_WIDTH, 80),
        )
    }

    fun save(config: DesktopPrinterConfig) {
        prefs.put(KEY_HOST, config.host)
        prefs.putInt(KEY_PORT, config.port)
        prefs.putInt(KEY_WIDTH, config.widthMm)
        prefs.flush()
    }

    fun clear() {
        prefs.remove(KEY_HOST)
        prefs.remove(KEY_PORT)
        prefs.remove(KEY_WIDTH)
        prefs.flush()
    }
}

/**
 * Send raw ESC/POS bytes to a network thermal printer over TCP.
 *
 * The socket is opened, the full byte payload is written, the stream is
 * flushed, and the socket is closed — one print job per connection.
 * That matches how every "JetDirect" / "Raw 9100" printer protocol
 * works and avoids leaking sockets when the user prints many receipts.
 *
 * Returns success/failure as a [Result] so the caller can surface a
 * "printer unreachable" toast without a crash. Connection timeout is
 * deliberately short (3 s) — if the printer is offline, the cashier
 * shouldn't have to wait the OS-default 30-60 s.
 */
fun sendEscPosOverTcp(config: DesktopPrinterConfig, bytes: ByteArray): Result<Unit> =
    runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(config.host, config.port), 3000)
            socket.tcpNoDelay = true
            socket.getOutputStream().use { out ->
                out.write(bytes)
                out.flush()
            }
        }
    }.recoverCatching { e ->
        // Re-throw with a clearer message so the caller can show it
        // verbatim to the merchant ("Connection refused", "Network
        // unreachable", etc.).
        throw IOException("Print failed: ${e.message}", e)
    }
