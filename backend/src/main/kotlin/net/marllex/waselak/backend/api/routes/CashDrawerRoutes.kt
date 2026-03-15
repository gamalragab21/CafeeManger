package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.plugins.routeTrace
import org.koin.java.KoinJavaComponent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class CashDrawerSessionDto(
    val id: String,
    val vendor_id: String,
    val cashier_id: String,
    val cashier_name: String? = null,
    val opened_at: Long,
    val closed_at: Long? = null,
    val opening_balance: Double,
    val closing_balance: Double? = null,
    val expected_balance: Double? = null,
    val difference: Double? = null,
    val status: String,       // OPEN, CLOSED
    val notes: String? = null,
    val movements: List<CashMovementDto> = emptyList(),
    val created_at: Long,
)

@Serializable
data class CashMovementDto(
    val id: String,
    val session_id: String,
    val vendor_id: String,
    val type: String,         // CASH_IN, CASH_OUT, SALE, REFUND, ADJUSTMENT
    val amount: Double,
    val reason: String? = null,
    val order_id: String? = null,
    val created_by: String,
    val created_by_name: String? = null,
    val created_at: Long,
)

@Serializable
data class OpenDrawerDto(
    val opening_balance: Double = 0.0,
    val notes: String? = null,
)

@Serializable
data class CloseDrawerDto(
    val closing_balance: Double,
    val notes: String? = null,
)

@Serializable
data class CashMovementCreateDto(
    val type: String,         // CASH_IN, CASH_OUT, ADJUSTMENT
    val amount: Double,
    val reason: String? = null,
    val order_id: String? = null,
)

@Serializable
data class DrawerSummaryDto(
    val session_id: String,
    val opening_balance: Double,
    val total_cash_in: Double,
    val total_cash_out: Double,
    val total_sales: Double,
    val total_refunds: Double,
    val expected_balance: Double,
    val movement_count: Int,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.cashDrawerRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/cash-drawer") {

        // POST open a new cash drawer session
        post("/open") {
            val trace = call.routeTrace()
            trace.step("Open cash drawer started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "CASH_DRAWER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<OpenDrawerDto>()

            val session = transaction {
                val now = Clock.System.now()

                // Check if there's already an OPEN session for this cashier
                val existing = CashDrawerSessionsTable.selectAll().where {
                    (CashDrawerSessionsTable.vendorId eq vendorUUID) and
                    (CashDrawerSessionsTable.cashierId eq userUUID) and
                    (CashDrawerSessionsTable.status eq "OPEN")
                }.firstOrNull()

                if (existing != null) {
                    throw IllegalStateException("You already have an open cash drawer session. Close it before opening a new one.")
                }

                val sessionId = CashDrawerSessionsTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[cashierId] = userUUID
                    it[openedAt] = now
                    it[openingBalance] = BigDecimal(request.opening_balance)
                    it[status] = "OPEN"
                    it[notes] = request.notes
                    it[createdAt] = now
                }

                // Record opening balance as initial CASH_IN movement
                if (request.opening_balance > 0.0) {
                    CashMovementsTable.insert {
                        it[CashMovementsTable.sessionId] = sessionId
                        it[vendorId] = vendorUUID
                        it[type] = "CASH_IN"
                        it[amount] = BigDecimal(request.opening_balance)
                        it[reason] = "Opening balance"
                        it[createdBy] = userUUID
                        it[createdAt] = now
                    }
                }

                val cashierName = UsersTable.selectAll().where { UsersTable.id eq userUUID }
                    .firstOrNull()?.get(UsersTable.name)

                CashDrawerSessionDto(
                    id = sessionId.toString(),
                    vendor_id = vendorUUID.toString(),
                    cashier_id = principal.userId,
                    cashier_name = cashierName,
                    opened_at = now.toEpochMilliseconds(),
                    opening_balance = request.opening_balance,
                    status = "OPEN",
                    notes = request.notes,
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Cash drawer opened", mapOf("sessionId" to session.id))
            call.respond(HttpStatusCode.Created, session)
        }

        // POST close the current cash drawer session
        post("/close") {
            val trace = call.routeTrace()
            trace.step("Close cash drawer started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "CASH_DRAWER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<CloseDrawerDto>()

            val session = transaction {
                val now = Clock.System.now()

                // Find the open session for this cashier
                val sessionRow = CashDrawerSessionsTable.selectAll().where {
                    (CashDrawerSessionsTable.vendorId eq vendorUUID) and
                    (CashDrawerSessionsTable.cashierId eq userUUID) and
                    (CashDrawerSessionsTable.status eq "OPEN")
                }.firstOrNull() ?: throw NoSuchElementException("No open cash drawer session found")

                val sessionId = sessionRow[CashDrawerSessionsTable.id]
                val openingBalance = sessionRow[CashDrawerSessionsTable.openingBalance]

                // Calculate expected balance from movements
                val movements = CashMovementsTable.selectAll().where {
                    CashMovementsTable.sessionId eq sessionId
                }.toList()

                val totalCashIn = movements
                    .filter { it[CashMovementsTable.type] in listOf("CASH_IN", "SALE") }
                    .sumOf { it[CashMovementsTable.amount] }
                val totalCashOut = movements
                    .filter { it[CashMovementsTable.type] in listOf("CASH_OUT", "REFUND") }
                    .sumOf { it[CashMovementsTable.amount] }
                val adjustments = movements
                    .filter { it[CashMovementsTable.type] == "ADJUSTMENT" }
                    .sumOf { it[CashMovementsTable.amount] }

                // Expected = opening + cash_in + sales - cash_out - refunds + adjustments
                // Note: opening balance is already included as initial CASH_IN
                val expectedBalance = totalCashIn - totalCashOut + adjustments
                val closingBalanceBD = BigDecimal(request.closing_balance)
                val differenceBD = closingBalanceBD - expectedBalance

                // Update session
                CashDrawerSessionsTable.update({
                    CashDrawerSessionsTable.id eq sessionId
                }) {
                    it[closedAt] = now
                    it[closingBalance] = closingBalanceBD
                    it[CashDrawerSessionsTable.expectedBalance] = expectedBalance
                    it[difference] = differenceBD
                    it[status] = "CLOSED"
                    it[notes] = request.notes ?: sessionRow[CashDrawerSessionsTable.notes]
                }

                val cashierName = UsersTable.selectAll().where { UsersTable.id eq userUUID }
                    .firstOrNull()?.get(UsersTable.name)

                CashDrawerSessionDto(
                    id = sessionId.toString(),
                    vendor_id = vendorUUID.toString(),
                    cashier_id = principal.userId,
                    cashier_name = cashierName,
                    opened_at = sessionRow[CashDrawerSessionsTable.openedAt].toEpochMilliseconds(),
                    closed_at = now.toEpochMilliseconds(),
                    opening_balance = openingBalance.toDouble(),
                    closing_balance = request.closing_balance,
                    expected_balance = expectedBalance.toDouble(),
                    difference = differenceBD.toDouble(),
                    status = "CLOSED",
                    notes = request.notes ?: sessionRow[CashDrawerSessionsTable.notes],
                    created_at = sessionRow[CashDrawerSessionsTable.createdAt].toEpochMilliseconds(),
                )
            }
            trace.step("Cash drawer closed", mapOf("sessionId" to session.id, "difference" to (session.difference?.toString() ?: "0")))
            call.respond(HttpStatusCode.OK, session)
        }

        // GET current open session for this cashier
        get("/current") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "CASH_DRAWER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)

            val session = transaction {
                val sessionRow = CashDrawerSessionsTable.selectAll().where {
                    (CashDrawerSessionsTable.vendorId eq vendorUUID) and
                    (CashDrawerSessionsTable.cashierId eq userUUID) and
                    (CashDrawerSessionsTable.status eq "OPEN")
                }.firstOrNull()

                if (sessionRow == null) {
                    return@transaction null
                }

                val sessionId = sessionRow[CashDrawerSessionsTable.id]
                val movements = CashMovementsTable.selectAll().where {
                    CashMovementsTable.sessionId eq sessionId
                }.orderBy(CashMovementsTable.createdAt, SortOrder.DESC).map { it.toCashMovementDto() }

                val cashierName = UsersTable.selectAll().where { UsersTable.id eq userUUID }
                    .firstOrNull()?.get(UsersTable.name)

                CashDrawerSessionDto(
                    id = sessionId.toString(),
                    vendor_id = vendorUUID.toString(),
                    cashier_id = principal.userId,
                    cashier_name = cashierName,
                    opened_at = sessionRow[CashDrawerSessionsTable.openedAt].toEpochMilliseconds(),
                    opening_balance = sessionRow[CashDrawerSessionsTable.openingBalance].toDouble(),
                    status = "OPEN",
                    notes = sessionRow[CashDrawerSessionsTable.notes],
                    movements = movements,
                    created_at = sessionRow[CashDrawerSessionsTable.createdAt].toEpochMilliseconds(),
                )
            }

            if (session == null) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.OK, session)
            }
        }

        // POST add a cash movement (cash in/out)
        post("/movements") {
            val trace = call.routeTrace()
            trace.step("Add cash movement started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "CASH_DRAWER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val request = call.receive<CashMovementCreateDto>()

            require(request.type in listOf("CASH_IN", "CASH_OUT", "SALE", "REFUND", "ADJUSTMENT")) {
                "Invalid movement type: ${request.type}"
            }
            require(request.amount > 0) { "Amount must be positive" }

            val movement = transaction {
                val now = Clock.System.now()

                // Find open session for this cashier
                val sessionRow = CashDrawerSessionsTable.selectAll().where {
                    (CashDrawerSessionsTable.vendorId eq vendorUUID) and
                    (CashDrawerSessionsTable.cashierId eq userUUID) and
                    (CashDrawerSessionsTable.status eq "OPEN")
                }.firstOrNull() ?: throw NoSuchElementException("No open cash drawer session. Open a drawer first.")

                val sessionId = sessionRow[CashDrawerSessionsTable.id]

                val movementId = CashMovementsTable.insertAndGetId {
                    it[CashMovementsTable.sessionId] = sessionId
                    it[vendorId] = vendorUUID
                    it[type] = request.type
                    it[amount] = BigDecimal(request.amount)
                    it[reason] = request.reason
                    it[orderId] = request.order_id?.let { oid -> UUID.fromString(oid) }
                    it[createdBy] = userUUID
                    it[createdAt] = now
                }

                val createdByName = UsersTable.selectAll().where { UsersTable.id eq userUUID }
                    .firstOrNull()?.get(UsersTable.name)

                CashMovementDto(
                    id = movementId.toString(),
                    session_id = sessionId.toString(),
                    vendor_id = vendorUUID.toString(),
                    type = request.type,
                    amount = request.amount,
                    reason = request.reason,
                    order_id = request.order_id,
                    created_by = principal.userId,
                    created_by_name = createdByName,
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Cash movement added", mapOf("type" to request.type, "amount" to request.amount.toString()))
            call.respond(HttpStatusCode.Created, movement)
        }

        // GET movements for current open session
        get("/movements") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "CASH_DRAWER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)

            val movements = transaction {
                val sessionRow = CashDrawerSessionsTable.selectAll().where {
                    (CashDrawerSessionsTable.vendorId eq vendorUUID) and
                    (CashDrawerSessionsTable.cashierId eq userUUID) and
                    (CashDrawerSessionsTable.status eq "OPEN")
                }.firstOrNull() ?: throw NoSuchElementException("No open cash drawer session")

                CashMovementsTable.selectAll().where {
                    CashMovementsTable.sessionId eq sessionRow[CashDrawerSessionsTable.id]
                }.orderBy(CashMovementsTable.createdAt, SortOrder.DESC)
                    .map { it.toCashMovementDto() }
            }
            call.respond(HttpStatusCode.OK, movements)
        }

        // GET session history (closed sessions) — MANAGER only
        get("/sessions") {
            val trace = call.routeTrace()
            trace.step("Get session history")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "CASH_DRAWER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            val cashierId = call.parameters["cashier_id"]

            val sessions = transaction {
                var query = CashDrawerSessionsTable.selectAll().where {
                    CashDrawerSessionsTable.vendorId eq vendorUUID
                }
                cashierId?.let { cid ->
                    query = query.andWhere {
                        CashDrawerSessionsTable.cashierId eq UUID.fromString(cid)
                    }
                }

                query.orderBy(CashDrawerSessionsTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        val sessionId = row[CashDrawerSessionsTable.id]
                        val cashierUUID = row[CashDrawerSessionsTable.cashierId]
                        val cashierName = UsersTable.selectAll().where { UsersTable.id eq cashierUUID }
                            .firstOrNull()?.get(UsersTable.name)

                        CashDrawerSessionDto(
                            id = sessionId.toString(),
                            vendor_id = vendorUUID.toString(),
                            cashier_id = cashierUUID.toString(),
                            cashier_name = cashierName,
                            opened_at = row[CashDrawerSessionsTable.openedAt].toEpochMilliseconds(),
                            closed_at = row[CashDrawerSessionsTable.closedAt]?.toEpochMilliseconds(),
                            opening_balance = row[CashDrawerSessionsTable.openingBalance].toDouble(),
                            closing_balance = row[CashDrawerSessionsTable.closingBalance]?.toDouble(),
                            expected_balance = row[CashDrawerSessionsTable.expectedBalance]?.toDouble(),
                            difference = row[CashDrawerSessionsTable.difference]?.toDouble(),
                            status = row[CashDrawerSessionsTable.status],
                            notes = row[CashDrawerSessionsTable.notes],
                            created_at = row[CashDrawerSessionsTable.createdAt].toEpochMilliseconds(),
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, sessions)
        }

        // GET drawer summary for current open session
        get("/summary") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "CASH_DRAWER")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)

            val summary = transaction {
                val sessionRow = CashDrawerSessionsTable.selectAll().where {
                    (CashDrawerSessionsTable.vendorId eq vendorUUID) and
                    (CashDrawerSessionsTable.cashierId eq userUUID) and
                    (CashDrawerSessionsTable.status eq "OPEN")
                }.firstOrNull() ?: throw NoSuchElementException("No open cash drawer session")

                val sessionId = sessionRow[CashDrawerSessionsTable.id]
                val openingBalance = sessionRow[CashDrawerSessionsTable.openingBalance].toDouble()

                val movements = CashMovementsTable.selectAll().where {
                    CashMovementsTable.sessionId eq sessionId
                }.toList()

                val totalCashIn = movements
                    .filter { it[CashMovementsTable.type] == "CASH_IN" }
                    .sumOf { it[CashMovementsTable.amount].toDouble() }
                val totalCashOut = movements
                    .filter { it[CashMovementsTable.type] == "CASH_OUT" }
                    .sumOf { it[CashMovementsTable.amount].toDouble() }
                val totalSales = movements
                    .filter { it[CashMovementsTable.type] == "SALE" }
                    .sumOf { it[CashMovementsTable.amount].toDouble() }
                val totalRefunds = movements
                    .filter { it[CashMovementsTable.type] == "REFUND" }
                    .sumOf { it[CashMovementsTable.amount].toDouble() }

                val expectedBalance = totalCashIn + totalSales - totalCashOut - totalRefunds

                DrawerSummaryDto(
                    session_id = sessionId.toString(),
                    opening_balance = openingBalance,
                    total_cash_in = totalCashIn,
                    total_cash_out = totalCashOut,
                    total_sales = totalSales,
                    total_refunds = totalRefunds,
                    expected_balance = expectedBalance,
                    movement_count = movements.size,
                )
            }
            call.respond(HttpStatusCode.OK, summary)
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toCashMovementDto(): CashMovementDto {
    val createdByUUID = this[CashMovementsTable.createdBy]
    val createdByName = UsersTable.selectAll().where { UsersTable.id eq createdByUUID }
        .firstOrNull()?.get(UsersTable.name)

    return CashMovementDto(
        id = this[CashMovementsTable.id].toString(),
        session_id = this[CashMovementsTable.sessionId].toString(),
        vendor_id = this[CashMovementsTable.vendorId].toString(),
        type = this[CashMovementsTable.type],
        amount = this[CashMovementsTable.amount].toDouble(),
        reason = this[CashMovementsTable.reason],
        order_id = this[CashMovementsTable.orderId]?.toString(),
        created_by = createdByUUID.toString(),
        created_by_name = createdByName,
        created_at = this[CashMovementsTable.createdAt].toEpochMilliseconds(),
    )
}
