package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    val id: String,
    val vendorId: String,
    val name: String? = null,
    val phone: String,
    val notes: String? = null,
    val orderCount: Int = 0,
    val totalSpent: Double = 0.0,
    val lastOrderAt: Long? = null,
    val addresses: List<CustomerAddress> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long? = null,
)

@Serializable
data class CustomerAddress(
    val id: String,
    val customerId: String,
    val label: String? = null,
    val address: String,
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val deliveryZoneId: String? = null,
    val deliveryFee: Double? = null,
    val isDefault: Boolean = false,
    val createdAt: Long,
)
