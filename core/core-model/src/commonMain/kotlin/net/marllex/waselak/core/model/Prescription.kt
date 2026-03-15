package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Pharmacy Prescription domain models.
 * Used for managing prescriptions in pharmacy-type vendors.
 */

@Serializable
data class Prescription(
    val id: String,
    val vendorId: String,
    val customerId: String? = null,
    val orderId: String? = null,
    val doctorName: String? = null,
    val doctorPhone: String? = null,
    val patientName: String,
    val patientPhone: String? = null,
    val patientAge: Int? = null,
    val diagnosis: String? = null,
    val notes: String? = null,
    val imageUrl: String? = null,
    val status: String = "PENDING",   // PENDING, DISPENSED, PARTIALLY_DISPENSED, CANCELLED, EXPIRED
    val expiresAt: Long? = null,
    val dispensedAt: Long? = null,
    val dispensedBy: String? = null,
    val createdBy: String,
    val items: List<PrescriptionItem> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isPending: Boolean get() = status == "PENDING"
    val isDispensed: Boolean get() = status == "DISPENSED"
    val isPartiallyDispensed: Boolean get() = status == "PARTIALLY_DISPENSED"
    val isCancelled: Boolean get() = status == "CANCELLED"
    val isExpired: Boolean get() = status == "EXPIRED"
    val canDispense: Boolean get() = status in listOf("PENDING", "PARTIALLY_DISPENSED")
    val itemCount: Int get() = items.size
    val dispensedItemCount: Int get() = items.count { it.isDispensed || it.isSubstituted }
}

@Serializable
data class PrescriptionItem(
    val id: String,
    val prescriptionId: String,
    val itemId: String,
    val itemName: String? = null,
    val quantity: Int,
    val dosage: String? = null,       // e.g., "500mg"
    val frequency: String? = null,    // e.g., "3 times daily"
    val duration: String? = null,     // e.g., "7 days"
    val instructions: String? = null,
    val dispensedQuantity: Int = 0,
    val status: String = "PENDING",   // PENDING, DISPENSED, UNAVAILABLE, SUBSTITUTED
    val substituteItemId: String? = null,
    val substituteItemName: String? = null,
    val createdAt: Long,
) {
    val isPending: Boolean get() = status == "PENDING"
    val isDispensed: Boolean get() = status == "DISPENSED"
    val isSubstituted: Boolean get() = status == "SUBSTITUTED"
    val isUnavailable: Boolean get() = status == "UNAVAILABLE"
    val isFullyDispensed: Boolean get() = dispensedQuantity >= quantity
}

enum class PrescriptionStatus {
    PENDING, DISPENSED, PARTIALLY_DISPENSED, CANCELLED, EXPIRED;

    companion object {
        fun fromString(value: String): PrescriptionStatus =
            entries.firstOrNull { it.name == value.uppercase() } ?: PENDING
    }
}
