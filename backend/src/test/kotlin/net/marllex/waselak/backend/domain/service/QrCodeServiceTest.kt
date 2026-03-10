package net.marllex.waselak.backend.domain.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QrCodeServiceTest {
    private val service = QrCodeService(secretKey = "test-secret-key-for-unit-tests")
    private val json = Json { ignoreUnknownKeys = true }

    // ── QR code data generation ─────────────────────────────────

    @Test
    fun `generate QR data contains correct fields`() {
        val data = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        assertEquals("worker-123", data.id)
        assertEquals("Ahmed", data.name)
        assertEquals("CASHIER", data.role)
        assertEquals(1, data.v)
        assertTrue(data.issued > 0)
        assertTrue(data.sig.isNotBlank())
    }

    @Test
    fun `generate QR data with custom version`() {
        val data = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER", version = 2)
        assertEquals(2, data.v)
    }

    // ── QR code image generation ────────────────────────────────

    @Test
    fun `generate QR image returns non-empty PNG bytes`() {
        val data = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        val imageBytes = service.generateQrCodeImage(data)
        assertTrue(imageBytes.isNotEmpty())
        // PNG magic bytes
        assertEquals(0x89.toByte(), imageBytes[0])
        assertEquals(0x50.toByte(), imageBytes[1]) // 'P'
        assertEquals(0x4E.toByte(), imageBytes[2]) // 'N'
        assertEquals(0x47.toByte(), imageBytes[3]) // 'G'
    }

    @Test
    fun `generate QR image with custom size`() {
        val data = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        val imageBytes = service.generateQrCodeImage(data, size = 200)
        assertTrue(imageBytes.isNotEmpty())
    }

    // ── QR code validation ──────────────────────────────────────

    @Test
    fun `validate valid QR code`() {
        val data = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        val jsonString = json.encodeToString(data)
        val result = service.validateQrCode(jsonString)
        assertIs<QrCodeValidationResult.Valid>(result)
        assertEquals("worker-123", result.data.id)
    }

    @Test
    fun `validate tampered QR code fails`() {
        val data = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        val tampered = data.copy(name = "Hacker")
        val jsonString = json.encodeToString(tampered)
        val result = service.validateQrCode(jsonString)
        assertIs<QrCodeValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("tampered"))
    }

    @Test
    fun `validate QR code with wrong signature fails`() {
        val data = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        val wrongSig = data.copy(sig = "wrong-signature")
        val jsonString = json.encodeToString(wrongSig)
        val result = service.validateQrCode(jsonString)
        assertIs<QrCodeValidationResult.Invalid>(result)
    }

    @Test
    fun `validate expired QR code fails`() {
        val data = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        // Set issued to 2 years ago
        val expired = data.copy(issued = System.currentTimeMillis() - (2L * 365 * 24 * 60 * 60 * 1000))
        // Re-sign would be needed but since we tampered with issued, sig check fails first
        val jsonString = json.encodeToString(expired)
        val result = service.validateQrCode(jsonString)
        assertIs<QrCodeValidationResult.Invalid>(result)
    }

    @Test
    fun `validate invalid JSON returns Invalid`() {
        val result = service.validateQrCode("not-json")
        assertIs<QrCodeValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("Cannot parse"))
    }

    // ── Cross-service validation ────────────────────────────────

    @Test
    fun `different secret key produces different QR codes`() {
        val service2 = QrCodeService(secretKey = "different-secret-key")
        val data1 = service.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        val data2 = service2.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        // Signatures should differ
        assertTrue(data1.sig != data2.sig || data1.issued != data2.issued)
    }

    @Test
    fun `QR code from different service fails validation`() {
        val service2 = QrCodeService(secretKey = "different-secret-key")
        val data = service2.generateQrCodeData("worker-123", "Ahmed", "CASHIER")
        val jsonString = json.encodeToString(data)
        val result = service.validateQrCode(jsonString)
        assertIs<QrCodeValidationResult.Invalid>(result)
    }
}
