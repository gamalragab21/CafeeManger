package net.marllex.waselak.backend.domain.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.backend.config.AdminJwtConfig
import net.marllex.waselak.backend.data.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class CrmService(private val jwtConfig: AdminJwtConfig) {

    // ── Auth ──────────────────────────────────────────────────────

    data class CrmLoginResult(val agentId: String, val name: String, val email: String, val role: String, val token: String)

    fun login(email: String, password: String): CrmLoginResult? = transaction {
        val agent = SalesAgentsTable.selectAll()
            .where { SalesAgentsTable.email eq email }
            .firstOrNull() ?: return@transaction null

        if (!agent[SalesAgentsTable.active]) return@transaction null
        if (!BCrypt.checkpw(password, agent[SalesAgentsTable.passwordHash])) return@transaction null

        val id = agent[SalesAgentsTable.id].value.toString()
        val token = JWT.create()
            .withSubject(id)
            .withIssuer(jwtConfig.issuer)
            .withAudience(jwtConfig.audience)
            .withClaim("email", agent[SalesAgentsTable.email])
            .withClaim("role", agent[SalesAgentsTable.role])
            .withClaim("name", agent[SalesAgentsTable.name])
            .withClaim("type", "crm")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000)) // 24h
            .sign(Algorithm.HMAC256(jwtConfig.secret))

        CrmLoginResult(id, agent[SalesAgentsTable.name], agent[SalesAgentsTable.email], agent[SalesAgentsTable.role], token)
    }

    // ── Agents CRUD ───────────────────────────────────────────────

    data class AgentDto(val id: String, val name: String, val email: String, val role: String, val photoUrl: String?, val active: Boolean)

    fun listAgents(): List<AgentDto> = transaction {
        SalesAgentsTable.selectAll().orderBy(SalesAgentsTable.name).map {
            AgentDto(it[SalesAgentsTable.id].value.toString(), it[SalesAgentsTable.name], it[SalesAgentsTable.email], it[SalesAgentsTable.role], it[SalesAgentsTable.photoUrl], it[SalesAgentsTable.active])
        }
    }

    fun createAgent(name: String, email: String, password: String, role: String): AgentDto = transaction {
        val id = SalesAgentsTable.insertAndGetId {
            it[SalesAgentsTable.name] = name
            it[SalesAgentsTable.email] = email
            it[SalesAgentsTable.passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt())
            it[SalesAgentsTable.role] = role
        }
        AgentDto(id.value.toString(), name, email, role, null, true)
    }

    fun updateAgent(id: String, name: String?, role: String?, active: Boolean?, password: String?, photoUrl: String?): Boolean = transaction {
        SalesAgentsTable.update({ SalesAgentsTable.id eq UUID.fromString(id) }) {
            if (name != null) it[SalesAgentsTable.name] = name
            if (role != null) it[SalesAgentsTable.role] = role
            if (active != null) it[SalesAgentsTable.active] = active
            if (password != null) it[SalesAgentsTable.passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt())
            if (photoUrl != null) it[SalesAgentsTable.photoUrl] = photoUrl
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    fun getAgentPhotoUrl(agentId: String): String? = transaction {
        SalesAgentsTable.selectAll()
            .where { SalesAgentsTable.id eq UUID.fromString(agentId) }
            .firstOrNull()?.get(SalesAgentsTable.photoUrl)
    }

    fun deleteAgent(id: String): Boolean = transaction {
        val aid = UUID.fromString(id)
        // Clean up all FK references
        CrmClientsTable.update({ CrmClientsTable.assignedTo eq aid }) { it[assignedTo] = null }
        // Delete commission details first (FK to salary records)
        CrmCommissionDetailsTable.deleteWhere { agentId eq aid }
        CrmSalaryRecordsTable.deleteWhere { agentId eq aid }
        CrmSalaryConfigsTable.deleteWhere { agentId eq aid }
        CrmActivitiesTable.deleteWhere { agentId eq aid }
        CrmAgentTargetsTable.deleteWhere { agentId eq aid }
        CrmAgentReviewsTable.deleteWhere { agentId eq aid }
        // Nullify invoice/payment references
        CrmInvoicesTable.update({ CrmInvoicesTable.createdBy eq aid }) { it[createdBy] = null }
        CrmPaymentsTable.update({ CrmPaymentsTable.receivedBy eq aid }) { it[receivedBy] = null }
        SalesAgentsTable.deleteWhere { SalesAgentsTable.id eq aid } > 0
    }

    // ── Clients CRUD ──────────────────────────────────────────────

    data class ClientDto(
        val id: String, val clientName: String, val phone: String, val whatsapp: Boolean,
        val businessName: String?, val businessType: String?, val city: String?, val governorate: String?,
        val status: String, val plan: String?, val monthlyAmount: Double, val discountPercent: Int,
        val finalAmount: Double, val paymentMethod: String?, val assignedTo: String?, val assignedName: String?,
        val source: String?, val notes: String?,
        val firstContactAt: Long?, val lastContactAt: Long?, val nextActionDate: String?,
        val interactionCount: Int, val daysSinceLastContact: Long?,
    )

    fun listClients(agentId: String?, isManager: Boolean): List<ClientDto> = transaction {
        val query = CrmClientsTable.leftJoin(SalesAgentsTable, { CrmClientsTable.assignedTo }, { SalesAgentsTable.id })
            .selectAll()
            .orderBy(CrmClientsTable.updatedAt, SortOrder.DESC)

        if (!isManager && agentId != null) {
            query.andWhere { CrmClientsTable.assignedTo eq UUID.fromString(agentId) }
        }

        query.map { it.toClientDto() }
    }

    fun getClient(id: String): ClientDto? = transaction {
        CrmClientsTable.leftJoin(SalesAgentsTable, { CrmClientsTable.assignedTo }, { SalesAgentsTable.id })
            .selectAll().where { CrmClientsTable.id eq UUID.fromString(id) }
            .firstOrNull()?.toClientDto()
    }

    fun createClient(
        clientName: String, phone: String, whatsapp: Boolean, businessName: String?, businessType: String?,
        city: String?, governorate: String?, status: String, plan: String?, monthlyAmount: Double,
        discountPercent: Int, paymentMethod: String?, assignedTo: String?, source: String?, notes: String?,
        nextActionDate: String?,
    ): ClientDto = transaction {
        val id = CrmClientsTable.insertAndGetId {
            it[CrmClientsTable.clientName] = clientName
            it[CrmClientsTable.phone] = phone
            it[CrmClientsTable.whatsapp] = whatsapp
            it[CrmClientsTable.businessName] = businessName
            it[CrmClientsTable.businessType] = businessType
            it[CrmClientsTable.city] = city
            it[CrmClientsTable.governorate] = governorate
            it[CrmClientsTable.status] = status
            it[CrmClientsTable.plan] = plan
            it[CrmClientsTable.monthlyAmount] = java.math.BigDecimal.valueOf(monthlyAmount)
            it[CrmClientsTable.discountPercent] = discountPercent
            it[CrmClientsTable.paymentMethod] = paymentMethod
            if (assignedTo != null) it[CrmClientsTable.assignedTo] = UUID.fromString(assignedTo)
            it[CrmClientsTable.leadSource] = source
            it[CrmClientsTable.notes] = notes
            it[firstContactAt] = Clock.System.now()
            it[lastContactAt] = Clock.System.now()
            if (nextActionDate != null) it[CrmClientsTable.nextActionDate] = LocalDate.parse(nextActionDate)
            it[interactionCount] = 1
        }
        getClient(id.value.toString())!!
    }

    fun updateClient(
        id: String, clientName: String?, phone: String?, whatsapp: Boolean?, businessName: String?,
        businessType: String?, city: String?, governorate: String?, status: String?, plan: String?,
        monthlyAmount: Double?, discountPercent: Int?, paymentMethod: String?, assignedTo: String?,
        source: String?, notes: String?, nextActionDate: String?,
    ): Boolean = transaction {
        CrmClientsTable.update({ CrmClientsTable.id eq UUID.fromString(id) }) {
            if (clientName != null) it[CrmClientsTable.clientName] = clientName
            if (phone != null) it[CrmClientsTable.phone] = phone
            if (whatsapp != null) it[CrmClientsTable.whatsapp] = whatsapp
            if (businessName != null) it[CrmClientsTable.businessName] = businessName
            if (businessType != null) it[CrmClientsTable.businessType] = businessType
            if (city != null) it[CrmClientsTable.city] = city
            if (governorate != null) it[CrmClientsTable.governorate] = governorate
            if (status != null) it[CrmClientsTable.status] = status
            if (plan != null) it[CrmClientsTable.plan] = plan
            if (monthlyAmount != null) it[CrmClientsTable.monthlyAmount] = java.math.BigDecimal.valueOf(monthlyAmount)
            if (discountPercent != null) it[CrmClientsTable.discountPercent] = discountPercent
            if (paymentMethod != null) it[CrmClientsTable.paymentMethod] = paymentMethod
            if (assignedTo != null) it[CrmClientsTable.assignedTo] = UUID.fromString(assignedTo)
            if (source != null) it[CrmClientsTable.leadSource] = source
            if (notes != null) it[CrmClientsTable.notes] = notes
            if (nextActionDate != null) it[CrmClientsTable.nextActionDate] = LocalDate.parse(nextActionDate)
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    fun deleteClient(id: String): Boolean = transaction {
        val cid = UUID.fromString(id)
        CrmCommissionDetailsTable.deleteWhere { clientId eq cid }
        CrmActivitiesTable.deleteWhere { clientId eq cid }
        CrmPaymentsTable.deleteWhere { clientId eq cid }
        CrmInvoicesTable.deleteWhere { CrmInvoicesTable.clientId eq cid }
        CrmClientsTable.deleteWhere { CrmClientsTable.id eq cid } > 0
    }

    // ── Activities CRUD ───────────────────────────────────────────

    data class ActivityDto(
        val id: String, val agentId: String, val agentName: String, val clientId: String, val clientName: String,
        val actionType: String?, val channel: String?, val previousStatus: String?, val newStatus: String?,
        val planOffered: String?, val amount: Double?, val discountPercent: Int?, val callDuration: String?,
        val result: String?, val nextStep: String?, val nextDate: String?, val notes: String?, val createdAt: Long,
    )

    fun listActivities(agentId: String?, isManager: Boolean, limit: Int = 100): List<ActivityDto> = transaction {
        val query = CrmActivitiesTable
            .innerJoin(SalesAgentsTable, { CrmActivitiesTable.agentId }, { SalesAgentsTable.id })
            .innerJoin(CrmClientsTable, { CrmActivitiesTable.clientId }, { CrmClientsTable.id })
            .selectAll()
            .orderBy(CrmActivitiesTable.createdAt, SortOrder.DESC)
            .limit(limit)

        if (!isManager && agentId != null) {
            query.andWhere { CrmActivitiesTable.agentId eq UUID.fromString(agentId) }
        }

        query.map {
            ActivityDto(
                id = it[CrmActivitiesTable.id].value.toString(),
                agentId = it[CrmActivitiesTable.agentId].value.toString(),
                agentName = it[SalesAgentsTable.name],
                clientId = it[CrmActivitiesTable.clientId].value.toString(),
                clientName = it[CrmClientsTable.clientName],
                actionType = it[CrmActivitiesTable.actionType],
                channel = it[CrmActivitiesTable.channel],
                previousStatus = it[CrmActivitiesTable.previousStatus],
                newStatus = it[CrmActivitiesTable.newStatus],
                planOffered = it[CrmActivitiesTable.planOffered],
                amount = it[CrmActivitiesTable.amount]?.toDouble(),
                discountPercent = it[CrmActivitiesTable.discountPercent],
                callDuration = it[CrmActivitiesTable.callDuration],
                result = it[CrmActivitiesTable.result],
                nextStep = it[CrmActivitiesTable.nextStep],
                nextDate = it[CrmActivitiesTable.nextDate]?.toString(),
                notes = it[CrmActivitiesTable.notes],
                createdAt = it[CrmActivitiesTable.createdAt].toEpochMilliseconds(),
            )
        }
    }

    fun createActivity(
        agentId: String, clientId: String, actionType: String?, channel: String?,
        previousStatus: String?, newStatus: String?, planOffered: String?,
        amount: Double?, discountPercent: Int?, callDuration: String?,
        result: String?, nextStep: String?, nextDate: String?, notes: String?,
    ): String = transaction {
        val id = CrmActivitiesTable.insertAndGetId {
            it[CrmActivitiesTable.agentId] = UUID.fromString(agentId)
            it[CrmActivitiesTable.clientId] = UUID.fromString(clientId)
            it[CrmActivitiesTable.actionType] = actionType
            it[CrmActivitiesTable.channel] = channel
            it[CrmActivitiesTable.previousStatus] = previousStatus
            it[CrmActivitiesTable.newStatus] = newStatus
            it[CrmActivitiesTable.planOffered] = planOffered
            if (amount != null) it[CrmActivitiesTable.amount] = java.math.BigDecimal.valueOf(amount)
            it[CrmActivitiesTable.discountPercent] = discountPercent
            it[CrmActivitiesTable.callDuration] = callDuration
            it[CrmActivitiesTable.result] = result
            it[CrmActivitiesTable.nextStep] = nextStep
            if (nextDate != null) it[CrmActivitiesTable.nextDate] = LocalDate.parse(nextDate)
            it[CrmActivitiesTable.notes] = notes
        }

        // Update client status + last contact + interaction count
        CrmClientsTable.update({ CrmClientsTable.id eq UUID.fromString(clientId) }) {
            if (newStatus != null) it[status] = newStatus
            it[lastContactAt] = Clock.System.now()
            if (nextDate != null) it[nextActionDate] = LocalDate.parse(nextDate)
            with(SqlExpressionBuilder) {
                it.update(interactionCount, interactionCount + 1)
            }
            it[updatedAt] = Clock.System.now()
        }

        id.value.toString()
    }

    // ── Stats ─────────────────────────────────────────────────────

    data class CrmStats(
        val totalClients: Long, val totalActivities: Long,
        val byStatus: Map<String, Long>, val byBusinessType: Map<String, Long>,
        val subscribed: Long, val paid: Long, val monthlyRevenue: Double,
        val agentStats: List<AgentStat>,
    )

    data class AgentStat(
        val agentId: String, val agentName: String, val role: String, val photoUrl: String?,
        val totalActivities: Long, val clients: Long,
        val subscribed: Long, val paid: Long, val revenue: Double,
    )

    fun getStats(agentId: String?, isManager: Boolean): CrmStats = transaction {
        val clientFilter: Op<Boolean> = if (!isManager && agentId != null)
            CrmClientsTable.assignedTo eq UUID.fromString(agentId)
        else Op.TRUE

        val totalClients = CrmClientsTable.selectAll().where { clientFilter }.count()

        val activityFilter: Op<Boolean> = if (!isManager && agentId != null)
            CrmActivitiesTable.agentId eq UUID.fromString(agentId)
        else Op.TRUE

        val totalActivities = CrmActivitiesTable.selectAll().where { activityFilter }.count()

        val byStatus = CrmClientsTable.select(CrmClientsTable.status, CrmClientsTable.status.count())
            .where { clientFilter }
            .groupBy(CrmClientsTable.status)
            .associate { it[CrmClientsTable.status] to it[CrmClientsTable.status.count()] }

        val byBiz = CrmClientsTable.select(CrmClientsTable.businessType, CrmClientsTable.businessType.count())
            .where { clientFilter and CrmClientsTable.businessType.isNotNull() }
            .groupBy(CrmClientsTable.businessType)
            .associate { (it[CrmClientsTable.businessType] ?: "غير محدد") to it[CrmClientsTable.businessType.count()] }

        val subscribed = byStatus.getOrDefault("مشترك", 0L)
        val paid = byStatus.getOrDefault("مدفوع", 0L)

        val revenue = CrmClientsTable.selectAll()
            .where { clientFilter and (CrmClientsTable.status inList listOf("مشترك", "مدفوع")) }
            .sumOf {
                val amount = it[CrmClientsTable.monthlyAmount].toDouble()
                val disc = it[CrmClientsTable.discountPercent]
                amount * (1 - disc / 100.0)
            }

        val agentStats = if (isManager) {
            SalesAgentsTable.selectAll().where { SalesAgentsTable.active eq true }.map { agent ->
                val aid = agent[SalesAgentsTable.id].value
                val aidStr = aid.toString()
                val clientCount = CrmClientsTable.selectAll().where { CrmClientsTable.assignedTo eq aid }.count()
                val actCount = CrmActivitiesTable.selectAll().where { CrmActivitiesTable.agentId eq aid }.count()
                val sub = CrmClientsTable.selectAll().where { (CrmClientsTable.assignedTo eq aid) and (CrmClientsTable.status eq "مشترك") }.count()
                val pd = CrmClientsTable.selectAll().where { (CrmClientsTable.assignedTo eq aid) and (CrmClientsTable.status eq "مدفوع") }.count()
                val rev = CrmClientsTable.selectAll()
                    .where { (CrmClientsTable.assignedTo eq aid) and (CrmClientsTable.status inList listOf("مشترك", "مدفوع")) }
                    .sumOf { it[CrmClientsTable.monthlyAmount].toDouble() * (1 - it[CrmClientsTable.discountPercent] / 100.0) }
                AgentStat(aidStr, agent[SalesAgentsTable.name], agent[SalesAgentsTable.role], agent[SalesAgentsTable.photoUrl], actCount, clientCount, sub, pd, rev)
            }
        } else emptyList()

        CrmStats(totalClients, totalActivities, byStatus, byBiz, subscribed, paid, revenue, agentStats)
    }

    // ── Helper ────────────────────────────────────────────────────

    private fun ResultRow.toClientDto(): ClientDto {
        val amount = this[CrmClientsTable.monthlyAmount].toDouble()
        val disc = this[CrmClientsTable.discountPercent]
        val lastContact = this[CrmClientsTable.lastContactAt]
        val daysSince = lastContact?.let {
            (Clock.System.now() - it).inWholeDays
        }
        return ClientDto(
            id = this[CrmClientsTable.id].value.toString(),
            clientName = this[CrmClientsTable.clientName],
            phone = this[CrmClientsTable.phone],
            whatsapp = this[CrmClientsTable.whatsapp],
            businessName = this[CrmClientsTable.businessName],
            businessType = this[CrmClientsTable.businessType],
            city = this[CrmClientsTable.city],
            governorate = this[CrmClientsTable.governorate],
            status = this[CrmClientsTable.status],
            plan = this[CrmClientsTable.plan],
            monthlyAmount = amount,
            discountPercent = disc,
            finalAmount = amount * (1 - disc / 100.0),
            paymentMethod = this[CrmClientsTable.paymentMethod],
            assignedTo = this[CrmClientsTable.assignedTo]?.value?.toString(),
            assignedName = try { this[SalesAgentsTable.name] } catch (_: Exception) { null },
            source = this[CrmClientsTable.leadSource],
            notes = this[CrmClientsTable.notes],
            firstContactAt = this[CrmClientsTable.firstContactAt]?.toEpochMilliseconds(),
            lastContactAt = lastContact?.toEpochMilliseconds(),
            nextActionDate = this[CrmClientsTable.nextActionDate]?.toString(),
            interactionCount = this[CrmClientsTable.interactionCount],
            daysSinceLastContact = daysSince,
        )
    }

    // ── Invoices / Billing ────────────────────────────────────────

    data class InvoiceDto(
        val id: String, val clientId: String, val clientName: String, val clientPhone: String,
        val invoiceNumber: String, val plan: String, val period: String,
        val amount: Double, val discountPercent: Int, val finalAmount: Double,
        val paidAmount: Double, val remainingAmount: Double,
        val status: String, val dueDate: String, val paidDate: String?,
        val paymentMethod: String?, val notes: String?,
        val createdBy: String?, val createdAt: Long, val isOverdue: Boolean,
    )

    data class PaymentDto(
        val id: String, val invoiceId: String, val clientName: String,
        val amount: Double, val paymentMethod: String, val notes: String?,
        val receivedBy: String?, val createdAt: Long,
    )

    fun nextInvoiceNumber(): String = transaction {
        val count = CrmInvoicesTable.selectAll().count()
        val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        "INV-$year-${(count + 1).toString().padStart(4, '0')}"
    }

    fun listInvoices(clientId: String? = null, status: String? = null): List<InvoiceDto> = transaction {
        val query = CrmInvoicesTable
            .innerJoin(CrmClientsTable, { CrmInvoicesTable.clientId }, { CrmClientsTable.id })
            .selectAll()
            .orderBy(CrmInvoicesTable.createdAt, SortOrder.DESC)

        if (clientId != null) query.andWhere { CrmInvoicesTable.clientId eq UUID.fromString(clientId) }
        if (status != null) query.andWhere { CrmInvoicesTable.status eq status }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        query.map { row ->
            val final = row[CrmInvoicesTable.finalAmount].toDouble()
            val paid = row[CrmInvoicesTable.paidAmount].toDouble()
            val due = row[CrmInvoicesTable.dueDate]
            InvoiceDto(
                id = row[CrmInvoicesTable.id].value.toString(),
                clientId = row[CrmInvoicesTable.clientId].value.toString(),
                clientName = row[CrmClientsTable.clientName],
                clientPhone = row[CrmClientsTable.phone],
                invoiceNumber = row[CrmInvoicesTable.invoiceNumber],
                plan = row[CrmInvoicesTable.plan],
                period = row[CrmInvoicesTable.period],
                amount = row[CrmInvoicesTable.amount].toDouble(),
                discountPercent = row[CrmInvoicesTable.discountPercent],
                finalAmount = final,
                paidAmount = paid,
                remainingAmount = final - paid,
                status = row[CrmInvoicesTable.status],
                dueDate = due.toString(),
                paidDate = row[CrmInvoicesTable.paidDate]?.toString(),
                paymentMethod = row[CrmInvoicesTable.paymentMethod],
                notes = row[CrmInvoicesTable.notes],
                createdBy = row[CrmInvoicesTable.createdBy]?.value?.toString(),
                createdAt = row[CrmInvoicesTable.createdAt].toEpochMilliseconds(),
                isOverdue = row[CrmInvoicesTable.status] != "مدفوع" && row[CrmInvoicesTable.status] != "ملغي" && due < today,
            )
        }
    }

    fun createInvoice(
        clientId: String, plan: String, period: String, amount: Double,
        discountPercent: Int, dueDate: String, paymentMethod: String?,
        notes: String?, createdBy: String?,
    ): InvoiceDto = transaction {
        val final = amount * (1 - discountPercent / 100.0)
        val invNum = nextInvoiceNumber()
        CrmInvoicesTable.insertAndGetId {
            it[CrmInvoicesTable.clientId] = UUID.fromString(clientId)
            it[invoiceNumber] = invNum
            it[CrmInvoicesTable.plan] = plan
            it[CrmInvoicesTable.period] = period
            it[CrmInvoicesTable.amount] = java.math.BigDecimal.valueOf(amount)
            it[CrmInvoicesTable.discountPercent] = discountPercent
            it[finalAmount] = java.math.BigDecimal.valueOf(final)
            it[CrmInvoicesTable.dueDate] = LocalDate.parse(dueDate)
            it[CrmInvoicesTable.paymentMethod] = paymentMethod
            it[CrmInvoicesTable.notes] = notes
            if (createdBy != null) it[CrmInvoicesTable.createdBy] = UUID.fromString(createdBy)
        }

        // Auto-mark overdue invoices for this client
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        CrmInvoicesTable.update({
            (CrmInvoicesTable.clientId eq UUID.fromString(clientId)) and
            (CrmInvoicesTable.status eq "غير مدفوع") and
            (CrmInvoicesTable.dueDate less today)
        }) {
            it[status] = "متأخر"
        }

        listInvoices(clientId).first()
    }

    fun recordPayment(
        invoiceId: String, amount: Double, paymentMethod: String, notes: String?, receivedBy: String?,
    ): Boolean = transaction {
        val invoice = CrmInvoicesTable.selectAll()
            .where { CrmInvoicesTable.id eq UUID.fromString(invoiceId) }
            .firstOrNull() ?: return@transaction false

        CrmPaymentsTable.insert {
            it[CrmPaymentsTable.invoiceId] = UUID.fromString(invoiceId)
            it[clientId] = invoice[CrmInvoicesTable.clientId]
            it[CrmPaymentsTable.amount] = java.math.BigDecimal.valueOf(amount)
            it[CrmPaymentsTable.paymentMethod] = paymentMethod
            it[CrmPaymentsTable.notes] = notes
            if (receivedBy != null) it[CrmPaymentsTable.receivedBy] = UUID.fromString(receivedBy)
        }

        val totalPaid = invoice[CrmInvoicesTable.paidAmount].toDouble() + amount
        val finalAmt = invoice[CrmInvoicesTable.finalAmount].toDouble()

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        CrmInvoicesTable.update({ CrmInvoicesTable.id eq UUID.fromString(invoiceId) }) {
            it[paidAmount] = java.math.BigDecimal.valueOf(totalPaid)
            if (totalPaid >= finalAmt) {
                it[status] = "مدفوع"
                it[paidDate] = today
            } else {
                it[status] = "مدفوع جزئي"
            }
            it[updatedAt] = Clock.System.now()
        }
        true
    }

    fun getPaymentsForInvoice(invoiceId: String): List<PaymentDto> = transaction {
        CrmPaymentsTable
            .innerJoin(CrmClientsTable, { CrmPaymentsTable.clientId }, { CrmClientsTable.id })
            .selectAll()
            .where { CrmPaymentsTable.invoiceId eq UUID.fromString(invoiceId) }
            .orderBy(CrmPaymentsTable.createdAt, SortOrder.DESC)
            .map {
                PaymentDto(
                    id = it[CrmPaymentsTable.id].value.toString(),
                    invoiceId = invoiceId,
                    clientName = it[CrmClientsTable.clientName],
                    amount = it[CrmPaymentsTable.amount].toDouble(),
                    paymentMethod = it[CrmPaymentsTable.paymentMethod],
                    notes = it[CrmPaymentsTable.notes],
                    receivedBy = it[CrmPaymentsTable.receivedBy]?.value?.toString(),
                    createdAt = it[CrmPaymentsTable.createdAt].toEpochMilliseconds(),
                )
            }
    }

    data class BillingStats(
        val totalInvoices: Long, val totalRevenue: Double, val totalPaid: Double,
        val totalOverdue: Double, val overdueCount: Long, val unpaidCount: Long,
    )

    fun getBillingStats(): BillingStats = transaction {
        val all = CrmInvoicesTable.selectAll().where { CrmInvoicesTable.status neq "ملغي" }.toList()
        val totalRevenue = all.sumOf { it[CrmInvoicesTable.finalAmount].toDouble() }
        val totalPaid = all.sumOf { it[CrmInvoicesTable.paidAmount].toDouble() }
        val overdue = all.filter { it[CrmInvoicesTable.status] == "متأخر" }
        val totalOverdue = overdue.sumOf { it[CrmInvoicesTable.finalAmount].toDouble() - it[CrmInvoicesTable.paidAmount].toDouble() }
        val unpaid = all.count { it[CrmInvoicesTable.status] in listOf("غير مدفوع", "مدفوع جزئي") }
        BillingStats(all.size.toLong(), totalRevenue, totalPaid, totalOverdue, overdue.size.toLong(), unpaid.toLong())
    }

    // ── Agent Targets ─────────────────────────────────────────────

    data class TargetDto(
        val id: String, val agentId: String, val month: String,
        val targetClients: Int, val targetSubscriptions: Int, val targetRevenue: Double,
        val notes: String?,
    )

    fun getTarget(agentId: String, month: String): TargetDto? = transaction {
        CrmAgentTargetsTable.selectAll()
            .where { (CrmAgentTargetsTable.agentId eq UUID.fromString(agentId)) and (CrmAgentTargetsTable.month eq month) }
            .firstOrNull()?.let {
                TargetDto(
                    it[CrmAgentTargetsTable.id].value.toString(), agentId, month,
                    it[CrmAgentTargetsTable.targetClients], it[CrmAgentTargetsTable.targetSubscriptions],
                    it[CrmAgentTargetsTable.targetRevenue].toDouble(), it[CrmAgentTargetsTable.notes],
                )
            }
    }

    fun setTarget(agentId: String, month: String, clients: Int, subscriptions: Int, revenue: Double, notes: String?): TargetDto = transaction {
        val existing = CrmAgentTargetsTable.selectAll()
            .where { (CrmAgentTargetsTable.agentId eq UUID.fromString(agentId)) and (CrmAgentTargetsTable.month eq month) }
            .firstOrNull()

        if (existing != null) {
            CrmAgentTargetsTable.update({
                (CrmAgentTargetsTable.agentId eq UUID.fromString(agentId)) and (CrmAgentTargetsTable.month eq month)
            }) {
                it[targetClients] = clients
                it[targetSubscriptions] = subscriptions
                it[targetRevenue] = java.math.BigDecimal.valueOf(revenue)
                it[CrmAgentTargetsTable.notes] = notes
            }
        } else {
            CrmAgentTargetsTable.insert {
                it[CrmAgentTargetsTable.agentId] = UUID.fromString(agentId)
                it[CrmAgentTargetsTable.month] = month
                it[targetClients] = clients
                it[targetSubscriptions] = subscriptions
                it[targetRevenue] = java.math.BigDecimal.valueOf(revenue)
                it[CrmAgentTargetsTable.notes] = notes
            }
        }
        getTarget(agentId, month)!!
    }

    // ── Agent Progress (auto-calculated) ──────────────────────────

    data class ProgressDto(
        val month: String,
        val actualClients: Long, val targetClients: Int, val clientsPercent: Int,
        val actualSubscriptions: Long, val targetSubscriptions: Int, val subscriptionsPercent: Int,
        val actualRevenue: Double, val targetRevenue: Double, val revenuePercent: Int,
    )

    fun getProgress(agentId: String, month: String): ProgressDto = transaction {
        val aid = UUID.fromString(agentId)
        val target = getTarget(agentId, month)

        // Parse month to get date range
        val parts = month.split("-")
        val year = parts[0].toInt()
        val mon = parts[1].toInt()
        val startDate = kotlinx.datetime.LocalDate(year, mon, 1)
        val endDate = if (mon == 12) kotlinx.datetime.LocalDate(year + 1, 1, 1) else kotlinx.datetime.LocalDate(year, mon + 1, 1)
        val startInstant = startDate.atStartOfDayIn(TimeZone.currentSystemDefault())
        val endInstant = endDate.atStartOfDayIn(TimeZone.currentSystemDefault())

        val actualClients = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and
            (CrmClientsTable.createdAt greaterEq startInstant) and
            (CrmClientsTable.createdAt less endInstant)
        }.count()

        val actualSubs = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and
            (CrmClientsTable.status inList listOf("مشترك", "مدفوع")) and
            (CrmClientsTable.updatedAt greaterEq startInstant) and
            (CrmClientsTable.updatedAt less endInstant)
        }.count()

        val actualRevenue = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and
            (CrmClientsTable.status inList listOf("مشترك", "مدفوع")) and
            (CrmClientsTable.updatedAt greaterEq startInstant) and
            (CrmClientsTable.updatedAt less endInstant)
        }.sumOf {
            it[CrmClientsTable.monthlyAmount].toDouble() * (1 - it[CrmClientsTable.discountPercent] / 100.0)
        }

        val tc = target?.targetClients ?: 0
        val ts = target?.targetSubscriptions ?: 0
        val tr = target?.targetRevenue ?: 0.0

        ProgressDto(
            month = month,
            actualClients = actualClients, targetClients = tc,
            clientsPercent = if (tc > 0) ((actualClients * 100) / tc).toInt() else 0,
            actualSubscriptions = actualSubs, targetSubscriptions = ts,
            subscriptionsPercent = if (ts > 0) ((actualSubs * 100) / ts).toInt() else 0,
            actualRevenue = actualRevenue, targetRevenue = tr,
            revenuePercent = if (tr > 0) ((actualRevenue * 100) / tr).toInt() else 0,
        )
    }

    // ── Agent Reviews ─────────────────────────────────────────────

    data class ReviewDto(
        val id: String, val agentId: String, val month: String,
        val score: Int, val review: String, val pinned: Boolean,
        val createdBy: String, val createdAt: Long,
    )

    fun listReviews(agentId: String): List<ReviewDto> = transaction {
        CrmAgentReviewsTable.selectAll()
            .where { CrmAgentReviewsTable.agentId eq UUID.fromString(agentId) }
            .orderBy(CrmAgentReviewsTable.month, SortOrder.DESC)
            .map {
                ReviewDto(
                    it[CrmAgentReviewsTable.id].value.toString(), agentId, it[CrmAgentReviewsTable.month],
                    it[CrmAgentReviewsTable.score], it[CrmAgentReviewsTable.review], it[CrmAgentReviewsTable.pinned],
                    it[CrmAgentReviewsTable.createdBy].value.toString(), it[CrmAgentReviewsTable.createdAt].toEpochMilliseconds(),
                )
            }
    }

    fun createReview(agentId: String, month: String, score: Int, review: String, createdBy: String): ReviewDto = transaction {
        val id = CrmAgentReviewsTable.insertAndGetId {
            it[CrmAgentReviewsTable.agentId] = UUID.fromString(agentId)
            it[CrmAgentReviewsTable.month] = month
            it[CrmAgentReviewsTable.score] = score.coerceIn(1, 10)
            it[CrmAgentReviewsTable.review] = review
            it[CrmAgentReviewsTable.createdBy] = UUID.fromString(createdBy)
        }
        ReviewDto(id.value.toString(), agentId, month, score.coerceIn(1, 10), review, false, createdBy, Clock.System.now().toEpochMilliseconds())
    }

    fun updateReview(reviewId: String, score: Int?, review: String?, pinned: Boolean?): Boolean = transaction {
        CrmAgentReviewsTable.update({ CrmAgentReviewsTable.id eq UUID.fromString(reviewId) }) {
            if (score != null) it[CrmAgentReviewsTable.score] = score.coerceIn(1, 10)
            if (review != null) it[CrmAgentReviewsTable.review] = review
            if (pinned != null) it[CrmAgentReviewsTable.pinned] = pinned
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    // ── Agent Profile (aggregated) ────────────────────────────────

    data class AgentProfileDto(
        val agent: AgentDto,
        val currentMonth: String,
        val target: TargetDto?,
        val progress: ProgressDto,
        val reviews: List<ReviewDto>,
        val pinnedReviews: List<ReviewDto>,
        val totalClients: Long,
        val totalSubscriptions: Long,
        val totalRevenue: Double,
        val totalActivities: Long,
        val recentActivities: List<ActivityDto>,
    )

    fun getAgentProfile(agentId: String): AgentProfileDto? = transaction {
        val agent = SalesAgentsTable.selectAll()
            .where { SalesAgentsTable.id eq UUID.fromString(agentId) }
            .firstOrNull() ?: return@transaction null

        val agentDto = AgentDto(
            agentId, agent[SalesAgentsTable.name], agent[SalesAgentsTable.email],
            agent[SalesAgentsTable.role], agent[SalesAgentsTable.photoUrl], agent[SalesAgentsTable.active],
        )

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentMonth = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
        val aid = UUID.fromString(agentId)

        val target = getTarget(agentId, currentMonth)
        val progress = getProgress(agentId, currentMonth)
        val reviews = listReviews(agentId)
        val pinnedReviews = reviews.filter { it.pinned }

        val totalClients = CrmClientsTable.selectAll().where { CrmClientsTable.assignedTo eq aid }.count()
        val totalSubs = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and (CrmClientsTable.status inList listOf("مشترك", "مدفوع"))
        }.count()
        val totalRevenue = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and (CrmClientsTable.status inList listOf("مشترك", "مدفوع"))
        }.sumOf { it[CrmClientsTable.monthlyAmount].toDouble() * (1 - it[CrmClientsTable.discountPercent] / 100.0) }
        val totalActivities = CrmActivitiesTable.selectAll().where { CrmActivitiesTable.agentId eq aid }.count()

        val recentActivities = listActivities(agentId, isManager = false, limit = 10)

        AgentProfileDto(
            agent = agentDto, currentMonth = currentMonth,
            target = target, progress = progress,
            reviews = reviews, pinnedReviews = pinnedReviews,
            totalClients = totalClients, totalSubscriptions = totalSubs,
            totalRevenue = totalRevenue, totalActivities = totalActivities,
            recentActivities = recentActivities,
        )
    }

    // ── Salary Config ─────────────────────────────────────────────

    data class SalaryConfigDto(
        val id: String?, val agentId: String, val baseSalary: Double,
        val commissionPercent: Double, val commissionType: String,
        val commissionMonths: Int, val commissionBase: String, val notes: String?,
    )

    fun getSalaryConfig(agentId: String): SalaryConfigDto = transaction {
        val row = CrmSalaryConfigsTable.selectAll()
            .where { CrmSalaryConfigsTable.agentId eq UUID.fromString(agentId) }
            .firstOrNull()
        if (row != null) {
            SalaryConfigDto(
                row[CrmSalaryConfigsTable.id].value.toString(), agentId,
                row[CrmSalaryConfigsTable.baseSalary].toDouble(),
                row[CrmSalaryConfigsTable.commissionPercent].toDouble(),
                row[CrmSalaryConfigsTable.commissionType],
                row[CrmSalaryConfigsTable.commissionMonths],
                row[CrmSalaryConfigsTable.commissionBase],
                row[CrmSalaryConfigsTable.notes],
            )
        } else {
            SalaryConfigDto(null, agentId, 0.0, 0.0, "NONE", 0, "FINAL", null)
        }
    }

    fun setSalaryConfig(
        agentId: String, baseSalary: Double, commissionPercent: Double,
        commissionType: String, commissionMonths: Int, commissionBase: String, notes: String?,
    ): SalaryConfigDto = transaction {
        val aid = UUID.fromString(agentId)
        val existing = CrmSalaryConfigsTable.selectAll()
            .where { CrmSalaryConfigsTable.agentId eq aid }.firstOrNull()

        if (existing != null) {
            CrmSalaryConfigsTable.update({ CrmSalaryConfigsTable.agentId eq aid }) {
                it[CrmSalaryConfigsTable.baseSalary] = java.math.BigDecimal.valueOf(baseSalary)
                it[CrmSalaryConfigsTable.commissionPercent] = java.math.BigDecimal.valueOf(commissionPercent)
                it[CrmSalaryConfigsTable.commissionType] = commissionType
                it[CrmSalaryConfigsTable.commissionMonths] = commissionMonths
                it[CrmSalaryConfigsTable.commissionBase] = commissionBase
                it[CrmSalaryConfigsTable.notes] = notes
                it[updatedAt] = Clock.System.now()
            }
        } else {
            CrmSalaryConfigsTable.insert {
                it[CrmSalaryConfigsTable.agentId] = aid
                it[CrmSalaryConfigsTable.baseSalary] = java.math.BigDecimal.valueOf(baseSalary)
                it[CrmSalaryConfigsTable.commissionPercent] = java.math.BigDecimal.valueOf(commissionPercent)
                it[CrmSalaryConfigsTable.commissionType] = commissionType
                it[CrmSalaryConfigsTable.commissionMonths] = commissionMonths
                it[CrmSalaryConfigsTable.commissionBase] = commissionBase
                it[CrmSalaryConfigsTable.notes] = notes
            }
        }
        getSalaryConfig(agentId)
    }

    // ── Salary Calculation ────────────────────────────────────────

    data class CommissionDetailDto(
        val clientId: String, val clientName: String, val plan: String?,
        val clientAmount: Double, val commissionPercent: Double,
        val commissionAmount: Double, val commissionType: String,
        val monthNumber: Int, val isActive: Boolean,
    )

    data class SalaryRecordDto(
        val id: String, val agentId: String, val agentName: String, val month: String,
        val baseSalary: Double, val commissionTotal: Double,
        val bonus: Double, val deductions: Double,
        val deductionReason: String?, val bonusReason: String?,
        val finalSalary: Double, val status: String, val paidDate: String?,
        val notes: String?, val commissionDetails: List<CommissionDetailDto>,
    )

    fun calculateMonthlySalary(agentId: String, month: String, createdBy: String): SalaryRecordDto = transaction {
        val aid = UUID.fromString(agentId)
        val config = getSalaryConfig(agentId)

        // Parse month
        val parts = month.split("-")
        val year = parts[0].toInt()
        val mon = parts[1].toInt()

        // Get active subscribed clients assigned to this agent
        val subscribedClients = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and
            (CrmClientsTable.status inList listOf("مشترك", "مدفوع"))
        }.toList()

        // Calculate commission for each client
        val commissionDetails = mutableListOf<CommissionDetailDto>()
        var commissionTotal = 0.0

        for (client in subscribedClients) {
            val clientId = client[CrmClientsTable.id].value
            val clientName = client[CrmClientsTable.clientName]
            val plan = client[CrmClientsTable.plan]
            val amount = if (config.commissionBase == "ORIGINAL")
                client[CrmClientsTable.monthlyAmount].toDouble()
            else
                client[CrmClientsTable.monthlyAmount].toDouble() * (1 - client[CrmClientsTable.discountPercent] / 100.0)

            // Find when client first subscribed (first activity with newStatus = مشترك)
            val firstSubscription = CrmActivitiesTable.selectAll().where {
                (CrmActivitiesTable.clientId eq clientId) and
                (CrmActivitiesTable.newStatus inList listOf("مشترك", "مدفوع"))
            }.orderBy(CrmActivitiesTable.createdAt, SortOrder.ASC).firstOrNull()

            val subscriptionDate = firstSubscription?.let {
                it[CrmActivitiesTable.createdAt].toLocalDateTime(TimeZone.currentSystemDefault())
            }

            // Calculate month number since subscription
            val monthNumber = if (subscriptionDate != null) {
                val subYear = subscriptionDate.year
                val subMonth = subscriptionDate.monthNumber
                (year - subYear) * 12 + (mon - subMonth) + 1
            } else 1

            // Check if commission applies
            val commissionAmount = when (config.commissionType) {
                "FIRST_ONLY" -> if (monthNumber == 1) amount * config.commissionPercent / 100.0 else 0.0
                "FIXED_MONTHS" -> if (monthNumber <= config.commissionMonths) amount * config.commissionPercent / 100.0 else 0.0
                "FOREVER" -> amount * config.commissionPercent / 100.0
                else -> 0.0
            }

            commissionDetails.add(CommissionDetailDto(
                clientId.toString(), clientName, plan, amount,
                config.commissionPercent, commissionAmount, config.commissionType,
                monthNumber, true,
            ))

            commissionTotal += commissionAmount
        }

        // Check for existing record
        val existing = CrmSalaryRecordsTable.selectAll().where {
            (CrmSalaryRecordsTable.agentId eq aid) and (CrmSalaryRecordsTable.month eq month)
        }.firstOrNull()

        val existingBonus = existing?.get(CrmSalaryRecordsTable.bonus)?.toDouble() ?: 0.0
        val existingDeductions = existing?.get(CrmSalaryRecordsTable.deductions)?.toDouble() ?: 0.0
        val existingBonusReason = existing?.get(CrmSalaryRecordsTable.bonusReason)
        val existingDeductionReason = existing?.get(CrmSalaryRecordsTable.deductionReason)
        val finalSalary = config.baseSalary + commissionTotal + existingBonus - existingDeductions

        val recordId = if (existing != null) {
            // Update existing
            CrmSalaryRecordsTable.update({
                (CrmSalaryRecordsTable.agentId eq aid) and (CrmSalaryRecordsTable.month eq month)
            }) {
                it[baseSalary] = java.math.BigDecimal.valueOf(config.baseSalary)
                it[CrmSalaryRecordsTable.commissionTotal] = java.math.BigDecimal.valueOf(commissionTotal)
                it[CrmSalaryRecordsTable.finalSalary] = java.math.BigDecimal.valueOf(finalSalary)
                it[updatedAt] = Clock.System.now()
            }
            // Delete old commission details
            CrmCommissionDetailsTable.deleteWhere {
                (CrmCommissionDetailsTable.agentId eq aid) and
                (salaryRecordId eq existing[CrmSalaryRecordsTable.id])
            }
            existing[CrmSalaryRecordsTable.id].value
        } else {
            CrmSalaryRecordsTable.insertAndGetId {
                it[CrmSalaryRecordsTable.agentId] = aid
                it[CrmSalaryRecordsTable.month] = month
                it[baseSalary] = java.math.BigDecimal.valueOf(config.baseSalary)
                it[CrmSalaryRecordsTable.commissionTotal] = java.math.BigDecimal.valueOf(commissionTotal)
                it[CrmSalaryRecordsTable.finalSalary] = java.math.BigDecimal.valueOf(finalSalary)
                it[CrmSalaryRecordsTable.createdBy] = UUID.fromString(createdBy)
            }.value
        }

        // Insert commission details
        for (detail in commissionDetails) {
            CrmCommissionDetailsTable.insert {
                it[salaryRecordId] = recordId
                it[CrmCommissionDetailsTable.agentId] = aid
                it[clientId] = UUID.fromString(detail.clientId)
                it[clientName] = detail.clientName
                it[CrmCommissionDetailsTable.plan] = detail.plan
                it[clientAmount] = java.math.BigDecimal.valueOf(detail.clientAmount)
                it[commissionPercent] = java.math.BigDecimal.valueOf(detail.commissionPercent)
                it[commissionAmount] = java.math.BigDecimal.valueOf(detail.commissionAmount)
                it[commissionType] = detail.commissionType
                it[monthNumber] = detail.monthNumber
                it[isActive] = detail.isActive
            }
        }

        val agentName = SalesAgentsTable.selectAll().where { SalesAgentsTable.id eq aid }
            .firstOrNull()?.get(SalesAgentsTable.name) ?: ""

        SalaryRecordDto(
            recordId.toString(), agentId, agentName, month,
            config.baseSalary, commissionTotal, existingBonus, existingDeductions,
            existingDeductionReason, existingBonusReason, finalSalary,
            existing?.get(CrmSalaryRecordsTable.status) ?: "معلق",
            existing?.get(CrmSalaryRecordsTable.paidDate)?.toString(),
            existing?.get(CrmSalaryRecordsTable.notes),
            commissionDetails,
        )
    }

    fun getSalaryRecord(agentId: String, month: String): SalaryRecordDto? = transaction {
        val aid = UUID.fromString(agentId)
        val record = CrmSalaryRecordsTable.selectAll().where {
            (CrmSalaryRecordsTable.agentId eq aid) and (CrmSalaryRecordsTable.month eq month)
        }.firstOrNull() ?: return@transaction null

        val agentName = SalesAgentsTable.selectAll().where { SalesAgentsTable.id eq aid }
            .firstOrNull()?.get(SalesAgentsTable.name) ?: ""

        val details = CrmCommissionDetailsTable.selectAll().where {
            CrmCommissionDetailsTable.salaryRecordId eq record[CrmSalaryRecordsTable.id]
        }.map {
            CommissionDetailDto(
                it[CrmCommissionDetailsTable.clientId].value.toString(),
                it[CrmCommissionDetailsTable.clientName],
                it[CrmCommissionDetailsTable.plan],
                it[CrmCommissionDetailsTable.clientAmount].toDouble(),
                it[CrmCommissionDetailsTable.commissionPercent].toDouble(),
                it[CrmCommissionDetailsTable.commissionAmount].toDouble(),
                it[CrmCommissionDetailsTable.commissionType],
                it[CrmCommissionDetailsTable.monthNumber],
                it[CrmCommissionDetailsTable.isActive],
            )
        }

        SalaryRecordDto(
            record[CrmSalaryRecordsTable.id].value.toString(), agentId, agentName, month,
            record[CrmSalaryRecordsTable.baseSalary].toDouble(),
            record[CrmSalaryRecordsTable.commissionTotal].toDouble(),
            record[CrmSalaryRecordsTable.bonus].toDouble(),
            record[CrmSalaryRecordsTable.deductions].toDouble(),
            record[CrmSalaryRecordsTable.deductionReason],
            record[CrmSalaryRecordsTable.bonusReason],
            record[CrmSalaryRecordsTable.finalSalary].toDouble(),
            record[CrmSalaryRecordsTable.status],
            record[CrmSalaryRecordsTable.paidDate]?.toString(),
            record[CrmSalaryRecordsTable.notes],
            details,
        )
    }

    fun listAllSalaryRecords(month: String): List<SalaryRecordDto> = transaction {
        val agents = SalesAgentsTable.selectAll().where { SalesAgentsTable.active eq true }
        agents.mapNotNull { agent ->
            getSalaryRecord(agent[SalesAgentsTable.id].value.toString(), month)
        }
    }

    fun updateSalaryBonusDeduction(
        recordId: String, bonus: Double?, bonusReason: String?,
        deductions: Double?, deductionReason: String?, notes: String?,
    ): Boolean = transaction {
        val record = CrmSalaryRecordsTable.selectAll()
            .where { CrmSalaryRecordsTable.id eq UUID.fromString(recordId) }
            .firstOrNull() ?: return@transaction false

        val newBonus = bonus ?: record[CrmSalaryRecordsTable.bonus].toDouble()
        val newDeductions = deductions ?: record[CrmSalaryRecordsTable.deductions].toDouble()
        val base = record[CrmSalaryRecordsTable.baseSalary].toDouble()
        val commission = record[CrmSalaryRecordsTable.commissionTotal].toDouble()
        val finalSalary = base + commission + newBonus - newDeductions

        CrmSalaryRecordsTable.update({ CrmSalaryRecordsTable.id eq UUID.fromString(recordId) }) {
            if (bonus != null) it[CrmSalaryRecordsTable.bonus] = java.math.BigDecimal.valueOf(bonus)
            if (bonusReason != null) it[CrmSalaryRecordsTable.bonusReason] = bonusReason
            if (deductions != null) it[CrmSalaryRecordsTable.deductions] = java.math.BigDecimal.valueOf(deductions)
            if (deductionReason != null) it[CrmSalaryRecordsTable.deductionReason] = deductionReason
            if (notes != null) it[CrmSalaryRecordsTable.notes] = notes
            it[CrmSalaryRecordsTable.finalSalary] = java.math.BigDecimal.valueOf(finalSalary)
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    fun markSalaryPaid(recordId: String): Boolean = transaction {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        CrmSalaryRecordsTable.update({ CrmSalaryRecordsTable.id eq UUID.fromString(recordId) }) {
            it[status] = "مدفوع"
            it[paidDate] = today
            it[updatedAt] = Clock.System.now()
        } > 0
    }
}
