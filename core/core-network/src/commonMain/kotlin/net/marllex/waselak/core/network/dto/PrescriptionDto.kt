package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrescriptionResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("doctor_name") val doctorName: String? = null,
    @SerialName("doctor_phone") val doctorPhone: String? = null,
    @SerialName("patient_name") val patientName: String,
    @SerialName("patient_phone") val patientPhone: String? = null,
    @SerialName("patient_age") val patientAge: Int? = null,
    val diagnosis: String? = null,
    val notes: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val status: String = "PENDING",
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("dispensed_at") val dispensedAt: Long? = null,
    @SerialName("dispensed_by") val dispensedBy: String? = null,
    @SerialName("created_by") val createdBy: String,
    val items: List<PrescriptionItemResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class PrescriptionItemResponse(
    val id: String,
    @SerialName("prescription_id") val prescriptionId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String? = null,
    val quantity: Int,
    val dosage: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null,
    @SerialName("dispensed_quantity") val dispensedQuantity: Int = 0,
    val status: String = "PENDING",
    @SerialName("substitute_item_id") val substituteItemId: String? = null,
    @SerialName("substitute_item_name") val substituteItemName: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreatePrescriptionRequest(
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("doctor_name") val doctorName: String? = null,
    @SerialName("doctor_phone") val doctorPhone: String? = null,
    @SerialName("patient_name") val patientName: String,
    @SerialName("patient_phone") val patientPhone: String? = null,
    @SerialName("patient_age") val patientAge: Int? = null,
    val diagnosis: String? = null,
    val notes: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val items: List<CreatePrescriptionItemRequest>,
)

@Serializable
data class CreatePrescriptionItemRequest(
    @SerialName("item_id") val itemId: String,
    val quantity: Int,
    val dosage: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val instructions: String? = null,
)

@Serializable
data class DispensePrescriptionRequest(
    val items: List<DispenseItemRequest>? = null,
    val notes: String? = null,
    @SerialName("create_order") val createOrder: Boolean = true,
)

@Serializable
data class DispenseItemRequest(
    @SerialName("prescription_item_id") val prescriptionItemId: String,
    @SerialName("dispensed_quantity") val dispensedQuantity: Int,
    @SerialName("substitute_item_id") val substituteItemId: String? = null,
)
