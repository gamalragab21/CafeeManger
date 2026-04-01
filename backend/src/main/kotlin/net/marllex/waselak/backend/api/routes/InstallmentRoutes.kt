package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.math.BigDecimal
import java.util.*
import net.marllex.waselak.backend.domain.service.PlanService

// ── DTOs ────────────────────────────────────────────────────────────────

@Serializable
data class CreateInstallmentPlanDto(
    val customer_id: String,
    val order_id: String? = null,
    val total_amount: Double,
    val down_payment: Double = 0.0,
    val num_installments: Int,
    val late_fee_percent: Double = 0.0,
    val start_date: Long? = null,
)

@Serializable
data class RecordInstallmentPaymentDto(
    val amount: Double,
    val note: String? = null,
)

@Serializable
data class UpdateInstallmentStatusDto(
    val status: String,
)

@Serializable
data class InstallmentPlanDto(
    val id: String,
    val vendor_id: String,
    val customer_id: String,
    val customer_name: String? = null,
    val customer_phone: String? = null,
    val order_id: String? = null,
    val total_amount: Double,
    val down_payment: Double,
    val remaining_amount: Double,
    val num_installments: Int,
    val installment_amount: Double,
    val late_fee_percent: Double,
    val status: String,
    val start_date: Long,
    val payments: List<InstallmentPaymentDto> = emptyList(),
    val created_by: String,
    val created_by_name: String? = null,
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class InstallmentPaymentDto(
    val id: String,
    val plan_id: String,
    val due_date: Long,
    val amount: Double,
    val paid_amount: Double,
    val late_fee: Double,
    val status: String,
    val paid_at: Long? = null,
    val paid_by: String? = null,
    val paid_by_name: String? = null,
    val note: String? = null,
    val created_at: Long,
)

@Serializable
data class InstallmentAnalyticsDto(
    val total_plans: Int,
    val active_plans: Int,
    val completed_plans: Int,
    val defaulted_plans: Int,
    val total_revenue: Double,
    val collected_revenue: Double,
    val pending_revenue: Double,
    val overdue_revenue: Double,
    val late_fees_collected: Double,
)

// ── Helper functions ────────────────────────────────────────────────────

private fun ResultRow.toInstallmentPlanDto(payments: List<InstallmentPaymentDto> = emptyList()): InstallmentPlanDto {
    val customerRow = CustomersTable.selectAll().where { CustomersTable.id eq this@toInstallmentPlanDto[InstallmentPlansTable.customerId] }.firstOrNull()
    val creatorRow = UsersTable.selectAll().where { UsersTable.id eq this@toInstallmentPlanDto[InstallmentPlansTable.createdBy] }.firstOrNull()
    return InstallmentPlanDto(
        id = this[InstallmentPlansTable.id].toString(),
        vendor_id = this[InstallmentPlansTable.vendorId].toString(),
        customer_id = this[InstallmentPlansTable.customerId].toString(),
        customer_name = customerRow?.get(CustomersTable.name),
        customer_phone = customerRow?.get(CustomersTable.phone),
        order_id = this[InstallmentPlansTable.orderId]?.toString(),
        total_amount = this[InstallmentPlansTable.totalAmount].toDouble(),
        down_payment = this[InstallmentPlansTable.downPayment].toDouble(),
        remaining_amount = this[InstallmentPlansTable.remainingAmount].toDouble(),
        num_installments = this[InstallmentPlansTable.numInstallments],
        installment_amount = this[InstallmentPlansTable.installmentAmount].toDouble(),
        late_fee_percent = this[InstallmentPlansTable.lateFeePercent].toDouble(),
        status = this[InstallmentPlansTable.status],
        start_date = this[InstallmentPlansTable.startDate],
        payments = payments,
        created_by = this[InstallmentPlansTable.createdBy].toString(),
        created_by_name = creatorRow?.get(UsersTable.name),
        created_at = this[InstallmentPlansTable.createdAt],
        updated_at = this[InstallmentPlansTable.updatedAt],
    )
}

private fun ResultRow.toInstallmentPaymentDto(): InstallmentPaymentDto {
    val paidById = this[InstallmentPaymentsTable.paidBy]
    val paidByName = paidById?.let { id ->
        UsersTable.selectAll().where { UsersTable.id eq id }.firstOrNull()?.get(UsersTable.name)
    }
    return InstallmentPaymentDto(
        id = this[InstallmentPaymentsTable.id].toString(),
        plan_id = this[InstallmentPaymentsTable.planId].toString(),
        due_date = this[InstallmentPaymentsTable.dueDate],
        amount = this[InstallmentPaymentsTable.amount].toDouble(),
        paid_amount = this[InstallmentPaymentsTable.paidAmount].toDouble(),
        late_fee = this[InstallmentPaymentsTable.lateFee].toDouble(),
        status = this[InstallmentPaymentsTable.status],
        paid_at = this[InstallmentPaymentsTable.paidAt],
        paid_by = paidById?.toString(),
        paid_by_name = paidByName,
        note = this[InstallmentPaymentsTable.note],
        created_at = this[InstallmentPaymentsTable.createdAt],
    )
}

private fun loadPlanWithPayments(planId: UUID, vendorId: UUID): InstallmentPlanDto? {
    val planRow = InstallmentPlansTable.selectAll().where {
        (InstallmentPlansTable.id eq planId) and (InstallmentPlansTable.vendorId eq vendorId)
    }.firstOrNull() ?: return null

    val payments = InstallmentPaymentsTable.selectAll().where {
        InstallmentPaymentsTable.planId eq planId
    }.orderBy(InstallmentPaymentsTable.dueDate).map { it.toInstallmentPaymentDto() }

    return planRow.toInstallmentPlanDto(payments)
}

// ── Routes ──────────────────────────────────────────────────────────────

fun Route.installmentRoutes() {
    val planService by inject<PlanService>()

    route("/api/v1") {
        authenticate("auth-jwt") {

            // POST /installments — Create plan
            post("/installments") {
                routeTrace("POST /installments")
                val principal = requireRole("MANAGER", "CASHIER")
                val vendorId = UUID.fromString(principal.vendorId)
                planService.checkFeature(vendorId, "INSTALLMENTS")

                val request = call.receive<CreateInstallmentPlanDto>()
                require(request.total_amount > 0) { "Total amount must be positive" }
                require(request.num_installments in 1..60) { "Installments must be 1-60 months" }
                require(request.down_payment >= 0) { "Down payment must be non-negative" }
                require(request.down_payment < request.total_amount) { "Down payment must be less than total" }

                val now = Clock.System.now().toEpochMilliseconds()
                val startDate = request.start_date ?: now
                val remaining = request.total_amount - request.down_payment
                val monthlyAmount = Math.round(remaining / request.num_installments * 100.0) / 100.0

                val result = transaction {
                    val planId = UUID.randomUUID()

                    InstallmentPlansTable.insert {
                        it[id] = planId
                        it[InstallmentPlansTable.vendorId] = vendorId
                        it[customerId] = UUID.fromString(request.customer_id)
                        it[orderId] = request.order_id?.let { oid -> UUID.fromString(oid) }
                        it[totalAmount] = BigDecimal.valueOf(request.total_amount)
                        it[downPayment] = BigDecimal.valueOf(request.down_payment)
                        it[remainingAmount] = BigDecimal.valueOf(remaining)
                        it[numInstallments] = request.num_installments
                        it[installmentAmount] = BigDecimal.valueOf(monthlyAmount)
                        it[lateFeePercent] = BigDecimal.valueOf(request.late_fee_percent)
                        it[status] = "ACTIVE"
                        it[InstallmentPlansTable.startDate] = startDate
                        it[createdBy] = UUID.fromString(principal.userId)
                        it[createdAt] = now
                        it[updatedAt] = now
                    }

                    // Generate monthly payment schedule
                    val oneMonthMs = 30L * 24 * 60 * 60 * 1000
                    for (i in 0 until request.num_installments) {
                        val dueDate = startDate + (i * oneMonthMs)
                        InstallmentPaymentsTable.insert {
                            it[id] = UUID.randomUUID()
                            it[InstallmentPaymentsTable.planId] = planId
                            it[InstallmentPaymentsTable.vendorId] = vendorId
                            it[InstallmentPaymentsTable.dueDate] = dueDate
                            it[amount] = BigDecimal.valueOf(monthlyAmount)
                            it[paidAmount] = BigDecimal.ZERO
                            it[lateFee] = BigDecimal.ZERO
                            it[InstallmentPaymentsTable.status] = "PENDING"
                            it[createdAt] = now
                        }
                    }

                    loadPlanWithPayments(planId, vendorId)
                }

                call.respond(HttpStatusCode.Created, result!!)
            }

            // GET /installments — List plans
            get("/installments") {
                routeTrace("GET /installments")
                val principal = requireRole("MANAGER", "CASHIER")
                val vendorId = UUID.fromString(principal.vendorId)
                val statusFilter = call.request.queryParameters["status"]

                val plans = transaction {
                    val query = InstallmentPlansTable.selectAll().where {
                        InstallmentPlansTable.vendorId eq vendorId
                    }
                    statusFilter?.let {
                        query.andWhere { InstallmentPlansTable.status eq it.uppercase() }
                    }
                    query.orderBy(InstallmentPlansTable.createdAt, SortOrder.DESC).map { row ->
                        val payments = InstallmentPaymentsTable.selectAll().where {
                            InstallmentPaymentsTable.planId eq row[InstallmentPlansTable.id]
                        }.orderBy(InstallmentPaymentsTable.dueDate).map { it.toInstallmentPaymentDto() }
                        row.toInstallmentPlanDto(payments)
                    }
                }

                call.respond(plans)
            }

            // GET /installments/{id} — Plan detail
            get("/installments/{id}") {
                routeTrace("GET /installments/{id}")
                val principal = requireRole("MANAGER", "CASHIER")
                val vendorId = UUID.fromString(principal.vendorId)
                val planId = UUID.fromString(call.parameters["id"]!!)

                val plan = transaction { loadPlanWithPayments(planId, vendorId) }
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Plan not found"))

                call.respond(plan)
            }

            // POST /installments/{id}/payments — Record payment
            post("/installments/{id}/payments") {
                routeTrace("POST /installments/{id}/payments")
                val principal = requireRole("MANAGER", "CASHIER")
                val vendorId = UUID.fromString(principal.vendorId)
                val planId = UUID.fromString(call.parameters["id"]!!)
                val request = call.receive<RecordInstallmentPaymentDto>()
                require(request.amount > 0) { "Payment amount must be positive" }

                val now = Clock.System.now().toEpochMilliseconds()

                val payment = transaction {
                    // Find next unpaid installment
                    val nextPayment = InstallmentPaymentsTable.selectAll().where {
                        (InstallmentPaymentsTable.planId eq planId) and
                            (InstallmentPaymentsTable.status inList listOf("PENDING", "OVERDUE"))
                    }.orderBy(InstallmentPaymentsTable.dueDate).firstOrNull()
                        ?: throw NoSuchElementException("No pending payments found")

                    val paymentId = nextPayment[InstallmentPaymentsTable.id]

                    // Update payment
                    InstallmentPaymentsTable.update({ InstallmentPaymentsTable.id eq paymentId }) {
                        it[paidAmount] = BigDecimal.valueOf(request.amount)
                        it[status] = "PAID"
                        it[paidAt] = now
                        it[paidBy] = UUID.fromString(principal.userId)
                        it[note] = request.note
                    }

                    // Update plan remaining amount
                    val plan = InstallmentPlansTable.selectAll().where {
                        InstallmentPlansTable.id eq planId
                    }.first()
                    val newRemaining = plan[InstallmentPlansTable.remainingAmount].toDouble() - request.amount

                    InstallmentPlansTable.update({ InstallmentPlansTable.id eq planId }) {
                        it[remainingAmount] = BigDecimal.valueOf(maxOf(0.0, newRemaining))
                        it[updatedAt] = now
                    }

                    // Check if all payments are done → complete the plan
                    val pendingCount = InstallmentPaymentsTable.selectAll().where {
                        (InstallmentPaymentsTable.planId eq planId) and
                            (InstallmentPaymentsTable.status inList listOf("PENDING", "OVERDUE"))
                    }.count()

                    if (pendingCount == 0L) {
                        InstallmentPlansTable.update({ InstallmentPlansTable.id eq planId }) {
                            it[status] = "COMPLETED"
                            it[remainingAmount] = BigDecimal.ZERO
                            it[updatedAt] = now
                        }
                    }

                    InstallmentPaymentsTable.selectAll().where {
                        InstallmentPaymentsTable.id eq paymentId
                    }.first().toInstallmentPaymentDto()
                }

                call.respond(HttpStatusCode.Created, payment)
            }

            // PATCH /installments/{id}/status — Change plan status (MANAGER only)
            patch("/installments/{id}/status") {
                routeTrace("PATCH /installments/{id}/status")
                val principal = requireRole("MANAGER")
                val vendorId = UUID.fromString(principal.vendorId)
                val planId = UUID.fromString(call.parameters["id"]!!)
                val request = call.receive<UpdateInstallmentStatusDto>()
                require(request.status in listOf("ACTIVE", "COMPLETED", "DEFAULTED", "CANCELLED")) {
                    "Invalid status: ${request.status}"
                }

                val now = Clock.System.now().toEpochMilliseconds()

                val plan = transaction {
                    InstallmentPlansTable.update({
                        (InstallmentPlansTable.id eq planId) and (InstallmentPlansTable.vendorId eq vendorId)
                    }) {
                        it[status] = request.status
                        it[updatedAt] = now
                    }
                    loadPlanWithPayments(planId, vendorId)
                } ?: return@patch call.respond(HttpStatusCode.NotFound, mapOf("error" to "Plan not found"))

                call.respond(plan)
            }

            // POST /installments/{id}/apply-late-fee — Apply late fee (MANAGER only)
            post("/installments/{id}/apply-late-fee") {
                routeTrace("POST /installments/{id}/apply-late-fee")
                val principal = requireRole("MANAGER")
                val vendorId = UUID.fromString(principal.vendorId)
                val planId = UUID.fromString(call.parameters["id"]!!)

                val now = Clock.System.now().toEpochMilliseconds()

                val plan = transaction {
                    val planRow = InstallmentPlansTable.selectAll().where {
                        (InstallmentPlansTable.id eq planId) and (InstallmentPlansTable.vendorId eq vendorId)
                    }.firstOrNull() ?: throw NoSuchElementException("Plan not found")

                    val feePercent = planRow[InstallmentPlansTable.lateFeePercent].toDouble()
                    if (feePercent <= 0) throw IllegalArgumentException("No late fee configured for this plan")

                    // Apply fee to all overdue payments
                    val overduePayments = InstallmentPaymentsTable.selectAll().where {
                        (InstallmentPaymentsTable.planId eq planId) and
                            (InstallmentPaymentsTable.status eq "OVERDUE") and
                            (InstallmentPaymentsTable.lateFee eq BigDecimal.ZERO)
                    }.toList()

                    for (payment in overduePayments) {
                        val fee = payment[InstallmentPaymentsTable.amount].toDouble() * feePercent / 100.0
                        InstallmentPaymentsTable.update({ InstallmentPaymentsTable.id eq payment[InstallmentPaymentsTable.id] }) {
                            it[lateFee] = BigDecimal.valueOf(Math.round(fee * 100.0) / 100.0)
                        }
                    }

                    // Update plan remaining
                    val totalFees = overduePayments.sumOf {
                        it[InstallmentPaymentsTable.amount].toDouble() * feePercent / 100.0
                    }
                    val currentRemaining = planRow[InstallmentPlansTable.remainingAmount].toDouble()
                    InstallmentPlansTable.update({ InstallmentPlansTable.id eq planId }) {
                        it[remainingAmount] = BigDecimal.valueOf(currentRemaining + totalFees)
                        it[updatedAt] = now
                    }

                    loadPlanWithPayments(planId, vendorId)
                } ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Plan not found"))

                call.respond(plan)
            }

            // GET /customers/{customerId}/installments — Customer's plans
            get("/customers/{customerId}/installments") {
                routeTrace("GET /customers/{customerId}/installments")
                val principal = requireRole("MANAGER", "CASHIER")
                val vendorId = UUID.fromString(principal.vendorId)
                val customerId = UUID.fromString(call.parameters["customerId"]!!)

                val plans = transaction {
                    InstallmentPlansTable.selectAll().where {
                        (InstallmentPlansTable.vendorId eq vendorId) and
                            (InstallmentPlansTable.customerId eq customerId)
                    }.orderBy(InstallmentPlansTable.createdAt, SortOrder.DESC).map { row ->
                        val payments = InstallmentPaymentsTable.selectAll().where {
                            InstallmentPaymentsTable.planId eq row[InstallmentPlansTable.id]
                        }.orderBy(InstallmentPaymentsTable.dueDate).map { it.toInstallmentPaymentDto() }
                        row.toInstallmentPlanDto(payments)
                    }
                }

                call.respond(plans)
            }

            // GET /analytics/installments — Analytics
            get("/analytics/installments") {
                routeTrace("GET /analytics/installments")
                val principal = requireRole("MANAGER")
                val vendorId = UUID.fromString(principal.vendorId)
                val from = call.request.queryParameters["from"]?.toLongOrNull()
                val to = call.request.queryParameters["to"]?.toLongOrNull()

                val analytics = transaction {
                    val plansQuery = InstallmentPlansTable.selectAll().where {
                        InstallmentPlansTable.vendorId eq vendorId
                    }
                    from?.let { plansQuery.andWhere { InstallmentPlansTable.createdAt greaterEq it } }
                    to?.let { plansQuery.andWhere { InstallmentPlansTable.createdAt lessEq it } }

                    val plans = plansQuery.toList()

                    val allPayments = if (plans.isNotEmpty()) {
                        val planIds = plans.map { it[InstallmentPlansTable.id] }
                        InstallmentPaymentsTable.selectAll().where {
                            InstallmentPaymentsTable.planId inList planIds
                        }.toList()
                    } else emptyList()

                    val paidPayments = allPayments.filter { it[InstallmentPaymentsTable.status] == "PAID" }
                    val pendingPayments = allPayments.filter { it[InstallmentPaymentsTable.status] == "PENDING" }
                    val overduePayments = allPayments.filter { it[InstallmentPaymentsTable.status] == "OVERDUE" }

                    InstallmentAnalyticsDto(
                        total_plans = plans.size,
                        active_plans = plans.count { it[InstallmentPlansTable.status] == "ACTIVE" },
                        completed_plans = plans.count { it[InstallmentPlansTable.status] == "COMPLETED" },
                        defaulted_plans = plans.count { it[InstallmentPlansTable.status] == "DEFAULTED" },
                        total_revenue = plans.sumOf { it[InstallmentPlansTable.totalAmount].toDouble() },
                        collected_revenue = paidPayments.sumOf { it[InstallmentPaymentsTable.paidAmount].toDouble() },
                        pending_revenue = pendingPayments.sumOf { it[InstallmentPaymentsTable.amount].toDouble() },
                        overdue_revenue = overduePayments.sumOf { it[InstallmentPaymentsTable.amount].toDouble() + it[InstallmentPaymentsTable.lateFee].toDouble() },
                        late_fees_collected = paidPayments.sumOf { it[InstallmentPaymentsTable.lateFee].toDouble() },
                    )
                }

                call.respond(analytics)
            }
        }
    }
}
