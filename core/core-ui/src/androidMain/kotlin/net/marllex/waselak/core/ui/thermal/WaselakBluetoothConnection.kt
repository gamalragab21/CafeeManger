package net.marllex.waselak.core.ui.thermal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import java.io.IOException
import java.util.UUID

/**
 * Custom Bluetooth ESC/POS connection that fixes the
 * "read failed, socket might closed or timeout, read ret: -1" crash
 * we hit with the stock [com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection].
 *
 * Why the stock connection breaks
 * --------------------------------
 * The stock connection uses `createRfcommSocketToServiceRecord` —
 * Android's "secure" RFCOMM socket — which requires the peer to honour
 * an encrypted-pairing handshake. Most generic Chinese ESC/POS thermal
 * printers (the ones merchants buy on AliExpress / Alibaba, brandless,
 * with names like `BT-58`, `606E41_1`, `XPrinter`, etc.) accept the
 * pairing prompt but **refuse the secure handshake at RFCOMM time**,
 * so `socket.connect()` waits a couple of seconds then closes with
 * "read ret: -1".
 *
 * The fix
 * -------
 * Try variants in order, return the first that succeeds. Same UUID
 * (well-known SerialPortService.0001) all the way through:
 *
 *   1. **`createInsecureRfcommSocketToServiceRecord(uuid)`** — skips
 *      the encryption / authenticated-pairing requirement. This is
 *      what 95% of cheap thermal printers actually use, and what every
 *      "Bluetooth Print" / RawBT-style app on the Play Store falls back
 *      to. Fixes the issue for nearly all merchants in one shot.
 *
 *   2. **Reflection-based `createRfcommSocket(int channel)`** — calls a
 *      private overload that connects directly on a specified RFCOMM
 *      channel (usually channel 1 for SPP). This is the "deep magic"
 *      workaround for printers that even reject the insecure socket
 *      because they don't implement SDP service discovery at all.
 *
 * If both fail we throw the same [EscPosConnectionException] the stock
 * library throws, so the surrounding `printOrder` catch + toast keep
 * working unchanged.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT/SCAN granted at the picker dialog
class WaselakBluetoothConnection(val device: BluetoothDevice) : DeviceConnection() {

    private var socket: BluetoothSocket? = null

    override fun connect(): DeviceConnection {
        if (isConnected) return this

        // Retry loop. The first attempt sometimes fails with
        // `read ret: -1` because the printer's RFCOMM service is still
        // releasing the previous socket (e.g. from a rapid double-tap
        // on Print, or a previous print that didn't disconnect cleanly).
        // A short delay + retry recovers without a power-cycle on the
        // common case. Three attempts is enough; if all three fail the
        // printer is genuinely off / out of range / paper-jammed and
        // the merchant needs to fix it physically.
        var lastError: Throwable? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            if (attempt > 1) {
                Log.i(TAG, "Retrying connect (attempt $attempt/$MAX_ATTEMPTS) after ${RETRY_DELAY_MS}ms")
                try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) {}
            }

            // ── Attempt N.A — insecure RFCOMM socket ───────────────
            try {
                val s = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                socket = s
                outputStream = s.outputStream
                Log.i(TAG, "Connected via insecure RFCOMM to ${device.address} (attempt $attempt)")
                return this
            } catch (e: IOException) {
                Log.w(TAG, "Insecure RFCOMM failed on attempt $attempt: ${e.message}")
                lastError = e
                closeSocketQuietly()
            } catch (e: SecurityException) {
                Log.e(TAG, "BT permission denied during connect: ${e.message}")
                throw EscPosConnectionException("Bluetooth permission denied")
            }

            // ── Attempt N.B — reflection: createRfcommSocket(1) ─────
            try {
                val createRfcommSocket = device.javaClass.getMethod(
                    "createRfcommSocket",
                    Int::class.javaPrimitiveType,
                )
                val s = createRfcommSocket.invoke(device, 1) as BluetoothSocket
                s.connect()
                socket = s
                outputStream = s.outputStream
                Log.i(TAG, "Connected via reflection channel=1 to ${device.address} (attempt $attempt)")
                return this
            } catch (e: Throwable) {
                Log.w(TAG, "Reflection RFCOMM failed on attempt $attempt: ${e.message}")
                lastError = e
                closeSocketQuietly()
            }
        }

        Log.e(TAG, "All $MAX_ATTEMPTS connect attempts failed (last: ${lastError?.message})")
        throw EscPosConnectionException("Unable to connect to bluetooth device.")
    }

    override fun disconnect(): DeviceConnection {
        try { outputStream?.close() } catch (_: Throwable) {}
        closeSocketQuietly()
        outputStream = null
        return this
    }

    private fun closeSocketQuietly() {
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
    }

    companion object {
        private const val TAG = "WaselakBtConn"
        // SerialPortService.0001 — the universal RFCOMM UUID for ESC/POS
        // thermal printers (and the same one the stock library uses).
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        // 3 attempts × (insecure + reflection) = up to 6 socket creates,
        // separated by 600ms each, giving the printer ~1.8s total
        // breathing room to release a stuck RFCOMM channel.
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 600L
    }
}
