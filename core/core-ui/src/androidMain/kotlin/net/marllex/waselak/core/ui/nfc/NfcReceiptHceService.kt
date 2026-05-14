package net.marllex.waselak.core.ui.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * HCE service that emulates an NFC Forum Type-4 Tag containing a single
 * NDEF URI record. When another phone is tapped against the device, its
 * built-in NFC reader sees an NDEF URI and prompts the user to open it
 * (or follows the OS's tap-to-launch flow) — exactly what the merchant
 * wants for "share receipt by tap".
 *
 * Why HCE and not Android Beam?
 *   Android Beam (NdefPushMessage / setNdefPushMessage) is deprecated
 *   since Android 10 (API 29) and removed entirely in Android 14 (API
 *   34). HCE is the only supported way to push data phone-to-phone via
 *   NFC on modern Android.
 *
 * Protocol summary (NFC Forum NDEF Tag Application — Type 4):
 *   1. Reader sends SELECT AID `D2760000850101` → we reply 9000.
 *   2. Reader sends SELECT CC FILE  `00A4000C02E103` → we reply 9000.
 *   3. Reader sends READ BINARY on CC file → we reply with the 15-byte
 *      Capability Container that points the reader at the NDEF file
 *      (FileID `E104`, max size 0xFFFE, read-only, no write access).
 *   4. Reader sends SELECT NDEF FILE `00A4000C02E104` → we reply 9000.
 *   5. Reader sends READ BINARY on NDEF file with an offset+length.
 *      First two bytes of the file are the NLEN (big-endian length of
 *      the NDEF message), followed by the NDEF message bytes.
 *
 * Receiver behavior:
 *   The OS's stock NFC handler matches `NDEF_DISCOVERED` on URI records
 *   and opens the URL — typically in Chrome / the user's default
 *   browser. From there the user can save, share, or print the page.
 *
 * Setting the URL:
 *   The activity/composable sets [currentReceiptUrl] before showing the
 *   "tap to share" dialog. The service reads it lazily on every APDU,
 *   so updating the URL after the service is bound just works.
 *
 *   When the URL is null/blank, the service returns an empty NDEF
 *   message instead of failing — the receiver just sees an empty tag.
 */
class NfcReceiptHceService : HostApduService() {

    /**
     * Which NDEF file the reader has currently SELECTed. Reset to NONE
     * after a deselect (HCE callback `onDeactivated`) so a follow-up
     * read on the same connection can't accidentally hit the wrong file.
     */
    private var selectedFile: SelectedFile = SelectedFile.NONE

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return SW_UNKNOWN

        return when {
            // 1. SELECT NDEF Application by AID
            commandApdu.startsWith(SELECT_AID_HEADER) &&
                commandApdu.contains(NDEF_AID) -> {
                Log.d(TAG, "SELECT NDEF application")
                selectedFile = SelectedFile.NONE
                SW_OK
            }

            // 2. SELECT CC file (E103)
            commandApdu.contentEquals(SELECT_CC_FILE) -> {
                Log.d(TAG, "SELECT CC file")
                selectedFile = SelectedFile.CC
                SW_OK
            }

            // 3. SELECT NDEF file (E104)
            commandApdu.contentEquals(SELECT_NDEF_FILE) -> {
                Log.d(TAG, "SELECT NDEF file")
                selectedFile = SelectedFile.NDEF
                SW_OK
            }

            // 4. READ BINARY — parse offset + length, slice the right file.
            commandApdu.size >= 5 && commandApdu[1] == INS_READ_BINARY -> {
                val offset = ((commandApdu[2].toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)
                val length = commandApdu[4].toInt() and 0xFF
                val payload = when (selectedFile) {
                    SelectedFile.CC -> CC_FILE
                    SelectedFile.NDEF -> buildNdefFile(currentReceiptUrl.get())
                    SelectedFile.NONE -> {
                        Log.w(TAG, "READ BINARY before any SELECT — refusing")
                        return SW_UNKNOWN
                    }
                }
                if (offset >= payload.size) return SW_UNKNOWN
                val end = (offset + length).coerceAtMost(payload.size)
                val slice = payload.copyOfRange(offset, end)
                slice + SW_OK
            }

            else -> {
                Log.d(TAG, "Unhandled APDU: ${commandApdu.toHex()}")
                SW_UNKNOWN
            }
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated (reason=$reason)")
        selectedFile = SelectedFile.NONE
    }

    private enum class SelectedFile { NONE, CC, NDEF }

    companion object {
        private const val TAG = "WaselakNfcHce"

        /**
         * The URL the next reader will receive. Set this from the UI
         * before showing the "tap to share" dialog; clear it (set null)
         * when the dialog is dismissed so a stray tap later doesn't
         * accidentally send a stale receipt URL.
         *
         * AtomicReference so reads from the binder thread (HCE service)
         * and writes from the main thread (UI) don't race.
         */
        val currentReceiptUrl: AtomicReference<String?> = AtomicReference(null)

        // Status words.
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_UNKNOWN = byteArrayOf(0x6F.toByte(), 0x00)

        // APDU INS bytes.
        private const val INS_READ_BINARY: Byte = 0xB0.toByte()

        // SELECT AID header (CLA INS P1 P2 Lc).
        private val SELECT_AID_HEADER = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00,
        )

        // NDEF Tag Application AID — registered by the NFC Forum.
        private val NDEF_AID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )

        private val SELECT_CC_FILE = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03,
        )

        private val SELECT_NDEF_FILE = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04,
        )

        // Capability Container (15 bytes).
        //   CCLEN     00 0F      — total length of this CC
        //   Mapping   20         — NDEF mapping version 2.0
        //   MLe       00 7F      — max read size per APDU
        //   MLc       00 7F      — max write size per APDU
        //   TLV tag   04         — NDEF file control TLV
        //   TLV len   06         — 6 bytes follow
        //   NDEF FID  E1 04      — file ID of the NDEF file
        //   Max NDEF  FF FE      — max NDEF file size
        //   Read      00         — read freely
        //   Write     FF         — write forbidden (read-only tag)
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F,
            0x20,
            0x00, 0x7F,
            0x00, 0x7F,
            0x04,
            0x06,
            0xE1.toByte(), 0x04,
            0xFF.toByte(), 0xFE.toByte(),
            0x00,
            0xFF.toByte(),
        )

        /**
         * Encode the given URL as an NDEF Type-4 file:
         *   [NLEN_HI][NLEN_LO][NDEF message bytes…]
         *
         * The NDEF message is a single short URI record. URI prefix
         * abbreviations let us drop the `https://` etc. as a single
         * byte; we use 0x00 (no abbreviation) and write the full URL
         * to stay safe across every backend host config.
         */
        internal fun buildNdefFile(url: String?): ByteArray {
            val safe = url?.takeIf { it.isNotBlank() } ?: ""
            val uriBytes = safe.encodeToByteArray()
            val payload = ByteArray(uriBytes.size + 1).also {
                it[0] = 0x00                       // URI prefix abbreviation: none
                uriBytes.copyInto(it, destinationOffset = 1)
            }

            // NDEF record header for a single, short URI record:
            //   MB=1 ME=1 CF=0 SR=1 IL=0 TNF=001 (well-known)  → 0xD1
            //   Type length: 1
            //   Payload length: payload.size (short record fits in 1 byte)
            //   Type: 'U'
            //   Payload: prefix + URI
            val recordHeader = byteArrayOf(
                0xD1.toByte(),
                0x01,
                payload.size.toByte(),
                'U'.code.toByte(),
            )
            val ndefMessage = recordHeader + payload
            val nlen = byteArrayOf(
                ((ndefMessage.size shr 8) and 0xFF).toByte(),
                (ndefMessage.size and 0xFF).toByte(),
            )
            return nlen + ndefMessage
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
            if (size < prefix.size) return false
            for (i in prefix.indices) if (this[i] != prefix[i]) return false
            return true
        }

        private fun ByteArray.contains(needle: ByteArray): Boolean {
            if (needle.size > size) return false
            outer@ for (start in 0..(size - needle.size)) {
                for (i in needle.indices) if (this[start + i] != needle[i]) continue@outer
                return true
            }
            return false
        }

        private fun ByteArray.toHex(): String =
            joinToString("") { "%02X".format(it) }
    }
}
