package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.TablesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class TableDto(
    val id: String,
    val vendor_id: String,
    val number: String,
    val capacity: Int = 4,
    val status: String = "AVAILABLE",
    val created_at: Long? = null,
    val updated_at: Long? = null
)

@Serializable
data class CreateTableDto(
    val number: String,
    val capacity: Int = 4
)

@Serializable
data class UpdateTableDto(
    val number: String? = null,
    val capacity: Int? = null
)

@Serializable
data class UpdateTableStatusDto(val status: String)

fun Route.tableRoutes() {
    route("/api/v1/tables") {
        get {
            val principal = currentUser()
            val status = call.parameters["status"]

            val tables = transaction {
                var query = TablesTable.selectAll()
                    .where { TablesTable.vendorId eq UUID.fromString(principal.vendorId) }

                status?.let { query = query.andWhere { TablesTable.status eq it } }

                query.orderBy(TablesTable.number)
                    .map { it.toTableDto() }
            }
            call.respond(HttpStatusCode.OK, tables)
        }

        get("/available") {
            val principal = currentUser()

            val tables = transaction {
                TablesTable.selectAll()
                    .where {
                        (TablesTable.vendorId eq UUID.fromString(principal.vendorId)) and
                        (TablesTable.status eq "AVAILABLE")
                    }
                    .orderBy(TablesTable.number)
                    .map { it.toTableDto() }
            }
            call.respond(HttpStatusCode.OK, tables)
        }

        get("/{id}") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val table = transaction {
                TablesTable.selectAll()
                    .where {
                        (TablesTable.id eq UUID.fromString(id)) and
                        (TablesTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toTableDto()
                    ?: throw NoSuchElementException("Table not found")
            }
            call.respond(HttpStatusCode.OK, table)
        }

        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateTableDto>()
            require(request.number.isNotBlank()) { "Table number is required" }

            val table = transaction {
                val id = TablesTable.insertAndGetId {
                    it[vendorId] = UUID.fromString(principal.vendorId)
                    it[number] = request.number
                    it[capacity] = request.capacity
                    it[status] = "AVAILABLE"
                    it[createdAt] = Clock.System.now()
                    it[updatedAt] = Clock.System.now()
                }
                TablesTable.selectAll().where { TablesTable.id eq id }.first().toTableDto()
            }
            call.respond(HttpStatusCode.Created, table)
        }

        put("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateTableDto>()

            val updated = transaction {
                TablesTable.update({
                    (TablesTable.id eq UUID.fromString(id)) and
                    (TablesTable.vendorId eq UUID.fromString(principal.vendorId))
                }) { stmt ->
                    request.number?.let { stmt[number] = it }
                    request.capacity?.let { stmt[capacity] = it }
                    stmt[updatedAt] = Clock.System.now()
                }
                TablesTable.selectAll().where { TablesTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toTableDto() ?: throw NoSuchElementException("Table not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        patch("/{id}/status") {
            val principal = requireRole("MANAGER", "CASHIER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateTableStatusDto>()

            val validStatuses = listOf("AVAILABLE", "OCCUPIED", "RESERVED")
            require(request.status in validStatuses) {
                "Invalid status. Must be one of: ${validStatuses.joinToString()}"
            }

            val updated = transaction {
                TablesTable.update({
                    (TablesTable.id eq UUID.fromString(id)) and
                    (TablesTable.vendorId eq UUID.fromString(principal.vendorId))
                }) {
                    it[status] = request.status
                    it[updatedAt] = Clock.System.now()
                }
                TablesTable.selectAll().where { TablesTable.id eq UUID.fromString(id) }
                    .firstOrNull()?.toTableDto() ?: throw NoSuchElementException("Table not found")
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val deleted = TablesTable.deleteWhere {
                    (TablesTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("Table not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }
}

private fun ResultRow.toTableDto() = TableDto(
    id = this[TablesTable.id].toString(),
    vendor_id = this[TablesTable.vendorId].toString(),
    number = this[TablesTable.number],
    capacity = this[TablesTable.capacity],
    status = this[TablesTable.status],
    created_at = this[TablesTable.createdAt].toEpochMilliseconds(),
    updated_at = this[TablesTable.updatedAt].toEpochMilliseconds()
)
