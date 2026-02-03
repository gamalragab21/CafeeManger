package net.marllex.cafeemanger.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Table(
    val id: String,
    val vendorId: String,
    val number: String,
    val capacity: Int = 4,
    val status: TableStatus = TableStatus.AVAILABLE,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

@Serializable
enum class TableStatus {
    AVAILABLE,
    OCCUPIED,
    RESERVED
}
