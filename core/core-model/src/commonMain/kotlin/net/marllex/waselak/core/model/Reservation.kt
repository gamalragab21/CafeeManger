package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Reservation(
    val id: String,
    val vendorId: String,
    val tableId: String,
    val tableNumber: String? = null,
    val clientName: String,
    val clientPhone: String? = null,
    val reservationDate: String, // YYYY-MM-DD
    val reservationTime: String, // HH:MM
    val numberOfGuests: Int = 1,
    val notes: String? = null,
    val status: ReservationStatus = ReservationStatus.PENDING,
    val orderId: String? = null,
    val createdBy: String,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)

@Serializable
enum class ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
