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
    // Orders summary
    val total_orders: Int = 0,
    // Payment method breakdown
    val cash_sales: Double = 0.0,
    val card_sales: Double = 0.0,
    val wallet_sales: Double = 0.0,
    val credit_sales: Double = 0.0,
    val cash_order_count: Int = 0,
    val card_order_count: Int = 0,
    val wallet_order_count: Int = 0,
    val credit_order_count: Int = 0,
    // Installment payments
    val installment_payments: Double = 0.0,
    val installment_payment_count: Int = 0,
    // Channel breakdown
    val channels: List<ChannelSummaryDto> = emptyList(),
)

@Serializable
data class ChannelSummaryDto(
    val channel: String,
    val order_count: Int,
    val total_amount: Double,
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

                // Calculate expected CASH balance from movements + actual cash orders
                val sessionOpenedAt = sessionRow[CashDrawerSessionsTable.openedAt]
                val movements = CashMovementsTable.selectAll().where {
                    CashMovementsTable.sessionId eq sessionId
                }.toList()

                val manualCashIn = movements
                    .filter { it[CashMovementsTable.type] == "CASH_IN" }
                    .sumOf { it[CashMovementsTable.amount] }
                val manualCashOut = movements
                    .filter { it[CashMovementsTable.type] == "CASH_OUT" }
                    .sumOf { it[CashMovementsTable.amount] }

                // Get actual CASH sales from completed orders for this cashier
                val paidOrders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                    (OrdersTable.cashierId eq userUUID) and
                    (OrdersTable.createdAt greaterEq sessionOpenedAt) and
                    (OrdersTable.status inList listOf("COMPLETED", "PAID"))
                }.toList()

                // Cash from non-split orders
                val cashFromOrders = paidOrders
                    .filter { it[OrdersTable.paymentMethod] == "CASH" }
                    .sumOf { it[OrdersTable.total] }

                // Cash from split orders
                val splitIds = paidOrders.filter { it[OrdersTable.paymentMethod] == "SPLIT" }.map { it[OrdersTable.id].value }
                val cashFromSplits = if (splitIds.isNotEmpty()) {
                    OrderPaymentsTable.selectAll().where {
                        (OrderPaymentsTable.orderId inList splitIds) and
                        (OrderPaymentsTable.paymentMethod eq "CASH")
                    }.sumOf { it[OrderPaymentsTable.amount] }
                } else BigDecimal.ZERO

                // Refunded orders
                val refundedTotal = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                    (OrdersTable.cashierId eq userUUID) and
                    (OrdersTable.createdAt greaterEq sessionOpenedAt) and
                    (OrdersTable.status eq "REFUNDED")
                }.sumOf { it[OrdersTable.total] }

                // Expected CASH in drawer = opening + manual deposits + cash sales - manual withdrawals - refunds
                val expectedBalance = openingBalance + (manualCashIn - openingBalance) + cashFromOrders + cashFromSplits - manualCashOut - refundedTotal
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
                val sessionOpenedAt = sessionRow[CashDrawerSessionsTable.openedAt]

                // Manual movements (CASH_IN / CASH_OUT / ADJUSTMENT) from the drawer
                val movements = CashMovementsTable.selectAll().where {
                    CashMovementsTable.sessionId eq sessionId
                }.toList()

                val manualCashIn = movements
                    .filter { it[CashMovementsTable.type] == "CASH_IN" }
                    .sumOf { it[CashMovementsTable.amount].toDouble() }
                val manualCashOut = movements
                    .filter { it[CashMovementsTable.type] == "CASH_OUT" }
                    .sumOf { it[CashMovementsTable.amount].toDouble() }

                // Get COMPLETED/PAID orders for THIS cashier during THIS session
                // Include orders created during session OR paid during session (for PAY_LATER orders)
                val paidOrders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                    (OrdersTable.cashierId eq userUUID) and
                    (OrdersTable.status inList listOf("COMPLETED", "PAID")) and
                    ((OrdersTable.createdAt greaterEq sessionOpenedAt) or
                     (OrdersTable.updatedAt greaterEq sessionOpenedAt) or
                     (OrdersTable.paymentConfirmedAt greaterEq sessionOpenedAt))
                }.toList()

                // Get REFUNDED orders for this cashier during this session
                val refundedOrders = OrdersTable.selectAll().where {
                    (OrdersTable.vendorId eq vendorUUID) and
                    (OrdersTable.cashierId eq userUUID) and
                    (OrdersTable.status eq "REFUNDED") and
                    ((OrdersTable.createdAt greaterEq sessionOpenedAt) or
                     (OrdersTable.updatedAt greaterEq sessionOpenedAt) or
                     (OrdersTable.paymentConfirmedAt greaterEq sessionOpenedAt))
                }.toList()

                // Calculate total sales from actual orders
                val totalSales = paidOrders.sumOf { it[OrdersTable.total].toDouble() }
                val totalRefunds = refundedOrders.sumOf { it[OrdersTable.total].toDouble() }

                // Payment method breakdown from orders
                val methodTotals = mutableMapOf<String, Pair<Int, Double>>()

                // Non-split paid orders
                paidOrders.filter { it[OrdersTable.paymentMethod] != "SPLIT" }.forEach { row ->
                    val m = row[OrdersTable.paymentMethod]
                    val (c, r) = methodTotals.getOrDefault(m, Pair(0, 0.0))
                    methodTotals[m] = Pair(c + 1, r + row[OrdersTable.total].toDouble())
                }

                // Split orders: distribute from OrderPaymentsTable
                val splitIds = paidOrders
                    .filter { it[OrdersTable.paymentMethod] == "SPLIT" }
                    .map { it[OrdersTable.id].value }
                if (splitIds.isNotEmpty()) {
                    OrderPaymentsTable.selectAll().where {
                        OrderPaymentsTable.orderId inList splitIds
                    }.forEach { payment ->
                        val m = payment[OrderPaymentsTable.paymentMethod]
                        val amt = payment[OrderPaymentsTable.amount].toDouble()
                        val (c, r) = methodTotals.getOrDefault(m, Pair(0, 0.0))
                        methodTotals[m] = Pair(c + 1, r + amt)
                    }
                }

                // Expected CASH in drawer:
                // = opening balance
                // + manual cash deposits (excluding opening balance which is already in manualCashIn)
                // + cash from sales
                // + installment payments
                // - manual cash withdrawals
                val cashSales = methodTotals["CASH"]?.second ?: 0.0
                val cardSales = methodTotals["CARD"]?.second ?: 0.0
                val walletSales = methodTotals["WALLET"]?.second ?: 0.0
                val creditSales = methodTotals["CREDIT"]?.second ?: 0.0

                // Installment payments collected during this session
                val installmentMovements = movements.filter { it[CashMovementsTable.type] == "INSTALLMENT_PAYMENT" }
                val installmentTotal = installmentMovements.sumOf { it[CashMovementsTable.amount].toDouble() }
                val installmentCount = installmentMovements.size

                // Expected = opening + all sales + installment payments + manual deposits - manual withdrawals - refunds
                val additionalCashIn = (manualCashIn - openingBalance).coerceAtLeast(0.0)
                val expectedBalance = openingBalance + additionalCashIn + totalSales + installmentTotal - manualCashOut - totalRefunds

                // Channel breakdown
                val channelBreakdown = paidOrders
                    .groupBy { it[OrdersTable.channel] }
                    .map { (channel, orders) ->
                        ChannelSummaryDto(
                            channel = channel,
                            order_count = orders.size,
                            total_amount = Math.round(orders.sumOf { it[OrdersTable.total].toDouble() } * 100.0) / 100.0,
                        )
                    }
                    .sortedByDescending { it.total_amount }

                fun Double.round2() = Math.round(this * 100.0) / 100.0

                DrawerSummaryDto(
                    session_id = sessionId.toString(),
                    opening_balance = openingBalance.round2(),
                    total_cash_in = manualCashIn.round2(),
                    total_cash_out = manualCashOut.round2(),
                    total_sales = totalSales.round2(),
                    total_refunds = totalRefunds.round2(),
                    expected_balance = expectedBalance.round2(),
                    movement_count = movements.size + paidOrders.size,
                    total_orders = paidOrders.size,
                    cash_sales = cashSales.round2(),
                    card_sales = cardSales.round2(),
                    wallet_sales = walletSales.round2(),
                    credit_sales = creditSales.round2(),
                    cash_order_count = methodTotals["CASH"]?.first ?: 0,
                    card_order_count = methodTotals["CARD"]?.first ?: 0,
                    wallet_order_count = methodTotals["WALLET"]?.first ?: 0,
                    credit_order_count = methodTotals["CREDIT"]?.first ?: 0,
                    installment_payments = installmentTotal.round2(),
                    installment_payment_count = installmentCount,
                    channels = channelBreakdown,
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
