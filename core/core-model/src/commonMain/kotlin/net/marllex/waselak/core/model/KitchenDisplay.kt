package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Kitchen Display System (KDS) domain models.
 * Used to display order items in the kitchen with their preparation status.
 */

@Serializable
data class KdsOrder(
    val orderId: String,
    val orderNumber: Int = 0,
    val channel: String,
    val tableNumber: String? = null,
    val clientName: String? = null,
    val notes: String? = null,
    val items: List<KdsOrderItem> = emptyList(),
    val createdAt: Long,
    val elapsedMinutes: Long = 0,
) {
    val hasPendingItems: Boolean get() = items.any { it.isPending }
    val hasCookingItems: Boolean get() = items.any { it.isCooking }
    val allReady: Boolean get() = items.all { it.isReady || it.isServed }
    val allServed: Boolean get() = items.all { it.isServed }
    val pendingCount: Int get() = items.count { it.isPending }
    val cookingCount: Int get() = items.count { it.isCooking }
    val readyCount: Int get() = items.count { it.isReady }
}

@Serializable
data class KdsOrderItem(
    val id: String,
    val orderId: String,
    val itemName: String,
    val quantity: Int,
    val note: String? = null,
    val variantOptions: String? = null,
    val kitchenStatus: String = "PENDING",  // PENDING, COOKING, READY, SERVED
    val kitchenStation: String? = null,
    val createdAt: Long,
) {
    val isPending: Boolean get() = kitchenStatus == "PENDING"
    val isCooking: Boolean get() = kitchenStatus == "COOKING"
    val isReady: Boolean get() = kitchenStatus == "READY"
    val isServed: Boolean get() = kitchenStatus == "SERVED"
}

@Serializable
data class KdsSummary(
    val totalItems: Int = 0,
    val pending: Int = 0,
    val cooking: Int = 0,
    val ready: Int = 0,
    val served: Int = 0,
    val avgPrepTimeMinutes: Double = 0.0,
)

enum class KitchenStatus {
    PENDING, COOKING, READY, SERVED;

    companion object {
        fun fromString(value: String): KitchenStatus =
            entries.firstOrNull { it.name == value.uppercase() } ?: PENDING
    }
}
