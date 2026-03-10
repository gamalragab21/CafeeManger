package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.TaxPlacesTable
import net.marllex.waselak.backend.data.database.OrdersTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

@Serializable
data class TaxPlaceDto(
    val id: String,
    val vendor_id: String,
    val name: String,
    val tax_percent: Double,
    val is_default: Boolean,
    val display_order: Int,
    val created_at: Long,
    val updated_at: Long
)

@Serializable
data class CreateTaxPlaceDto(
    val name: String,
    val tax_percent: Double,
    val is_default: Boolean = false,
    val display_order: Int = 0
)

@Serializable
data class UpdateTaxPlaceDto(
    val name: String? = null,
    val tax_percent: Double? = null,
    val is_default: Boolean? = null,
    val display_order: Int? = null
)

private fun ResultRow.toTaxPlaceDto() = TaxPlaceDto(
    id = this[TaxPlacesTable.id].toString(),
    vendor_id = this[TaxPlacesTable.vendorId].toString(),
    name = this[TaxPlacesTable.name],
    tax_percent = this[TaxPlacesTable.taxPercent].toDouble(),
    is_default = this[TaxPlacesTable.isDefault],
    display_order = this[TaxPlacesTable.displayOrder],
    created_at = this[TaxPlacesTable.createdAt].toEpochMilliseconds(),
    updated_at = this[TaxPlacesTable.updatedAt].toEpochMilliseconds()
)

fun Route.taxPlacesRoutes() {
    route("/api/v1/tax-places") {
        get {
            val trace = call.routeTrace()
            trace.step("List tax places started")
            val principal = requireRole("MANAGER", "CASHIER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Querying tax places for vendor", mapOf("vendorId" to principal.vendorId))
            val list = transaction {
                TaxPlacesTable.selectAll()
                    .where { TaxPlacesTable.vendorId eq vendorUUID }
                    .orderBy(TaxPlacesTable.displayOrder)
                    .map { it.toTaxPlaceDto() }
            }
            trace.step("List tax places result", mapOf("count" to list.size.toString()))
            trace.step("List tax places completed")
            call.respond(HttpStatusCode.OK, list)
        }

        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get tax place started")
            val principal = requireRole("MANAGER", "CASHIER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Fetching tax place", mapOf("taxPlaceId" to id))
            val place = transaction {
                TaxPlacesTable.selectAll()
                    .where {
                        (TaxPlacesTable.id eq UUID.fromString(id)) and
                                (TaxPlacesTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toTaxPlaceDto()
            } ?: throw NoSuchElementException("Tax place not found")
            trace.step("Get tax place result", mapOf("taxPlaceId" to place.id, "name" to place.name, "taxPercent" to place.tax_percent.toString()))
            trace.step("Get tax place completed")
            call.respond(HttpStatusCode.OK, place)
        }

        post {
            val trace = call.routeTrace()
            trace.step("Create tax place started")
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateTaxPlaceDto>()
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Creating tax place", mapOf("name" to request.name, "taxPercent" to request.tax_percent.toString(), "vendorId" to principal.vendorId))
            val created = transaction {
                if (request.is_default) {
                    TaxPlacesTable.update(
                        { TaxPlacesTable.vendorId eq vendorUUID }
                    ) {
                        it[TaxPlacesTable.isDefault] = false
                    }
                }
                val id = TaxPlacesTable.insertAndGetId { stmt ->
                    stmt[vendorId] = vendorUUID
                    stmt[name] = request.name
                    stmt[taxPercent] = java.math.BigDecimal.valueOf(request.tax_percent)
                    stmt[isDefault] = request.is_default
                    stmt[displayOrder] = request.display_order
                    stmt[createdAt] = Clock.System.now()
                    stmt[updatedAt] = Clock.System.now()
                }
                TaxPlacesTable.selectAll().where { TaxPlacesTable.id eq id }.first().toTaxPlaceDto()
            }
            trace.step("Create tax place result", mapOf("taxPlaceId" to created.id, "name" to created.name, "taxPercent" to created.tax_percent.toString()))
            trace.step("Create tax place completed")
            call.respond(HttpStatusCode.Created, created)
        }

        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update tax place started")
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateTaxPlaceDto>()
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Updating tax place", mapOf("taxPlaceId" to id, "name" to (request.name ?: "null"), "taxPercent" to (request.tax_percent?.toString() ?: "null"), "vendorId" to principal.vendorId))
            val updated = transaction {
                if (request.is_default == true) {
                    TaxPlacesTable.update(
                        { TaxPlacesTable.vendorId eq vendorUUID }
                    ) { stmt ->
                        stmt[TaxPlacesTable.isDefault] = false
                    }
                }
                TaxPlacesTable.update(
                    { (TaxPlacesTable.id eq UUID.fromString(id)) and (TaxPlacesTable.vendorId eq vendorUUID) }
                ) { stmt ->
                    request.name?.let { newName -> stmt[name] = newName }
                    request.tax_percent?.let { percent -> stmt[taxPercent] = java.math.BigDecimal.valueOf(percent) }
                    request.is_default?.let { def -> stmt[TaxPlacesTable.isDefault] = def }
                    request.display_order?.let { order -> stmt[displayOrder] = order }
                    stmt[updatedAt] = Clock.System.now()
                }
                TaxPlacesTable.selectAll()
                    .where {
                        (TaxPlacesTable.id eq UUID.fromString(id)) and
                                (TaxPlacesTable.vendorId eq vendorUUID)
                    }.firstOrNull()?.toTaxPlaceDto()
            } ?: throw NoSuchElementException("Tax place not found")
            trace.step("Update tax place result", mapOf("taxPlaceId" to updated.id, "name" to updated.name, "taxPercent" to updated.tax_percent.toString()))
            trace.step("Update tax place completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete tax place started")
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Deleting tax place", mapOf("taxPlaceId" to id, "vendorId" to principal.vendorId))
            transaction {
                // Unlink orders referencing this tax place
                OrdersTable.update({
                    (OrdersTable.taxPlaceId eq UUID.fromString(id)) and (OrdersTable.vendorId eq vendorUUID)
                }) {
                    it[OrdersTable.taxPlaceId] = null
                    it[OrdersTable.tax] = java.math.BigDecimal.ZERO
                    it[OrdersTable.updatedAt] = Clock.System.now()
                }
                val deleted = TaxPlacesTable.deleteWhere {
                    (TaxPlacesTable.id eq UUID.fromString(id)) and
                            (TaxPlacesTable.vendorId eq vendorUUID)
                }
                if (deleted == 0) throw NoSuchElementException("Tax place not found")
            }
            trace.step("Tax place deleted successfully", mapOf("taxPlaceId" to id))
            trace.step("Delete tax place completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}
