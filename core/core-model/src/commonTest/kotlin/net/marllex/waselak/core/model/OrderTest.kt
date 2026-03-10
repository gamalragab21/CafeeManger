package net.marllex.waselak.core.model

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderStatusTest {

    // ── DINE_IN transitions ────────────────────────────────────

    @Test
    fun dineInHappyPath() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.IN_PREPARATION, OrderChannel.DINE_IN))
        assertTrue(OrderStatus.IN_PREPARATION.canTransitionTo(OrderStatus.READY, OrderChannel.DINE_IN))
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.SERVED, OrderChannel.DINE_IN))
        assertTrue(OrderStatus.SERVED.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.DINE_IN))
    }

    @Test
    fun dineInCanRefundCompleted() {
        assertTrue(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.REFUNDED, OrderChannel.DINE_IN))
    }

    @Test
    fun dineInCanCancelFromActiveStates() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELED, OrderChannel.DINE_IN))
        assertTrue(OrderStatus.IN_PREPARATION.canTransitionTo(OrderStatus.CANCELED, OrderChannel.DINE_IN))
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.CANCELED, OrderChannel.DINE_IN))
        assertTrue(OrderStatus.SERVED.canTransitionTo(OrderStatus.CANCELED, OrderChannel.DINE_IN))
    }

    @Test
    fun dineInTerminalStates() {
        assertFalse(OrderStatus.CANCELED.canTransitionTo(OrderStatus.CREATED, OrderChannel.DINE_IN))
        assertFalse(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.DINE_IN))
    }

    @Test
    fun dineInCannotSkipStates() {
        assertFalse(OrderStatus.CREATED.canTransitionTo(OrderStatus.SERVED, OrderChannel.DINE_IN))
        assertFalse(OrderStatus.CREATED.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.DINE_IN))
    }

    // ── DELIVERY transitions ───────────────────────────────────

    @Test
    fun deliveryHappyPath() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.IN_PREPARATION, OrderChannel.DELIVERY))
        assertTrue(OrderStatus.IN_PREPARATION.canTransitionTo(OrderStatus.READY, OrderChannel.DELIVERY))
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.ASSIGNED, OrderChannel.DELIVERY))
        assertTrue(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY, OrderChannel.DELIVERY))
        assertTrue(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED, OrderChannel.DELIVERY))
        assertTrue(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.DELIVERY))
    }

    @Test
    fun deliveryFailedCanBeReassignedOrReturned() {
        assertTrue(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERY_FAILED, OrderChannel.DELIVERY))
        assertTrue(OrderStatus.DELIVERY_FAILED.canTransitionTo(OrderStatus.ASSIGNED, OrderChannel.DELIVERY))
        assertTrue(OrderStatus.DELIVERY_FAILED.canTransitionTo(OrderStatus.RETURNED, OrderChannel.DELIVERY))
    }

    @Test
    fun deliveryReturnedIsTerminal() {
        assertFalse(OrderStatus.RETURNED.canTransitionTo(OrderStatus.ASSIGNED, OrderChannel.DELIVERY))
        assertFalse(OrderStatus.RETURNED.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.DELIVERY))
    }

    // ── TAKEAWAY transitions ───────────────────────────────────

    @Test
    fun takeawayHappyPath() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.IN_PREPARATION, OrderChannel.TAKEAWAY))
        assertTrue(OrderStatus.IN_PREPARATION.canTransitionTo(OrderStatus.READY, OrderChannel.TAKEAWAY))
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.PICKED_UP, OrderChannel.TAKEAWAY))
        assertTrue(OrderStatus.PICKED_UP.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.TAKEAWAY))
    }

    // ── IN_STORE transitions ───────────────────────────────────

    @Test
    fun inStoreDirectComplete() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.IN_STORE))
    }

    @Test
    fun inStoreCannotGoThroughPreparation() {
        assertFalse(OrderStatus.CREATED.canTransitionTo(OrderStatus.IN_PREPARATION, OrderChannel.IN_STORE))
    }

    // ── PICKUP_LATER transitions ───────────────────────────────

    @Test
    fun pickupLaterHappyPath() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.IN_PREPARATION, OrderChannel.PICKUP_LATER))
        assertTrue(OrderStatus.IN_PREPARATION.canTransitionTo(OrderStatus.READY, OrderChannel.PICKUP_LATER))
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.PICKED_UP, OrderChannel.PICKUP_LATER))
        assertTrue(OrderStatus.PICKED_UP.canTransitionTo(OrderStatus.COMPLETED, OrderChannel.PICKUP_LATER))
    }

    // ── getAvailableStatuses ───────────────────────────────────

    @Test
    fun dineInAvailableStatuses() {
        val statuses = OrderStatus.getAvailableStatuses(OrderChannel.DINE_IN)
        assertTrue(OrderStatus.SERVED in statuses)
        assertFalse(OrderStatus.ASSIGNED in statuses)
        assertFalse(OrderStatus.PICKED_UP in statuses)
    }

    @Test
    fun deliveryAvailableStatuses() {
        val statuses = OrderStatus.getAvailableStatuses(OrderChannel.DELIVERY)
        assertTrue(OrderStatus.ASSIGNED in statuses)
        assertTrue(OrderStatus.OUT_FOR_DELIVERY in statuses)
        assertTrue(OrderStatus.DELIVERED in statuses)
        assertTrue(OrderStatus.DELIVERY_FAILED in statuses)
        assertTrue(OrderStatus.RETURNED in statuses)
        assertFalse(OrderStatus.SERVED in statuses)
    }

    @Test
    fun inStoreAvailableStatusesAreMinimal() {
        val statuses = OrderStatus.getAvailableStatuses(OrderChannel.IN_STORE)
        assertEquals(4, statuses.size)
        assertTrue(OrderStatus.CREATED in statuses)
        assertTrue(OrderStatus.COMPLETED in statuses)
        assertTrue(OrderStatus.CANCELED in statuses)
        assertTrue(OrderStatus.REFUNDED in statuses)
    }

    // ── parse (legacy support) ─────────────────────────────────

    @Test
    fun parseLegacyOnTable() {
        assertEquals(OrderStatus.SERVED, OrderStatus.parse("ON_TABLE"))
    }

    @Test
    fun parseStandardValues() {
        assertEquals(OrderStatus.CREATED, OrderStatus.parse("CREATED"))
        assertEquals(OrderStatus.COMPLETED, OrderStatus.parse("COMPLETED"))
        assertEquals(OrderStatus.CANCELED, OrderStatus.parse("CANCELED"))
    }

    @Test
    fun parseInvalidThrows() {
        assertFailsWith<IllegalArgumentException> {
            OrderStatus.parse("NONEXISTENT")
        }
    }
}

class OrderChannelTest {

    @Test
    fun allChannelsExist() {
        assertEquals(5, OrderChannel.entries.size)
    }

    @Test
    fun channelValues() {
        assertEquals("DINE_IN", OrderChannel.DINE_IN.name)
        assertEquals("DELIVERY", OrderChannel.DELIVERY.name)
        assertEquals("TAKEAWAY", OrderChannel.TAKEAWAY.name)
        assertEquals("IN_STORE", OrderChannel.IN_STORE.name)
        assertEquals("PICKUP_LATER", OrderChannel.PICKUP_LATER.name)
    }
}

class PaymentEnumsTest {

    @Test
    fun paymentMethodValues() {
        assertEquals(3, PaymentMethod.entries.size)
        assertEquals("CASH", PaymentMethod.CASH.name)
        assertEquals("WALLET", PaymentMethod.WALLET.name)
        assertEquals("CARD", PaymentMethod.CARD.name)
    }

    @Test
    fun paymentStatusValues() {
        assertEquals(5, PaymentStatus.entries.size)
    }

    @Test
    fun paymentTimingValues() {
        assertEquals(2, PaymentTiming.entries.size)
        assertEquals("PAY_NOW", PaymentTiming.PAY_NOW.name)
        assertEquals("PAY_LATER", PaymentTiming.PAY_LATER.name)
    }
}
