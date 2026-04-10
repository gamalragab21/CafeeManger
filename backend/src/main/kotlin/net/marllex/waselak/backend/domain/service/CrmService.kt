package net.marllex.waselak.backend.domain.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
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

    data class AgentDto(val id: String, val name: String, val email: String, val role: String, val active: Boolean)

    fun listAgents(): List<AgentDto> = transaction {
        SalesAgentsTable.selectAll().orderBy(SalesAgentsTable.name).map {
            AgentDto(it[SalesAgentsTable.id].value.toString(), it[SalesAgentsTable.name], it[SalesAgentsTable.email], it[SalesAgentsTable.role], it[SalesAgentsTable.active])
        }
    }

    fun createAgent(name: String, email: String, password: String, role: String): AgentDto = transaction {
        val id = SalesAgentsTable.insertAndGetId {
            it[SalesAgentsTable.name] = name
            it[SalesAgentsTable.email] = email
            it[SalesAgentsTable.passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt())
            it[SalesAgentsTable.role] = role
        }
        AgentDto(id.value.toString(), name, email, role, true)
    }

    fun updateAgent(id: String, name: String?, role: String?, active: Boolean?, password: String?): Boolean = transaction {
        SalesAgentsTable.update({ SalesAgentsTable.id eq UUID.fromString(id) }) {
            if (name != null) it[SalesAgentsTable.name] = name
            if (role != null) it[SalesAgentsTable.role] = role
            if (active != null) it[SalesAgentsTable.active] = active
            if (password != null) it[SalesAgentsTable.passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt())
            it[updatedAt] = Clock.System.now()
        } > 0
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
        val agentId: String, val agentName: String, val role: String,
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
                AgentStat(aidStr, agent[SalesAgentsTable.name], agent[SalesAgentsTable.role], actCount, clientCount, sub, pd, rev)
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
}
