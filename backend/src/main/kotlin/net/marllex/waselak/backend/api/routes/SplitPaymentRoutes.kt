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
data class OrderPaymentDto(
    val id: String,
    val order_id: String,
    val vendor_id: String,
    val payment_method: String,   // CASH, CARD, WALLET, BANK_TRANSFER
    val amount: Double,
    val paid_by: String? = null,
    val paid_by_name: String? = null,
    val note: String? = null,
    val created_at: Long,
)

@Serializable
data class CreateOrderPaymentDto(
    val payment_method: String,
    val amount: Double,
    val note: String? = null,
)

@Serializable
data class AddPaymentResponseDto(
    val payment: OrderPaymentDto,
    val is_fully_paid: Boolean,
    val remaining: Double,
)

@Serializable
data class SplitPaymentSummaryDto(
    val order_id: String,
    val order_total: Double,
    val total_paid: Double,
    val remaining: Double,
    val is_fully_paid: Boolean,
    val payments: List<OrderPaymentDto>,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.splitPaymentRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/orders/{orderId}/payments") {

        // GET all payments for an order
        get {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "SPLIT_PAYMENT")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val orderId = call.parameters["orderId"]
                ?: throw IllegalArgumentException("Order ID required")

            val summary = transaction {
                val orderUUID = UUID.fromString(orderId)

                // Verify order belongs to vendor
                val order = OrdersTable.selectAll().where {
                    (OrdersTable.id eq orderUUID) and (OrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val orderTotal = order[OrdersTable.total].toDouble()

                val payments = OrderPaymentsTable.selectAll().where {
                    (OrderPaymentsTable.orderId eq orderUUID) and
                    (OrderPaymentsTable.vendorId eq vendorUUID)
                }.orderBy(OrderPaymentsTable.createdAt, SortOrder.ASC)
                    .map { it.toOrderPaymentDto() }

                val totalPaid = payments.sumOf { it.amount }
                val remaining = orderTotal - totalPaid

                SplitPaymentSummaryDto(
                    order_id = orderId,
                    order_total = orderTotal,
                    total_paid = totalPaid,
                    remaining = if (remaining < 0.01) 0.0 else remaining,
                    is_fully_paid = remaining < 0.01,
                    payments = payments,
                )
            }
            call.respond(HttpStatusCode.OK, summary)
        }

        // POST add a payment leg to an order
        post {
            val trace = call.routeTrace()
            trace.step("Add split payment started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SPLIT_PAYMENT")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val orderId = call.parameters["orderId"]
                ?: throw IllegalArgumentException("Order ID required")
            val request = call.receive<CreateOrderPaymentDto>()

            require(request.payment_method in listOf("CASH", "CARD", "WALLET")) {
                "Invalid payment method: ${request.payment_method}"
            }
            require(request.amount > 0) { "Payment amount must be positive" }

            val result = transaction {
                val orderUUID = UUID.fromString(orderId)
                val now = Clock.System.now()

                // Verify order belongs to vendor
                val order = OrdersTable.selectAll().where {
                    (OrdersTable.id eq orderUUID) and (OrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                val orderTotal = order[OrdersTable.total]

                // Calculate how much has already been paid
                val existingPayments = OrderPaymentsTable.selectAll().where {
                    OrderPaymentsTable.orderId eq orderUUID
                }.toList()
                val totalPaid = existingPayments.sumOf { it[OrderPaymentsTable.amount] }
                val remaining = orderTotal - totalPaid

                if (remaining <= BigDecimal.ZERO) {
                    throw IllegalStateException("Order is already fully paid")
                }

                // Cap payment at remaining amount
                val paymentAmount = minOf(BigDecimal(request.amount), remaining)

                val paymentId = OrderPaymentsTable.insertAndGetId {
                    it[OrderPaymentsTable.orderId] = orderUUID
                    it[vendorId] = vendorUUID
                    it[paymentMethod] = request.payment_method
                    it[amount] = paymentAmount
                    it[paidBy] = userUUID
                    it[note] = request.note
                    it[createdAt] = now
                }

                // Check if order is now fully paid
                val newTotalPaid = totalPaid + paymentAmount
                val isFullyPaid = (orderTotal - newTotalPaid) < BigDecimal("0.01")

                if (isFullyPaid) {
                    // Update order payment status
                    OrdersTable.update({ OrdersTable.id eq orderUUID }) {
                        it[paymentStatus] = "PAID"
                        it[paymentConfirmedAt] = now
                        it[paymentConfirmedBy] = userUUID
                        // Use SPLIT as the payment method when multiple payments exist
                        if (existingPayments.isNotEmpty()) {
                            it[paymentMethod] = "SPLIT"
                        } else {
                            it[paymentMethod] = request.payment_method
                        }
                        it[updatedAt] = now
                    }
                }

                // Also record as cash drawer movement if type is CASH and there's an open drawer
                if (request.payment_method == "CASH") {
                    val openSession = CashDrawerSessionsTable.selectAll().where {
                        (CashDrawerSessionsTable.vendorId eq vendorUUID) and
                        (CashDrawerSessionsTable.cashierId eq userUUID) and
                        (CashDrawerSessionsTable.status eq "OPEN")
                    }.firstOrNull()

                    if (openSession != null) {
                        CashMovementsTable.insert {
                            it[sessionId] = openSession[CashDrawerSessionsTable.id]
                            it[vendorId] = vendorUUID
                            it[type] = "SALE"
                            it[CashMovementsTable.amount] = paymentAmount
                            it[reason] = "Split payment (cash portion)"
                            it[CashMovementsTable.orderId] = orderUUID
                            it[createdBy] = userUUID
                            it[createdAt] = now
                        }
                    }
                }

                val paidByName = UsersTable.selectAll().where { UsersTable.id eq userUUID }
                    .firstOrNull()?.get(UsersTable.name)

                val payment = OrderPaymentDto(
                    id = paymentId.toString(),
                    order_id = orderId,
                    vendor_id = vendorUUID.toString(),
                    payment_method = request.payment_method,
                    amount = paymentAmount.toDouble(),
                    paid_by = principal.userId,
                    paid_by_name = paidByName,
                    note = request.note,
                    created_at = now.toEpochMilliseconds(),
                )

                val newRemaining = (orderTotal - newTotalPaid).toDouble()

                AddPaymentResponseDto(
                    payment = payment,
                    is_fully_paid = isFullyPaid,
                    remaining = if (newRemaining < 0.01) 0.0 else newRemaining,
                )
            }
            trace.step("Split payment added", mapOf("orderId" to orderId, "method" to request.payment_method))
            call.respond(HttpStatusCode.Created, result)
        }

        // DELETE remove a payment (before order completion only) — MANAGER only
        delete("/{paymentId}") {
            val trace = call.routeTrace()
            trace.step("Remove split payment started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "SPLIT_PAYMENT")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val orderId = call.parameters["orderId"]
                ?: throw IllegalArgumentException("Order ID required")
            val paymentId = call.parameters["paymentId"]
                ?: throw IllegalArgumentException("Payment ID required")

            transaction {
                val orderUUID = UUID.fromString(orderId)
                val paymentUUID = UUID.fromString(paymentId)

                // Verify order belongs to vendor and is not COMPLETED
                val order = OrdersTable.selectAll().where {
                    (OrdersTable.id eq orderUUID) and (OrdersTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Order not found")

                require(order[OrdersTable.status] != "COMPLETED") {
                    "Cannot remove payment from a completed order"
                }

                // Verify payment exists and belongs to this order
                val payment = OrderPaymentsTable.selectAll().where {
                    (OrderPaymentsTable.id eq paymentUUID) and
                    (OrderPaymentsTable.orderId eq orderUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Payment not found")

                // Delete the payment
                OrderPaymentsTable.deleteWhere {
                    (OrderPaymentsTable.id eq paymentUUID)
                }

                // Reset order payment status since we removed a payment
                OrdersTable.update({ OrdersTable.id eq orderUUID }) {
                    it[paymentStatus] = "PENDING"
                    it[paymentConfirmedAt] = null
                    it[paymentConfirmedBy] = null
                    it[updatedAt] = Clock.System.now()
                }
            }
            trace.step("Payment removed", mapOf("paymentId" to paymentId))
            call.respond(HttpStatusCode.OK, mapOf("deleted" to paymentId))
        }
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toOrderPaymentDto(): OrderPaymentDto {
    val paidByUUID = this[OrderPaymentsTable.paidBy]
    val paidByName = paidByUUID?.let { uid ->
        UsersTable.selectAll().where { UsersTable.id eq uid }
            .firstOrNull()?.get(UsersTable.name)
    }

    return OrderPaymentDto(
        id = this[OrderPaymentsTable.id].toString(),
        order_id = this[OrderPaymentsTable.orderId].toString(),
        vendor_id = this[OrderPaymentsTable.vendorId].toString(),
        payment_method = this[OrderPaymentsTable.paymentMethod],
        amount = this[OrderPaymentsTable.amount].toDouble(),
        paid_by = paidByUUID?.toString(),
        paid_by_name = paidByName,
        note = this[OrderPaymentsTable.note],
        created_at = this[OrderPaymentsTable.createdAt].toEpochMilliseconds(),
    )
}
