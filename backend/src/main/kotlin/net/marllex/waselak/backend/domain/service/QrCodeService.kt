package net.marllex.waselak.backend.domain.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Service for generating and validating QR codes for worker authentication
 * - Generate QR codes with worker data
 * - Sign QR codes with HMAC-SHA256
 * - Validate scanned QR codes
 * - Generate QR code images (PNG)
 */
class QrCodeService(
    private val secretKey: String = System.getenv("QR_SECRET_KEY") ?: "default-secret-key-change-in-production"
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val QR_SIZE = 300 // pixels
        private const val ALGORITHM = "HmacSHA256"
    }

    /**
     * Generate QR code data for a worker
     */
    fun generateQrCodeData(workerId: String, name: String, role: String, version: Int = 1): QrCodeData {
        val issuedAt = System.currentTimeMillis()
        val dataToSign = "$workerId|$name|$role|$issuedAt|$version"
        val signature = sign(dataToSign)

        return QrCodeData(
            v = version,
            id = workerId,
            name = name,
            role = role,
            issued = issuedAt,
            sig = signature
        )
    }

    /**
     * Generate QR code image as PNG byte array
     */
    fun generateQrCodeImage(qrCodeData: QrCodeData, size: Int = QR_SIZE): ByteArray {
        val jsonString = json.encodeToString(qrCodeData)
        
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )

        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(jsonString, BarcodeFormat.QR_CODE, size, size, hints)
        
        val outputStream = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
        
        return outputStream.toByteArray()
    }

    /**
     * Validate and parse QR code data
     * Returns worker ID if valid, null if invalid
     */
    fun validateQrCode(qrDataJson: String): QrCodeValidationResult {
        return try {
            val qrData = json.decodeFromString<QrCodeData>(qrDataJson)
            
            // Verify signature
            val dataToVerify = "${qrData.id}|${qrData.name}|${qrData.role}|${qrData.issued}|${qrData.v}"
            val expectedSignature = sign(dataToVerify)
            
            if (qrData.sig != expectedSignature) {
                return QrCodeValidationResult.Invalid("Invalid signature - QR code may be tampered")
            }

            // Check if QR code is too old (optional: 1 year expiration)
            val oneYearMs = 365L * 24 * 60 * 60 * 1000
            if (System.currentTimeMillis() - qrData.issued > oneYearMs) {
                return QrCodeValidationResult.Invalid("QR code expired - please regenerate")
            }

            QrCodeValidationResult.Valid(qrData)
        } catch (e: Exception) {
            QrCodeValidationResult.Invalid("Cannot parse QR code: ${e.message}")
        }
    }

    /**
     * Sign data using HMAC-SHA256
     */
    private fun sign(data: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), ALGORITHM)
        mac.init(secretKeySpec)
        val signature = mac.doFinal(data.toByteArray())
        return Base64.getEncoder().encodeToString(signature)
    }
}

/**
 * QR Code data structure
 */
@Serializable
data class QrCodeData(
    val v: Int,           // Version number
    val id: String,       // Worker ID (UUID)
    val name: String,     // Worker name
    val role: String,     // Worker role
    val issued: Long,     // Timestamp when QR was issued
    val sig: String       // HMAC signature
)

/**
 * QR Code validation result
 */
sealed class QrCodeValidationResult {
    data class Valid(val data: QrCodeData) : QrCodeValidationResult()
    data class Invalid(val reason: String) : QrCodeValidationResult()
}
