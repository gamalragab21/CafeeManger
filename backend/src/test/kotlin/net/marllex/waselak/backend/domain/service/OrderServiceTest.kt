package net.marllex.waselak.backend.domain.service

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class OrderServiceTest {
    private val service = OrderService()

    // ── DINE_IN channel ─────────────────────────────────────────

    @Test
    fun `dine-in happy path CREATED to COMPLETED`() {
        assertTrue(service.validateStatusTransition("CREATED", "IN_PREPARATION", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("IN_PREPARATION", "READY", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("READY", "SERVED", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("SERVED", "COMPLETED", "DINE_IN", "CASHIER"))
    }

    @Test
    fun `dine-in COMPLETED can be refunded`() {
        assertTrue(service.validateStatusTransition("COMPLETED", "REFUNDED", "DINE_IN", "CASHIER"))
    }

    @Test
    fun `dine-in can cancel from any active state`() {
        assertTrue(service.validateStatusTransition("CREATED", "CANCELED", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("IN_PREPARATION", "CANCELED", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("READY", "CANCELED", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("SERVED", "CANCELED", "DINE_IN", "CASHIER"))
    }

    @Test
    fun `dine-in CANCELED is terminal`() {
        assertFalse(service.validateStatusTransition("CANCELED", "CREATED", "DINE_IN", "CASHIER"))
        assertFalse(service.validateStatusTransition("CANCELED", "IN_PREPARATION", "DINE_IN", "CASHIER"))
    }

    @Test
    fun `dine-in REFUNDED is terminal`() {
        assertFalse(service.validateStatusTransition("REFUNDED", "COMPLETED", "DINE_IN", "CASHIER"))
    }

    @Test
    fun `dine-in cannot skip to intermediate states`() {
        // Still illegal: jumping past prep/ready to a non-terminal intermediate
        // (SERVED) — that would mean "skip the kitchen and the wait". The
        // staff has to walk the order through prep at minimum.
        assertFalse(service.validateStatusTransition("CREATED", "SERVED", "DINE_IN", "CASHIER"))
        assertFalse(service.validateStatusTransition("CREATED", "READY", "DINE_IN", "CASHIER"))
        assertFalse(service.validateStatusTransition("IN_PREPARATION", "SERVED", "DINE_IN", "CASHIER"))
    }

    @Test
    fun `dine-in can jump directly to COMPLETED (Mark Completed shortcut)`() {
        // Product decision (May 2026): fast-counter merchants need a one-tap
        // "Mark Completed" CTA on the orders screen. Allowed from any
        // pre-terminal status. Paid-status gating happens at the UI layer.
        assertTrue(service.validateStatusTransition("CREATED", "COMPLETED", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("IN_PREPARATION", "COMPLETED", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("READY", "COMPLETED", "DINE_IN", "CASHIER"))
        assertTrue(service.validateStatusTransition("SERVED", "COMPLETED", "DINE_IN", "CASHIER"))
    }

    // ── DELIVERY channel ────────────────────────────────────────

    @Test
    fun `delivery happy path CREATED to COMPLETED`() {
        assertTrue(service.validateStatusTransition("CREATED", "IN_PREPARATION", "DELIVERY", "CASHIER"))
        assertTrue(service.validateStatusTransition("IN_PREPARATION", "READY", "DELIVERY", "CASHIER"))
        assertTrue(service.validateStatusTransition("READY", "ASSIGNED", "DELIVERY", "CASHIER"))
        assertTrue(service.validateStatusTransition("ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERY", "CASHIER"))
        assertTrue(service.validateStatusTransition("OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY", "CASHIER"))
        assertTrue(service.validateStatusTransition("DELIVERED", "COMPLETED", "DELIVERY", "CASHIER"))
    }

    @Test
    fun `delivery failed can be re-assigned or returned`() {
        assertTrue(service.validateStatusTransition("OUT_FOR_DELIVERY", "DELIVERY_FAILED", "DELIVERY", "CASHIER"))
        assertTrue(service.validateStatusTransition("DELIVERY_FAILED", "ASSIGNED", "DELIVERY", "CASHIER"))
        assertTrue(service.validateStatusTransition("DELIVERY_FAILED", "RETURNED", "DELIVERY", "CASHIER"))
    }

    @Test
    fun `delivery RETURNED is terminal`() {
        assertFalse(service.validateStatusTransition("RETURNED", "ASSIGNED", "DELIVERY", "CASHIER"))
        assertFalse(service.validateStatusTransition("RETURNED", "COMPLETED", "DELIVERY", "CASHIER"))
    }

    // ── TAKEAWAY channel ────────────────────────────────────────

    @Test
    fun `takeaway happy path CREATED to COMPLETED`() {
        assertTrue(service.validateStatusTransition("CREATED", "IN_PREPARATION", "TAKEAWAY", "CASHIER"))
        assertTrue(service.validateStatusTransition("IN_PREPARATION", "READY", "TAKEAWAY", "CASHIER"))
        assertTrue(service.validateStatusTransition("READY", "PICKED_UP", "TAKEAWAY", "CASHIER"))
        assertTrue(service.validateStatusTransition("PICKED_UP", "COMPLETED", "TAKEAWAY", "CASHIER"))
    }

    // ── IN_STORE channel ────────────────────────────────────────

    @Test
    fun `in-store direct CREATED to COMPLETED`() {
        assertTrue(service.validateStatusTransition("CREATED", "COMPLETED", "IN_STORE", "CASHIER"))
    }

    @Test
    fun `in-store cannot go through preparation`() {
        assertFalse(service.validateStatusTransition("CREATED", "IN_PREPARATION", "IN_STORE", "CASHIER"))
    }

    // ── PICKUP_LATER channel ────────────────────────────────────

    @Test
    fun `pickup-later happy path`() {
        assertTrue(service.validateStatusTransition("CREATED", "IN_PREPARATION", "PICKUP_LATER", "CASHIER"))
        assertTrue(service.validateStatusTransition("IN_PREPARATION", "READY", "PICKUP_LATER", "CASHIER"))
        assertTrue(service.validateStatusTransition("READY", "PICKED_UP", "PICKUP_LATER", "CASHIER"))
        assertTrue(service.validateStatusTransition("PICKED_UP", "COMPLETED", "PICKUP_LATER", "CASHIER"))
    }

    // ── MANAGER override ────────────────────────────────────────

    @Test
    fun `manager can override any transition`() {
        assertTrue(service.validateStatusTransition("CANCELED", "CREATED", "DINE_IN", "MANAGER"))
        assertTrue(service.validateStatusTransition("REFUNDED", "COMPLETED", "DELIVERY", "MANAGER"))
        assertTrue(service.validateStatusTransition("RETURNED", "ASSIGNED", "DELIVERY", "MANAGER"))
    }

    // ── Invalid channel ─────────────────────────────────────────

    @Test
    fun `invalid channel returns false`() {
        assertFalse(service.validateStatusTransition("CREATED", "IN_PREPARATION", "UNKNOWN", "CASHIER"))
    }
}
