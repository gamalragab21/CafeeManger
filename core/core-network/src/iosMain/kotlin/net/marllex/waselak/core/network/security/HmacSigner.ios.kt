package net.marllex.waselak.core.network.security

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

@OptIn(ExperimentalForeignApi::class)
actual object HmacSigner {
    actual fun hmacSha256(key: String, data: String): String = memScoped {
        val keyBytes = key.encodeToByteArray()
        val dataBytes = data.encodeToByteArray()
        val digestLength = CC_SHA256_DIGEST_LENGTH.toInt()
        val result = allocArray<UByteVar>(digestLength)

        keyBytes.usePinned { keyPinned ->
            dataBytes.usePinned { dataPinned ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    keyPinned.addressOf(0),
                    keyBytes.size.convert(),
                    dataPinned.addressOf(0),
                    dataBytes.size.convert(),
                    result
                )
            }
        }

        buildString {
            for (i in 0 until digestLength) {
                // Kotlin/Native doesn't expose String.format, so build the
                // hex string manually. Same output (lowercase 2-digit hex)
                // that "%02x".format(v) would have produced on JVM.
                val v = result[i].toInt() and 0xFF
                if (v < 0x10) append('0')
                append(v.toString(16))
            }
        }
    }

    actual fun sha256(data: String): String = memScoped {
        val dataBytes = data.encodeToByteArray()
        val digestLength = CC_SHA256_DIGEST_LENGTH.toInt()
        val result = allocArray<UByteVar>(digestLength)

        dataBytes.usePinned { pinned ->
            CC_SHA256(
                pinned.addressOf(0),
                dataBytes.size.convert(),
                result.reinterpret()
            )
        }

        buildString {
            for (i in 0 until digestLength) {
                // Kotlin/Native doesn't expose String.format, so build the
                // hex string manually. Same output (lowercase 2-digit hex)
                // that "%02x".format(v) would have produced on JVM.
                val v = result[i].toInt() and 0xFF
                if (v < 0x10) append('0')
                append(v.toString(16))
            }
        }
    }
}
