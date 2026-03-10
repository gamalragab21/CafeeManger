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
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import net.marllex.waselak.backend.domain.service.PlanService
import org.koin.java.KoinJavaComponent
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
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/tables") {
        get {
            val trace = call.routeTrace()
            trace.step("List tables started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val status = call.parameters["status"]
            trace.step("Query params", mapOf("statusFilter" to (status ?: "null")))

            val tables = transaction {
                var query = TablesTable.selectAll()
                    .where { TablesTable.vendorId eq UUID.fromString(principal.vendorId) }

                status?.let { query = query.andWhere { TablesTable.status eq it } }

                query.orderBy(TablesTable.number)
                    .map { it.toTableDto() }
            }
            trace.step("Tables fetched", mapOf("count" to tables.size.toString()))
            trace.step("List tables completed")
            call.respond(HttpStatusCode.OK, tables)
        }

        get("/available") {
            val trace = call.routeTrace()
            trace.step("List available tables started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")

            val tables = transaction {
                TablesTable.selectAll()
                    .where {
                        (TablesTable.vendorId eq UUID.fromString(principal.vendorId)) and
                        (TablesTable.status eq "AVAILABLE")
                    }
                    .orderBy(TablesTable.number)
                    .map { it.toTableDto() }
            }
            trace.step("Available tables fetched", mapOf("count" to tables.size.toString()))
            trace.step("List available tables completed")
            call.respond(HttpStatusCode.OK, tables)
        }

        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get table started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Table ID parsed", mapOf("tableId" to id))

            val table = transaction {
                TablesTable.selectAll()
                    .where {
                        (TablesTable.id eq UUID.fromString(id)) and
                        (TablesTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toTableDto()
                    ?: throw NoSuchElementException("Table not found")
            }
            trace.step("Table found", mapOf("tableNumber" to table.number, "status" to table.status))
            trace.step("Get table completed")
            call.respond(HttpStatusCode.OK, table)
        }

        post {
            val trace = call.routeTrace()
            trace.step("Create table started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val request = call.receive<CreateTableDto>()
            trace.step("Request parsed", mapOf("number" to request.number, "capacity" to request.capacity.toString()))
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
            trace.step("Table created", mapOf("tableId" to table.id, "tableNumber" to table.number))
            trace.step("Create table completed")
            call.respond(HttpStatusCode.Created, table)
        }

        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update table started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Table ID parsed", mapOf("tableId" to id))
            val request = call.receive<UpdateTableDto>()
            trace.step("Request parsed", mapOf("number" to (request.number ?: "null"), "capacity" to (request.capacity?.toString() ?: "null")))

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
            trace.step("Table updated", mapOf("tableId" to updated.id, "tableNumber" to updated.number))
            trace.step("Update table completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        patch("/{id}/status") {
            val trace = call.routeTrace()
            trace.step("Update table status started")
            val principal = requireRole("MANAGER", "CASHIER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Table ID parsed", mapOf("tableId" to id))
            val request = call.receive<UpdateTableStatusDto>()
            trace.step("Request parsed", mapOf("newStatus" to request.status))

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
            trace.step("Table status updated", mapOf("tableId" to updated.id, "status" to updated.status))
            trace.step("Update table status completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete table started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "TABLE")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Table ID parsed", mapOf("tableId" to id))

            transaction {
                val deleted = TablesTable.deleteWhere {
                    (TablesTable.id eq UUID.fromString(id)) and
                    (vendorId eq UUID.fromString(principal.vendorId))
                }
                if (deleted == 0) throw NoSuchElementException("Table not found")
            }
            trace.step("Table deleted", mapOf("tableId" to id))
            trace.step("Delete table completed")
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
