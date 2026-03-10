package net.marllex.waselak.backend.domain.service

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PinServiceTest {
    private val service = PinService()

    // ── PIN validation ──────────────────────────────────────────

    @Test
    fun `valid 4-digit PIN`() {
        assertTrue(service.isValidPin("1234"))
    }

    @Test
    fun `valid 5-digit PIN`() {
        assertTrue(service.isValidPin("12345"))
    }

    @Test
    fun `valid 6-digit PIN`() {
        assertTrue(service.isValidPin("123456"))
    }

    @Test
    fun `reject 3-digit PIN (too short)`() {
        assertFalse(service.isValidPin("123"))
    }

    @Test
    fun `reject 7-digit PIN (too long)`() {
        assertFalse(service.isValidPin("1234567"))
    }

    @Test
    fun `reject non-numeric PIN`() {
        assertFalse(service.isValidPin("abcd"))
        assertFalse(service.isValidPin("12ab"))
        assertFalse(service.isValidPin("12 4"))
    }

    @Test
    fun `reject empty PIN`() {
        assertFalse(service.isValidPin(""))
    }

    // ── PIN hashing and verification ────────────────────────────

    @Test
    fun `hash and verify correct PIN`() {
        val pin = "1234"
        val hash = service.hashPin(pin)
        assertTrue(service.verifyPin(pin, hash))
    }

    @Test
    fun `verify wrong PIN returns false`() {
        val hash = service.hashPin("1234")
        assertFalse(service.verifyPin("5678", hash))
    }

    @Test
    fun `hash invalid PIN throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            service.hashPin("abc")
        }
    }

    @Test
    fun `hash too short PIN throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            service.hashPin("12")
        }
    }

    // ── Default PIN generation ──────────────────────────────────

    @Test
    fun `generate default PIN from phone number`() {
        val pin = service.generateDefaultPin("worker-1", "01234567890")
        assertEquals("7890", pin)
    }

    @Test
    fun `generate default PIN from short phone falls back to random`() {
        val pin = service.generateDefaultPin("worker-1", "12")
        assertEquals(4, pin.length)
        assertTrue(pin.all { it.isDigit() })
    }

    @Test
    fun `generate default PIN from null phone falls back to random`() {
        val pin = service.generateDefaultPin("worker-1", null)
        assertEquals(4, pin.length)
        assertTrue(pin.all { it.isDigit() })
    }

    @Test
    fun `generate default PIN from blank phone falls back to random`() {
        val pin = service.generateDefaultPin("worker-1", "")
        assertEquals(4, pin.length)
        assertTrue(pin.all { it.isDigit() })
    }

    // ── Rate limiting ───────────────────────────────────────────

    @Test
    fun `allow first 3 PIN attempts`() {
        val workerId = "rate-test-${System.nanoTime()}"
        assertTrue(service.canAttemptPin(workerId))
        assertTrue(service.canAttemptPin(workerId))
        assertTrue(service.canAttemptPin(workerId))
    }

    @Test
    fun `block 4th PIN attempt`() {
        val workerId = "rate-block-${System.nanoTime()}"
        service.canAttemptPin(workerId)
        service.canAttemptPin(workerId)
        service.canAttemptPin(workerId)
        assertFalse(service.canAttemptPin(workerId))
    }

    @Test
    fun `reset clears rate limit`() {
        val workerId = "rate-reset-${System.nanoTime()}"
        service.canAttemptPin(workerId)
        service.canAttemptPin(workerId)
        service.canAttemptPin(workerId)
        assertFalse(service.canAttemptPin(workerId))

        service.resetRateLimit(workerId)
        assertTrue(service.canAttemptPin(workerId))
    }

    @Test
    fun `lockout time is zero when not locked`() {
        val workerId = "lockout-none-${System.nanoTime()}"
        assertEquals(0L, service.getRemainingLockoutTime(workerId))
    }

    @Test
    fun `lockout time is positive when locked`() {
        val workerId = "lockout-active-${System.nanoTime()}"
        service.canAttemptPin(workerId)
        service.canAttemptPin(workerId)
        service.canAttemptPin(workerId)
        assertTrue(service.getRemainingLockoutTime(workerId) > 0)
    }
}
