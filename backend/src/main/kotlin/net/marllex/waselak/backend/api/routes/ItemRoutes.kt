package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.ItemsTable
import net.marllex.waselak.backend.data.database.CategoriesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class ItemDto(
    val id: String,
    val vendor_id: String,
    val category_id: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    val image_url: String? = null,
    val available: Boolean = true,
    val created_at: Long? = null,
    val updated_at: Long? = null
)

@Serializable
data class CreateItemDto(
    val category_id: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    val image_url: String? = null,
    val available: Boolean = true
)

@Serializable
data class UpdateItemDto(
    val category_id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val image_url: String? = null,
    val available: Boolean? = null
)

fun Route.itemRoutes() {
    route("/api/v1/items") {
        get {
            val principal = currentUser()
            val categoryId = call.parameters["category_id"]

            val items = transaction {
                var query = ItemsTable.selectAll()
                    .where { ItemsTable.vendorId eq UUID.fromString(principal.vendorId) }

                categoryId?.let {
                    query = query.andWhere { ItemsTable.categoryId eq UUID.fromString(it) }
                }

                query.orderBy(ItemsTable.name)
                    .map { it.toItemDto() }
            }
            call.respond(HttpStatusCode.OK, items)
        }

        get("/available") {
            val principal = currentUser()
            val categoryId = call.parameters["category_id"]

            val items = transaction {
                var query = ItemsTable.selectAll()
                    .where {
                        (ItemsTable.vendorId eq UUID.fromString(principal.vendorId)) and
                        (ItemsTable.available eq true)
                    }

                categoryId?.let {
                    query = query.andWhere { ItemsTable.categoryId eq UUID.fromString(it) }
                }

                query.orderBy(ItemsTable.name)
                    .map { it.toItemDto() }
            }
            call.respond(HttpStatusCode.OK, items)
        }

        get("/{id}") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val item = transaction {
                ItemsTable.selectAll()
                    .where {
                        (ItemsTable.id eq UUID.fromString(id)) and
                        (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toItemDto()
                    ?: throw NoSuchElementException("Item not found")
            }
            call.respond(HttpStatusCode.OK, item)
        }

        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateItemDto>()
            require(request.name.isNotBlank()) { "Item name is required" }
            require(request.price > 0) { "Price must be positive" }

            val item = transaction {
                // Verify category belongs to this vendor
                val categoryExists = CategoriesTable.selectAll()
                    .where {
                        (CategoriesTable.id eq UUID.fromString(request.category_id)) and
                        (CategoriesTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.count() > 0

                if (!categoryExists) throw NoSuchElementException("Category not found")

                val id = ItemsTable.insertAndGetId {
                    it[vendorId] = UUID.fromString(principal.vendorId)
                    it[categoryId] = UUID.fromString(request.category_id)
                    it[name] = request.name
                    it[description] = request.description
                    it[price] = BigDecimal.valueOf(request.price)
                    it[imageUrl] = request.image_url
                    it[available] = request.available
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
                ItemsTable.selectAll().where { ItemsTable.id eq id }.first().toItemDto()
            }
            call.respond(HttpStatusCode.Created, item)
        }

        put("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateItemDto>()

            val updated = transaction {
                // If changing category, verify it belongs to this vendor
                request.category_id?.let { catId ->
                    val categoryExists = CategoriesTable.selectAll()
                        .where {
                            (CategoriesTable.id eq UUID.fromString(catId)) and
                            (CategoriesTable.vendorId eq UUID.fromString(principal.vendorId))
                        }.count() > 0
                    if (!categoryExists) throw NoSuchElementException("Category not found")
                }

                ItemsTable.update({
                    (ItemsTable.id eq UUID.fromString(id)) and
                    (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                }) { stmt ->
                    request.category_id?.let { stmt[categoryId] = UUID.fromString(it) }
                    request.name?.let { stmt[name] = it }
                    request.description?.let { stmt[description] = it }
                    request.price?.let { stmt[price] = BigDecimal.valueOf(it) }
                    request.image_url?.let { stmt[imageUrl] = it }
                    request.available?.let { stmt[available] = it }
                    stmt[updatedAt] = Clock.System.now()
                }

                ItemsTable.selectAll().where { ItemsTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toItemDto() ?: throw NoSuchElementException("Item not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        patch("/{id}/availability") {
            val principal = requireRole("MANAGER", "CASHIER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            @Serializable
            data class AvailabilityDto(val available: Boolean)
            val request = call.receive<AvailabilityDto>()

            val updated = transaction {
                ItemsTable.update({
                    (ItemsTable.id eq UUID.fromString(id)) and
                    (ItemsTable.vendorId eq UUID.fromString(principal.vendorId))
                }) {
                    it[available] = request.available
                    it[updatedAt] = Clock.System.now()
                }
                ItemsTable.selectAll().where { ItemsTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toItemDto() ?: throw NoSuchElementException("Item not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val deleted = ItemsTable.deleteWhere {
                    (ItemsTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("Item not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

private fun ResultRow.toItemDto() = ItemDto(
    id = this[ItemsTable.id].toString(),
    vendor_id = this[ItemsTable.vendorId].toString(),
    category_id = this[ItemsTable.categoryId].toString(),
    name = this[ItemsTable.name],
    description = this[ItemsTable.description],
    price = this[ItemsTable.price].toDouble(),
    image_url = this[ItemsTable.imageUrl],
    available = this[ItemsTable.available],
    created_at = this[ItemsTable.createdAt].toEpochMilliseconds(),
    updated_at = this[ItemsTable.updatedAt].toEpochMilliseconds()
)
