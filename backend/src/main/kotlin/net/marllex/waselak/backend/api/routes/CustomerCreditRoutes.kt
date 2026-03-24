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
data class CustomerCreditDto(
    val id: String,
    val vendor_id: String,
    val customer_id: String,
    val customer_name: String? = null,
    val customer_phone: String? = null,
    val balance: Double,
    val credit_limit: Double,
    val available_credit: Double,    // credit_limit - balance
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class CreditTransactionDto(
    val id: String,
    val credit_id: String,
    val vendor_id: String,
    val order_id: String? = null,
    val type: String,               // CHARGE, PAYMENT, ADJUSTMENT
    val amount: Double,
    val previous_balance: Double,
    val new_balance: Double,
    val note: String? = null,
    val created_by: String,
    val created_by_name: String? = null,
    val created_at: Long,
)

@Serializable
data class SetCreditLimitDto(
    val credit_limit: Double,
)

@Serializable
data class CreditChargeDto(
    val amount: Double,
    val order_id: String? = null,
    val note: String? = null,
)

@Serializable
data class CreditPaymentDto(
    val amount: Double,
    val note: String? = null,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.customerCreditRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/customers/{customerId}/credit") {

        // GET customer credit info
        get {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "CUSTOMER_CREDIT")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")

            val credit = transaction {
                val customerUUID = UUID.fromString(customerId)
                getOrCreateCustomerCredit(vendorUUID, customerUUID)
            }
            call.respond(HttpStatusCode.OK, credit)
        }

        // PUT set credit limit — MANAGER only
        put("/limit") {
            val trace = call.routeTrace()
            trace.step("Set credit limit")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "CUSTOMER_CREDIT")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val request = call.receive<SetCreditLimitDto>()

            require(request.credit_limit >= 0) { "Credit limit cannot be negative" }

            val credit = transaction {
                val customerUUID = UUID.fromString(customerId)
                val now = Clock.System.now()

                // Get or create credit record
                val existing = CustomerCreditsTable.selectAll().where {
                    (CustomerCreditsTable.vendorId eq vendorUUID) and
                    (CustomerCreditsTable.customerId eq customerUUID)
                }.firstOrNull()

                if (existing != null) {
                    CustomerCreditsTable.update({
                        (CustomerCreditsTable.vendorId eq vendorUUID) and
                        (CustomerCreditsTable.customerId eq customerUUID)
                    }) {
                        it[creditLimit] = BigDecimal(request.credit_limit)
                        it[updatedAt] = now
                    }
                } else {
                    CustomerCreditsTable.insert {
                        it[vendorId] = vendorUUID
                        it[CustomerCreditsTable.customerId] = customerUUID
                        it[balance] = BigDecimal.ZERO
                        it[creditLimit] = BigDecimal(request.credit_limit)
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }

                getOrCreateCustomerCredit(vendorUUID, customerUUID)
            }
            trace.step("Credit limit set", mapOf("customerId" to customerId, "limit" to request.credit_limit.toString()))
            call.respond(HttpStatusCode.OK, credit)
        }

        // POST charge to credit (increase balance = customer owes more)
        post("/charge") {
            val trace = call.routeTrace()
            trace.step("Credit charge started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "CUSTOMER_CREDIT")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val request = call.receive<CreditChargeDto>()

            require(request.amount > 0) { "Charge amount must be positive" }

            val result = transaction {
                val customerUUID = UUID.fromString(customerId)
                val now = Clock.System.now()

                // Get or create credit record
                val creditRow = CustomerCreditsTable.selectAll().where {
                    (CustomerCreditsTable.vendorId eq vendorUUID) and
                    (CustomerCreditsTable.customerId eq customerUUID)
                }.firstOrNull()

                val creditId = if (creditRow != null) {
                    creditRow[CustomerCreditsTable.id]
                } else {
                    CustomerCreditsTable.insertAndGetId {
                        it[vendorId] = vendorUUID
                        it[CustomerCreditsTable.customerId] = customerUUID
                        it[balance] = BigDecimal.ZERO
                        it[creditLimit] = BigDecimal("500.00")
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }

                val currentBalance = creditRow?.get(CustomerCreditsTable.balance) ?: BigDecimal.ZERO
                val creditLimit = creditRow?.get(CustomerCreditsTable.creditLimit) ?: BigDecimal("500.00")
                val chargeAmount = BigDecimal(request.amount)
                val newBalance = currentBalance + chargeAmount

                // Check credit limit
                require(newBalance <= creditLimit) {
                    "Charge would exceed credit limit. Current balance: ${currentBalance.toDouble()}, Limit: ${creditLimit.toDouble()}"
                }

                // Update balance
                CustomerCreditsTable.update({
                    CustomerCreditsTable.id eq creditId
                }) {
                    it[balance] = newBalance
                    it[updatedAt] = now
                }

                // Record transaction
                val txId = CreditTransactionsTable.insertAndGetId {
                    it[CreditTransactionsTable.creditId] = creditId
                    it[vendorId] = vendorUUID
                    it[orderId] = request.order_id?.let { oid -> UUID.fromString(oid) }
                    it[type] = "CHARGE"
                    it[amount] = chargeAmount
                    it[previousBalance] = currentBalance
                    it[CreditTransactionsTable.newBalance] = newBalance
                    it[note] = request.note
                    it[createdBy] = userUUID
                    it[createdAt] = now
                }

                val createdByName = UsersTable.selectAll().where { UsersTable.id eq userUUID }
                    .firstOrNull()?.get(UsersTable.name)

                CreditTransactionDto(
                    id = txId.toString(),
                    credit_id = creditId.toString(),
                    vendor_id = vendorUUID.toString(),
                    order_id = request.order_id,
                    type = "CHARGE",
                    amount = request.amount,
                    previous_balance = currentBalance.toDouble(),
                    new_balance = newBalance.toDouble(),
                    note = request.note,
                    created_by = principal.userId,
                    created_by_name = createdByName,
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Credit charged", mapOf("amount" to request.amount.toString()))
            call.respond(HttpStatusCode.Created, result)
        }

        // POST record a payment (decrease balance = customer paid off debt)
        post("/payment") {
            val trace = call.routeTrace()
            trace.step("Credit payment started")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "CUSTOMER_CREDIT")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val userUUID = UUID.fromString(principal.userId)
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val request = call.receive<CreditPaymentDto>()

            require(request.amount > 0) { "Payment amount must be positive" }

            val result = transaction {
                val customerUUID = UUID.fromString(customerId)
                val now = Clock.System.now()

                val creditRow = CustomerCreditsTable.selectAll().where {
                    (CustomerCreditsTable.vendorId eq vendorUUID) and
                    (CustomerCreditsTable.customerId eq customerUUID)
                }.firstOrNull() ?: throw NoSuchElementException("No credit account found for this customer")

                val creditId = creditRow[CustomerCreditsTable.id]
                val currentBalance = creditRow[CustomerCreditsTable.balance]
                val paymentAmount = BigDecimal(request.amount)
                val newBalance = (currentBalance - paymentAmount).coerceAtLeast(BigDecimal.ZERO)

                // Update balance
                CustomerCreditsTable.update({
                    CustomerCreditsTable.id eq creditId
                }) {
                    it[balance] = newBalance
                    it[updatedAt] = now
                }

                // Record transaction
                val txId = CreditTransactionsTable.insertAndGetId {
                    it[CreditTransactionsTable.creditId] = creditId
                    it[vendorId] = vendorUUID
                    it[type] = "PAYMENT"
                    it[amount] = paymentAmount
                    it[previousBalance] = currentBalance
                    it[CreditTransactionsTable.newBalance] = newBalance
                    it[note] = request.note
                    it[createdBy] = userUUID
                    it[createdAt] = now
                }

                val createdByName = UsersTable.selectAll().where { UsersTable.id eq userUUID }
                    .firstOrNull()?.get(UsersTable.name)

                CreditTransactionDto(
                    id = txId.toString(),
                    credit_id = creditId.toString(),
                    vendor_id = vendorUUID.toString(),
                    type = "PAYMENT",
                    amount = request.amount,
                    previous_balance = currentBalance.toDouble(),
                    new_balance = newBalance.toDouble(),
                    note = request.note,
                    created_by = principal.userId,
                    created_by_name = createdByName,
                    created_at = now.toEpochMilliseconds(),
                )
            }
            trace.step("Credit payment recorded", mapOf("amount" to request.amount.toString()))
            call.respond(HttpStatusCode.Created, result)
        }

        // GET credit transaction history
        get("/transactions") {
            val trace = call.routeTrace()
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "CUSTOMER_CREDIT")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50

            val transactions = transaction {
                val customerUUID = UUID.fromString(customerId)

                val creditRow = CustomerCreditsTable.selectAll().where {
                    (CustomerCreditsTable.vendorId eq vendorUUID) and
                    (CustomerCreditsTable.customerId eq customerUUID)
                }.firstOrNull()

                if (creditRow == null) {
                    emptyList()
                } else {
                    val creditId = creditRow[CustomerCreditsTable.id]
                    CreditTransactionsTable.selectAll().where {
                        CreditTransactionsTable.creditId eq creditId
                    }.orderBy(CreditTransactionsTable.createdAt, SortOrder.DESC)
                        .limit(limit)
                        .map { it.toCreditTransactionDto() }
                }
            }
            call.respond(HttpStatusCode.OK, transactions)
        }
    }

    // GET all customers with credit balances > 0 (debtors list) — MANAGER only
    route("/api/v1/credit") {
        get("/debtors") {
            val trace = call.routeTrace()
            trace.step("List credit debtors")
            val principal = requireRole("MANAGER", "CASHIER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "CUSTOMER_CREDIT")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val debtors = transaction {
                CustomerCreditsTable.selectAll().where {
                    (CustomerCreditsTable.vendorId eq vendorUUID) and
                    (CustomerCreditsTable.balance greater BigDecimal.ZERO)
                }.orderBy(CustomerCreditsTable.balance, SortOrder.DESC)
                    .map { row ->
                        val customerUUID = row[CustomerCreditsTable.customerId]
                        val customer = CustomersTable.selectAll().where {
                            CustomersTable.id eq customerUUID
                        }.firstOrNull()

                        val balance = row[CustomerCreditsTable.balance].toDouble()
                        val creditLimit = row[CustomerCreditsTable.creditLimit].toDouble()

                        CustomerCreditDto(
                            id = row[CustomerCreditsTable.id].toString(),
                            vendor_id = vendorUUID.toString(),
                            customer_id = customerUUID.toString(),
                            customer_name = customer?.get(CustomersTable.name),
                            customer_phone = customer?.get(CustomersTable.phone),
                            balance = balance,
                            credit_limit = creditLimit,
                            available_credit = creditLimit - balance,
                            created_at = row[CustomerCreditsTable.createdAt].toEpochMilliseconds(),
                            updated_at = row[CustomerCreditsTable.updatedAt].toEpochMilliseconds(),
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, debtors)
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────

private fun getOrCreateCustomerCredit(vendorUUID: UUID, customerUUID: UUID): CustomerCreditDto {
    val creditRow = CustomerCreditsTable.selectAll().where {
        (CustomerCreditsTable.vendorId eq vendorUUID) and
        (CustomerCreditsTable.customerId eq customerUUID)
    }.firstOrNull()

    val customer = CustomersTable.selectAll().where {
        CustomersTable.id eq customerUUID
    }.firstOrNull()

    return if (creditRow != null) {
        val balance = creditRow[CustomerCreditsTable.balance].toDouble()
        val limit = creditRow[CustomerCreditsTable.creditLimit].toDouble()
        CustomerCreditDto(
            id = creditRow[CustomerCreditsTable.id].toString(),
            vendor_id = vendorUUID.toString(),
            customer_id = customerUUID.toString(),
            customer_name = customer?.get(CustomersTable.name),
            customer_phone = customer?.get(CustomersTable.phone),
            balance = balance,
            credit_limit = limit,
            available_credit = limit - balance,
            created_at = creditRow[CustomerCreditsTable.createdAt].toEpochMilliseconds(),
            updated_at = creditRow[CustomerCreditsTable.updatedAt].toEpochMilliseconds(),
        )
    } else {
        // Return default (no credit account yet)
        val now = Clock.System.now().toEpochMilliseconds()
        CustomerCreditDto(
            id = "",
            vendor_id = vendorUUID.toString(),
            customer_id = customerUUID.toString(),
            customer_name = customer?.get(CustomersTable.name),
            customer_phone = customer?.get(CustomersTable.phone),
            balance = 0.0,
            credit_limit = 500.0,
            available_credit = 500.0,
            created_at = now,
            updated_at = now,
        )
    }
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toCreditTransactionDto(): CreditTransactionDto {
    val createdByUUID = this[CreditTransactionsTable.createdBy]
    val createdByName = UsersTable.selectAll().where { UsersTable.id eq createdByUUID }
        .firstOrNull()?.get(UsersTable.name)

    return CreditTransactionDto(
        id = this[CreditTransactionsTable.id].toString(),
        credit_id = this[CreditTransactionsTable.creditId].toString(),
        vendor_id = this[CreditTransactionsTable.vendorId].toString(),
        order_id = this[CreditTransactionsTable.orderId]?.toString(),
        type = this[CreditTransactionsTable.type],
        amount = this[CreditTransactionsTable.amount].toDouble(),
        previous_balance = this[CreditTransactionsTable.previousBalance].toDouble(),
        new_balance = this[CreditTransactionsTable.newBalance].toDouble(),
        note = this[CreditTransactionsTable.note],
        created_by = createdByUUID.toString(),
        created_by_name = createdByName,
        created_at = this[CreditTransactionsTable.createdAt].toEpochMilliseconds(),
    )
}
