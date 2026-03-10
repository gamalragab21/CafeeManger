package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReservationResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("table_id") val tableId: String,
    @SerialName("table_number") val tableNumber: String? = null,
    @SerialName("client_name") val clientName: String,
    @SerialName("client_phone") val clientPhone: String? = null,
    @SerialName("reservation_date") val reservationDate: String,
    @SerialName("reservation_time") val reservationTime: String,
    @SerialName("number_of_guests") val numberOfGuests: Int = 1,
    val notes: String? = null,
    val status: String = "PENDING",
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class CreateReservationRequest(
    @SerialName("table_id") val tableId: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("client_phone") val clientPhone: String? = null,
    @SerialName("reservation_date") val reservationDate: String,
    @SerialName("reservation_time") val reservationTime: String,
    @SerialName("number_of_guests") val numberOfGuests: Int = 1,
    val notes: String? = null,
    @SerialName("order_id") val orderId: String? = null,
)

@Serializable
data class UpdateReservationRequest(
    @SerialName("table_id") val tableId: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_phone") val clientPhone: String? = null,
    @SerialName("reservation_date") val reservationDate: String? = null,
    @SerialName("reservation_time") val reservationTime: String? = null,
    @SerialName("number_of_guests") val numberOfGuests: Int? = null,
    val notes: String? = null,
    @SerialName("order_id") val orderId: String? = null,
)

@Serializable
data class UpdateReservationStatusRequest(val status: String)
