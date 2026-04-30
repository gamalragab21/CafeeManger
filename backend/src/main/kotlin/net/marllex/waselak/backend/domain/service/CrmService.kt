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

/**
 * Egypt local time. Must match the CRM UI's timezone (see CrmRoutes.CRM_TZ) so the
 * "today" / "this month" buckets used by stats line up with what the user sees.
 * Named zone handles DST transitions automatically (Egypt reinstated DST in 2023).
 */
private val CRM_TZ = TimeZone.of("Africa/Cairo")

class CrmService(private val jwtConfig: AdminJwtConfig) {

    // ── Multi-tenant scope helpers ────────────────────────────────
    //
    // Every public read/write method takes `orgId: String?` (the caller's organisation
    // UUID, sourced from `CrmPrincipal.organizationId`). We resolve it through this
    // helper which falls back to the seeded "Waselak" organisation when null — that
    // covers two cases:
    //   1. Legacy CRM tokens minted before v1.9 multi-tenancy (no org_id claim).
    //   2. Internal background jobs that don't have a session.
    // For external API calls the route layer should always have an `organizationId`
    // on the principal, so the fallback is mainly defensive.

    @Volatile private var cachedDefaultOrgId: UUID? = null

    /**
     * Resolves the organisation UUID to filter every query by. NEVER returns null —
     * a missing/legacy token transparently falls back to the Waselak default org.
     * Cached after first lookup so we don't hit the DB on every request.
     */
    private fun resolveOrgId(orgId: String?): UUID {
        // 1. Prefer the explicit value from the caller (the common path).
        orgId?.let {
            runCatching { return UUID.fromString(it) }
        }
        // 2. Fall back to the cached default.
        cachedDefaultOrgId?.let { return it }
        // 3. Look it up once — assumes the v1.9 startup migration created the row.
        val resolved = transaction {
            CrmOrganizationsTable.selectAll()
                .where { CrmOrganizationsTable.name eq "Waselak" }
                .firstOrNull()
                ?.get(CrmOrganizationsTable.id)
                ?.value
                ?: throw IllegalStateException(
                    "Default 'Waselak' CRM organisation not found — did the v1.9 backfill run?"
                )
        }
        cachedDefaultOrgId = resolved
        return resolved
    }

    // ── Auth ──────────────────────────────────────────────────────

    data class CrmLoginResult(val agentId: String, val name: String, val email: String, val role: String, val token: String)

    fun login(email: String, password: String): CrmLoginResult? = transaction {
        // Lowercase + trim before lookup so a stray capitalisation from a mobile
        // keyboard ("Owner@…") still matches a row stored as "owner@…". Without
        // this the query returns nothing and the user sees "invalid password" —
        // which is what tripped the first super-admin-provisioned tenant.
        val normalizedEmail = email.trim().lowercase()
        val agent = SalesAgentsTable.selectAll()
            .where { SalesAgentsTable.email.lowerCase() eq normalizedEmail }
            .firstOrNull() ?: return@transaction null

        if (!agent[SalesAgentsTable.active]) return@transaction null
        if (!BCrypt.checkpw(password, agent[SalesAgentsTable.passwordHash])) return@transaction null

        val id = agent[SalesAgentsTable.id].value.toString()
        // organization_id is the multi-tenant scope. After the v1.9 migration every
        // agent row has one (Waselak's team is in the seeded "Waselak" org); future
        // bought-out customers get their own org via the super-admin endpoint.
        val organizationId = agent[SalesAgentsTable.organizationId]?.value?.toString()
        val tokenBuilder = JWT.create()
            .withSubject(id)
            .withIssuer(jwtConfig.issuer)
            .withAudience(jwtConfig.audience)
            .withClaim("email", agent[SalesAgentsTable.email])
            .withClaim("role", agent[SalesAgentsTable.role])
            .withClaim("name", agent[SalesAgentsTable.name])
            .withClaim("type", "crm")
        if (organizationId != null) tokenBuilder.withClaim("organization_id", organizationId)
        val token = tokenBuilder
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000)) // 24h
            .sign(Algorithm.HMAC256(jwtConfig.secret))

        CrmLoginResult(id, agent[SalesAgentsTable.name], agent[SalesAgentsTable.email], agent[SalesAgentsTable.role], token)
    }

    // ── Super-admin: tenant management ───────────────────────────

    data class OrganizationDto(
        val id: String,
        val name: String,
        val contactEmail: String?,
        val contactPhone: String?,
        val planTier: String,
        val active: Boolean,
        val logoUrl: String?,
        val ownerAgentId: String?,   // primary owner agent — what the password-reset endpoint targets
        val ownerEmail: String?,
        val agentCount: Long,
        val clientCount: Long,
        val createdAt: Long,
    )

    data class ProvisionOrganizationResult(
        val organizationId: String,
        val organizationName: String,
        val ownerAgentId: String,
        val ownerEmail: String,
        val ownerName: String,
    )

    /**
     * Lists every CRM organization in the system. Caller must already be an authenticated
     * super-admin (the route layer enforces that — there's no per-org filtering here on
     * purpose since super-admins manage the whole platform).
     */
    fun listAllOrganizations(): List<OrganizationDto> = transaction {
        CrmOrganizationsTable.selectAll().orderBy(CrmOrganizationsTable.createdAt, SortOrder.DESC).map { row ->
            val orgId = row[CrmOrganizationsTable.id].value
            val agentCount = SalesAgentsTable.selectAll()
                .where { SalesAgentsTable.organizationId eq orgId }
                .count()
            val clientCount = CrmClientsTable.selectAll()
                .where { CrmClientsTable.organizationId eq orgId }
                .count()
            // Pick the primary owner so the super-admin UI can target a password
            // reset at "the org owner" without needing the operator to know the
            // exact agent UUID. Falls back to super_admin if no plain owner exists
            // (Waselak's own org carries super_admin instead of owner).
            val owner = SalesAgentsTable.selectAll()
                .where {
                    (SalesAgentsTable.organizationId eq orgId) and
                        (SalesAgentsTable.role inList listOf("owner", "super_admin"))
                }
                .orderBy(SalesAgentsTable.createdAt, SortOrder.ASC)
                .firstOrNull()
            OrganizationDto(
                id = orgId.toString(),
                name = row[CrmOrganizationsTable.name],
                contactEmail = row[CrmOrganizationsTable.contactEmail],
                contactPhone = row[CrmOrganizationsTable.contactPhone],
                planTier = row[CrmOrganizationsTable.planTier],
                active = row[CrmOrganizationsTable.active],
                logoUrl = row[CrmOrganizationsTable.logoUrl],
                ownerAgentId = owner?.get(SalesAgentsTable.id)?.value?.toString(),
                ownerEmail = owner?.get(SalesAgentsTable.email),
                agentCount = agentCount,
                clientCount = clientCount,
                createdAt = row[CrmOrganizationsTable.createdAt].toEpochMilliseconds(),
            )
        }
    }

    /**
     * Branding for the CRM header — name + optional logo. Returned by getOrgBranding()
     * and consumed by the layout helper to swap the Waselak logo for the tenant's own
     * once a non-Waselak owner is signed in.
     */
    data class OrgBrandingDto(val name: String, val logoUrl: String?)

    /**
     * Lightweight read-only fetch used on every dashboard page load to look up
     * the current user's org branding. Returns null when orgId is null/invalid
     * or when the row doesn't exist — callers fall back to the Waselak default.
     */
    fun getOrgBranding(orgId: String?): OrgBrandingDto? = transaction {
        val orgUuid = orgId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@transaction null
        CrmOrganizationsTable.selectAll()
            .where { CrmOrganizationsTable.id eq orgUuid }
            .firstOrNull()
            ?.let { OrgBrandingDto(it[CrmOrganizationsTable.name], it[CrmOrganizationsTable.logoUrl]) }
    }

    /**
     * Edits a tenant organization. Every field is optional — pass null to leave
     * it untouched. Used by the super-admin Edit modal. Returns false when the
     * org id doesn't exist so the route can produce a 404.
     */
    fun updateOrganization(
        orgId: String,
        name: String?,
        contactEmail: String?,
        contactPhone: String?,
        planTier: String?,
        logoUrl: String?,
    ): Boolean = transaction {
        val orgUuid = runCatching { UUID.fromString(orgId) }.getOrNull() ?: return@transaction false
        val updated = CrmOrganizationsTable.update({ CrmOrganizationsTable.id eq orgUuid }) {
            if (name != null && name.isNotBlank()) it[CrmOrganizationsTable.name] = name.trim()
            // contactEmail / contactPhone / logoUrl: empty string == clear it. We
            // only treat *null* as "leave unchanged" — the UI sends an empty
            // string when the field is intentionally cleared.
            if (contactEmail != null) it[CrmOrganizationsTable.contactEmail] = contactEmail.trim().ifBlank { null }
            if (contactPhone != null) it[CrmOrganizationsTable.contactPhone] = contactPhone.trim().ifBlank { null }
            if (planTier != null && planTier.isNotBlank()) it[CrmOrganizationsTable.planTier] = planTier.trim()
            if (logoUrl != null) it[CrmOrganizationsTable.logoUrl] = logoUrl.trim().ifBlank { null }
            it[updatedAt] = Clock.System.now()
        }
        updated > 0
    }

    /**
     * Tenant-owner-scoped logo setter. Used by the /crm/api/organization/logo
     * endpoint so org owners can change *their own* logo without going through
     * the super-admin route. The route layer guarantees orgId == the caller's
     * organizationId, so we don't have to re-verify ownership here.
     * Returns true on success, false if the orgId is malformed or unknown.
     */
    fun updateOwnOrgLogo(orgId: String, logoUrl: String?): Boolean = transaction {
        val orgUuid = runCatching { UUID.fromString(orgId) }.getOrNull() ?: return@transaction false
        CrmOrganizationsTable.update({ CrmOrganizationsTable.id eq orgUuid }) {
            it[CrmOrganizationsTable.logoUrl] = logoUrl?.trim()?.ifBlank { null }
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    /**
     * Super-admin: reset the password of *any* agent in a given org. Differs
     * from resetOwnerPassword (which targets the org's primary owner) and
     * from updateAgent (which is scoped to the agent's own org via the JWT —
     * a super-admin needs to operate on tenants other than their own).
     *
     * Verifies the agent actually belongs to `orgId` before writing — a
     * mismatched (orgId, agentId) returns null instead of leaking writes
     * across tenants. Returns the agent's email on success so the operator
     * can hand it back to the customer alongside the new password.
     */
    fun resetAgentPasswordInOrg(orgId: String, agentId: String, newPassword: String): String? = transaction {
        require(newPassword.length >= 6) { "Password must be ≥ 6 characters" }
        val orgUuid = runCatching { UUID.fromString(orgId) }.getOrNull() ?: return@transaction null
        val agentUuid = runCatching { UUID.fromString(agentId) }.getOrNull() ?: return@transaction null
        val agent = SalesAgentsTable.selectAll()
            .where { (SalesAgentsTable.id eq agentUuid) and (SalesAgentsTable.organizationId eq orgUuid) }
            .firstOrNull() ?: return@transaction null
        SalesAgentsTable.update({ SalesAgentsTable.id eq agentUuid }) {
            it[passwordHash] = BCrypt.hashpw(newPassword, BCrypt.gensalt())
            it[updatedAt] = Clock.System.now()
        }
        agent[SalesAgentsTable.email]
    }

    /**
     * Super-admin: toggle an agent's active flag, scoped to the agent's org.
     * An inactive agent can't log in (see `login()` which checks `active`).
     * Returns false on (orgId, agentId) mismatch instead of silently
     * succeeding on a different agent.
     */
    fun setAgentActiveInOrg(orgId: String, agentId: String, active: Boolean): Boolean = transaction {
        val orgUuid = runCatching { UUID.fromString(orgId) }.getOrNull() ?: return@transaction false
        val agentUuid = runCatching { UUID.fromString(agentId) }.getOrNull() ?: return@transaction false
        SalesAgentsTable.update({
            (SalesAgentsTable.id eq agentUuid) and (SalesAgentsTable.organizationId eq orgUuid)
        }) {
            it[SalesAgentsTable.active] = active
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    /**
     * Super-admin: hard-delete an agent in any org, with the same FK cleanup
     * the existing deleteAgent does (clients reassigned to null, salary +
     * commission rows removed). Refuses to delete the org's primary owner —
     * losing the owner would orphan the entire tenant. Returns false on
     * (orgId, agentId) mismatch or on owner-deletion attempt; the route
     * surfaces these as 404 / 400.
     */
    fun deleteAgentInOrg(orgId: String, agentId: String): Boolean = transaction {
        val orgUuid = runCatching { UUID.fromString(orgId) }.getOrNull() ?: return@transaction false
        val agentUuid = runCatching { UUID.fromString(agentId) }.getOrNull() ?: return@transaction false
        val row = SalesAgentsTable.selectAll()
            .where { (SalesAgentsTable.id eq agentUuid) and (SalesAgentsTable.organizationId eq orgUuid) }
            .firstOrNull() ?: return@transaction false
        val role = row[SalesAgentsTable.role]
        if (role == "owner" || role == "super_admin") {
            // Don't let the super-admin accidentally orphan a tenant. To
            // remove an owner you delete the whole org (cascade) — a
            // separate, two-confirmation flow.
            throw IllegalStateException("Cannot delete the org owner via this endpoint")
        }
        // Same cascade as existing deleteAgent — duplicated rather than
        // calling deleteAgent() so we keep this transaction atomic.
        CrmClientsTable.update({ CrmClientsTable.assignedTo eq agentUuid }) { it[assignedTo] = null }
        CrmCommissionDetailsTable.deleteWhere { CrmCommissionDetailsTable.agentId eq agentUuid }
        CrmSalaryRecordsTable.deleteWhere { CrmSalaryRecordsTable.agentId eq agentUuid }
        CrmSalaryConfigsTable.deleteWhere { CrmSalaryConfigsTable.agentId eq agentUuid }
        CrmActivitiesTable.deleteWhere { CrmActivitiesTable.agentId eq agentUuid }
        CrmAgentReviewsTable.deleteWhere { CrmAgentReviewsTable.agentId eq agentUuid }
        CrmAgentTargetsTable.deleteWhere { CrmAgentTargetsTable.agentId eq agentUuid }
        SalesAgentsTable.deleteWhere { SalesAgentsTable.id eq agentUuid } > 0
    }

    /**
     * Super-admin: load every detail needed to render the org-detail page
     * in a single round-trip. Cheaper than firing five separate queries
     * from the route layer.
     */
    data class AgentSummaryDto(
        val id: String,
        val name: String,
        val email: String,
        val phone: String?,
        val role: String,
        val photoUrl: String?,
        val active: Boolean,
        val createdAt: Long,
    )

    data class OrgDetailDto(
        val id: String,
        val name: String,
        val contactEmail: String?,
        val contactPhone: String?,
        val planTier: String,
        val active: Boolean,
        val logoUrl: String?,
        val createdAt: Long,
        val updatedAt: Long,
        val agents: List<AgentSummaryDto>,
        val clientCount: Long,
        val activityCount: Long,
        val invoiceCount: Long,
        val invoiceTotal: Double,
        val paymentTotal: Double,
        val outstandingBalance: Double,
    )

    fun getOrganizationDetail(orgId: String): OrgDetailDto? = transaction {
        val orgUuid = runCatching { UUID.fromString(orgId) }.getOrNull() ?: return@transaction null
        val row = CrmOrganizationsTable.selectAll()
            .where { CrmOrganizationsTable.id eq orgUuid }
            .firstOrNull() ?: return@transaction null

        val agents = SalesAgentsTable.selectAll()
            .where { SalesAgentsTable.organizationId eq orgUuid }
            .orderBy(SalesAgentsTable.createdAt, SortOrder.ASC)
            .map {
                AgentSummaryDto(
                    id = it[SalesAgentsTable.id].value.toString(),
                    name = it[SalesAgentsTable.name],
                    email = it[SalesAgentsTable.email],
                    phone = it[SalesAgentsTable.phone],
                    role = it[SalesAgentsTable.role],
                    photoUrl = it[SalesAgentsTable.photoUrl],
                    active = it[SalesAgentsTable.active],
                    createdAt = it[SalesAgentsTable.createdAt].toEpochMilliseconds(),
                )
            }

        val clientCount = CrmClientsTable.selectAll().where { CrmClientsTable.organizationId eq orgUuid }.count()
        val activityCount = CrmActivitiesTable.selectAll().where { CrmActivitiesTable.organizationId eq orgUuid }.count()
        val invoiceCount = CrmInvoicesTable.selectAll().where { CrmInvoicesTable.organizationId eq orgUuid }.count()

        // Sum of final_amount (post-discount, what the customer actually owes)
        // across the org's invoices, and sum of amounts across the
        // corresponding payments. Outstanding = invoiced minus paid.
        val invoiceTotal = CrmInvoicesTable.selectAll()
            .where { CrmInvoicesTable.organizationId eq orgUuid }
            .sumOf { it[CrmInvoicesTable.finalAmount].toDouble() }
        val paymentTotal = CrmPaymentsTable.selectAll()
            .where { CrmPaymentsTable.organizationId eq orgUuid }
            .sumOf { it[CrmPaymentsTable.amount].toDouble() }

        OrgDetailDto(
            id = orgUuid.toString(),
            name = row[CrmOrganizationsTable.name],
            contactEmail = row[CrmOrganizationsTable.contactEmail],
            contactPhone = row[CrmOrganizationsTable.contactPhone],
            planTier = row[CrmOrganizationsTable.planTier],
            active = row[CrmOrganizationsTable.active],
            logoUrl = row[CrmOrganizationsTable.logoUrl],
            createdAt = row[CrmOrganizationsTable.createdAt].toEpochMilliseconds(),
            updatedAt = row[CrmOrganizationsTable.updatedAt].toEpochMilliseconds(),
            agents = agents,
            clientCount = clientCount,
            activityCount = activityCount,
            invoiceCount = invoiceCount,
            invoiceTotal = invoiceTotal,
            paymentTotal = paymentTotal,
            outstandingBalance = invoiceTotal - paymentTotal,
        )
    }

    /**
     * Resets the password of the org's primary owner agent. Used by the super-admin
     * "🔑 Reset password" button when a tenant has lost their credentials. Returns
     * the owner email on success so the operator can hand it back to the customer
     * along with the new password; returns null when the org has no owner row
     * (e.g., it's empty after the migration backfill failed) so the route can 404.
     *
     * Refuses passwords shorter than 6 characters — same minimum as provisioning.
     */
    fun resetOwnerPassword(orgId: String, newPassword: String): String? = transaction {
        require(newPassword.length >= 6) { "Password must be ≥ 6 characters" }
        val orgUuid = runCatching { UUID.fromString(orgId) }.getOrNull() ?: return@transaction null
        val owner = SalesAgentsTable.selectAll()
            .where {
                (SalesAgentsTable.organizationId eq orgUuid) and
                    (SalesAgentsTable.role inList listOf("owner", "super_admin"))
            }
            .orderBy(SalesAgentsTable.createdAt, SortOrder.ASC)
            .firstOrNull() ?: return@transaction null
        val ownerId = owner[SalesAgentsTable.id].value
        SalesAgentsTable.update({ SalesAgentsTable.id eq ownerId }) {
            it[passwordHash] = BCrypt.hashpw(newPassword, BCrypt.gensalt())
            it[updatedAt] = Clock.System.now()
        }
        owner[SalesAgentsTable.email]
    }

    /**
     * Creates a new tenant organisation **and** its first owner agent atomically. The
     * buyer can immediately log in with `ownerEmail`/`ownerPassword` and see an empty
     * dashboard isolated from every other tenant. Throws on duplicate email or duplicate
     * org name so the caller can surface a meaningful error.
     */
    fun provisionOrganization(
        name: String,
        contactEmail: String?,
        contactPhone: String?,
        planTier: String,
        logoUrl: String?,
        ownerName: String,
        ownerEmail: String,
        ownerPhone: String?,
        ownerPassword: String,
    ): ProvisionOrganizationResult = transaction {
        require(name.isNotBlank()) { "Organisation name is required" }
        require(ownerName.isNotBlank()) { "Owner name is required" }
        require(ownerEmail.isNotBlank()) { "Owner email is required" }
        require(ownerPassword.length >= 6) { "Owner password must be ≥ 6 characters" }

        // Normalise email — store lowercased so the case-insensitive login lookup
        // (see CrmService.login()) finds it regardless of how the user types it
        // back. Two stores using "Owner@x.com" and "owner@x.com" should still hit
        // the same row, hence the `lowerCase()` filter on the uniqueness check too.
        val normalizedEmail = ownerEmail.trim().lowercase()
        val emailAlreadyTaken = !SalesAgentsTable.selectAll()
            .where { SalesAgentsTable.email.lowerCase() eq normalizedEmail }
            .empty()
        if (emailAlreadyTaken) throw IllegalStateException("An agent with email '$normalizedEmail' already exists")

        val orgId = CrmOrganizationsTable.insertAndGetId {
            it[CrmOrganizationsTable.name] = name.trim()
            it[CrmOrganizationsTable.contactEmail] = contactEmail?.trim()?.ifBlank { null }
            it[CrmOrganizationsTable.contactPhone] = contactPhone?.trim()?.ifBlank { null }
            it[CrmOrganizationsTable.planTier] = planTier.ifBlank { "starter" }
            it[CrmOrganizationsTable.active] = true
            it[CrmOrganizationsTable.logoUrl] = logoUrl?.trim()?.ifBlank { null }
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }
        val agentId = SalesAgentsTable.insertAndGetId {
            it[SalesAgentsTable.name] = ownerName.trim()
            it[SalesAgentsTable.email] = normalizedEmail
            it[SalesAgentsTable.passwordHash] = BCrypt.hashpw(ownerPassword, BCrypt.gensalt())
            it[SalesAgentsTable.role] = "owner"
            it[SalesAgentsTable.active] = true
            it[SalesAgentsTable.phone] = ownerPhone?.trim()?.ifBlank { null }
            it[SalesAgentsTable.organizationId] = orgId.value
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }
        ProvisionOrganizationResult(
            organizationId = orgId.value.toString(),
            organizationName = name,
            ownerAgentId = agentId.value.toString(),
            ownerEmail = normalizedEmail,
            ownerName = ownerName,
        )
    }

    /**
     * Toggles an organization's active flag. Inactive orgs can't log in (the login flow
     * already gates on the agent's active flag, but we toggle the org-level flag too so
     * a future "all my agents are active but the org is suspended" state works cleanly).
     */
    fun setOrganizationActive(orgId: String, active: Boolean): Boolean = transaction {
        CrmOrganizationsTable.update({ CrmOrganizationsTable.id eq UUID.fromString(orgId) }) {
            it[CrmOrganizationsTable.active] = active
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    /**
     * Hard-delete an entire tenant: every CRM table is purged of rows belonging to
     * this org_id, then the organisation row itself goes. Order matters because of
     * FKs — children first, parents last.
     *
     * Refuses to delete the seeded "Waselak" organisation; that one is the home of
     * your team's data and the fallback for legacy tokens, so vaporising it would be
     * catastrophic. Caller (the route layer) must have super-admin auth.
     *
     * Returns `true` on success, `false` if the org doesn't exist or is the protected
     * Waselak org. Throws on unexpected DB errors.
     */
    fun deleteOrganization(orgId: String): Boolean = transaction {
        val orgUuid = UUID.fromString(orgId)
        val row = CrmOrganizationsTable.selectAll()
            .where { CrmOrganizationsTable.id eq orgUuid }
            .firstOrNull() ?: return@transaction false
        if (row[CrmOrganizationsTable.name] == "Waselak") {
            // Refuse — refuse hard. Soft-delete via setOrganizationActive(false) is
            // what the operator wants if they ever want to "disable" their own data.
            throw IllegalStateException("Cannot hard-delete the Waselak organisation")
        }
        // Order = leaves first, root last. Each table has organization_id (added in
        // v1.9 multi-tenancy) so we can purge directly without traversing FKs.
        CrmCommissionDetailsTable.deleteWhere { organizationId eq orgUuid }
        CrmSalaryRecordsTable.deleteWhere { organizationId eq orgUuid }
        CrmSalaryConfigsTable.deleteWhere { organizationId eq orgUuid }
        CrmAgentReviewsTable.deleteWhere { organizationId eq orgUuid }
        CrmAgentTargetsTable.deleteWhere { organizationId eq orgUuid }
        CrmPaymentsTable.deleteWhere { organizationId eq orgUuid }
        CrmInvoicesTable.deleteWhere { organizationId eq orgUuid }
        CrmActivitiesTable.deleteWhere { organizationId eq orgUuid }
        CrmClientsTable.deleteWhere { organizationId eq orgUuid }
        SalesAgentsTable.deleteWhere { organizationId eq orgUuid }
        CrmOrganizationsTable.deleteWhere { CrmOrganizationsTable.id eq orgUuid } > 0
    }

    // ── Agents CRUD ───────────────────────────────────────────────

    data class AgentDto(val id: String, val name: String, val email: String, val role: String, val photoUrl: String?, val active: Boolean)

    fun listAgents(orgId: String?): List<AgentDto> = transaction {
        val org = resolveOrgId(orgId)
        SalesAgentsTable.selectAll()
            .where { SalesAgentsTable.organizationId eq org }
            .orderBy(SalesAgentsTable.name)
            .map {
                AgentDto(it[SalesAgentsTable.id].value.toString(), it[SalesAgentsTable.name], it[SalesAgentsTable.email], it[SalesAgentsTable.role], it[SalesAgentsTable.photoUrl], it[SalesAgentsTable.active])
            }
    }

    fun createAgent(orgId: String?, name: String, email: String, password: String, role: String): AgentDto = transaction {
        val org = resolveOrgId(orgId)
        // Match the login flow's case-insensitive lookup — store lowercased.
        val normalizedEmail = email.trim().lowercase()
        val id = SalesAgentsTable.insertAndGetId {
            it[SalesAgentsTable.name] = name.trim()
            it[SalesAgentsTable.email] = normalizedEmail
            it[SalesAgentsTable.passwordHash] = BCrypt.hashpw(password, BCrypt.gensalt())
            it[SalesAgentsTable.role] = role
            it[SalesAgentsTable.organizationId] = org
        }
        AgentDto(id.value.toString(), name, normalizedEmail, role, null, true)
    }

    fun updateAgent(orgId: String?, id: String, name: String?, role: String?, active: Boolean?, password: String?, photoUrl: String?): Boolean = transaction {
        val org = resolveOrgId(orgId)
        SalesAgentsTable.update({
            (SalesAgentsTable.id eq UUID.fromString(id)) and (SalesAgentsTable.organizationId eq org)
        }) {
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
        // Audit timestamps surfaced for the dashboard so agents can see when a record was
        // added/last edited. Epoch millis — UI formats in local TZ.
        val createdAt: Long, val updatedAt: Long,
    )

    fun listClients(orgId: String?, agentId: String?, isManager: Boolean): List<ClientDto> = transaction {
        val org = resolveOrgId(orgId)
        val query = CrmClientsTable.leftJoin(SalesAgentsTable, { CrmClientsTable.assignedTo }, { SalesAgentsTable.id })
            .selectAll()
            .where { CrmClientsTable.organizationId eq org }
            .orderBy(CrmClientsTable.updatedAt, SortOrder.DESC)

        if (!isManager && agentId != null) {
            query.andWhere { CrmClientsTable.assignedTo eq UUID.fromString(agentId) }
        }

        query.map { it.toClientDto() }
    }

    fun getClient(orgId: String?, id: String): ClientDto? = transaction {
        val org = resolveOrgId(orgId)
        // Defense in depth: even though `id` is a UUID an attacker would have to guess,
        // we still scope by org so a token from organisation A can't fetch records from
        // organisation B by guessing.
        CrmClientsTable.leftJoin(SalesAgentsTable, { CrmClientsTable.assignedTo }, { SalesAgentsTable.id })
            .selectAll()
            .where { (CrmClientsTable.id eq UUID.fromString(id)) and (CrmClientsTable.organizationId eq org) }
            .firstOrNull()?.toClientDto()
    }

    fun createClient(
        orgId: String?,
        clientName: String, phone: String, whatsapp: Boolean, businessName: String?, businessType: String?,
        city: String?, governorate: String?, status: String, plan: String?, monthlyAmount: Double,
        discountPercent: Int, paymentMethod: String?, assignedTo: String?, source: String?, notes: String?,
        nextActionDate: String?,
    ): ClientDto = transaction {
        val org = resolveOrgId(orgId)
        // Treat blank strings as missing so UUID/date parsers never see empty input.
        val assignedToClean = assignedTo?.takeIf { it.isNotBlank() }
        val nextActionDateClean = nextActionDate?.takeIf { it.isNotBlank() }
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
            if (assignedToClean != null) it[CrmClientsTable.assignedTo] = UUID.fromString(assignedToClean)
            it[CrmClientsTable.leadSource] = source
            it[CrmClientsTable.notes] = notes
            it[firstContactAt] = Clock.System.now()
            it[lastContactAt] = Clock.System.now()
            if (nextActionDateClean != null) it[CrmClientsTable.nextActionDate] = LocalDate.parse(nextActionDateClean)
            it[interactionCount] = 1
            // Multi-tenant scope: stamp every new client with the caller's organisation
            // so it's visible only to their CRM session. Without this the row would
            // land in NULL and the org-scoped list queries wouldn't return it.
            it[CrmClientsTable.organizationId] = org
            // Exposed's column default (Clock.System.now()) is frozen at JVM start, so old
            // rows ended up sharing one timestamp. Set these explicitly on every insert.
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }
        getClient(orgId, id.value.toString())!!
    }

    fun updateClient(
        orgId: String?,
        id: String, clientName: String?, phone: String?, whatsapp: Boolean?, businessName: String?,
        businessType: String?, city: String?, governorate: String?, status: String?, plan: String?,
        monthlyAmount: Double?, discountPercent: Int?, paymentMethod: String?, assignedTo: String?,
        source: String?, notes: String?, nextActionDate: String?,
    ): Boolean = transaction {
        val org = resolveOrgId(orgId)
        // Treat blank strings as "field not provided" so the front-end's empty form fields
        // don't blow up UUID.fromString / LocalDate.parse (which would surface as 400 Bad
        // Request via StatusPages' IllegalArgumentException handler).
        val assignedToClean = assignedTo?.takeIf { it.isNotBlank() }
        val nextActionDateClean = nextActionDate?.takeIf { it.isNotBlank() }
        // Scope by org so a token from organisation A can't update a row in organisation B
        // by guessing its UUID. UPDATE returns 0 if the WHERE matches no row.
        CrmClientsTable.update({
            (CrmClientsTable.id eq UUID.fromString(id)) and (CrmClientsTable.organizationId eq org)
        }) {
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
            if (assignedToClean != null) it[CrmClientsTable.assignedTo] = UUID.fromString(assignedToClean)
            if (source != null) it[CrmClientsTable.leadSource] = source
            if (notes != null) it[CrmClientsTable.notes] = notes
            if (nextActionDateClean != null) it[CrmClientsTable.nextActionDate] = LocalDate.parse(nextActionDateClean)
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    fun deleteClient(orgId: String?, id: String): Boolean = transaction {
        val org = resolveOrgId(orgId)
        val cid = UUID.fromString(id)
        // Verify the client is in the caller's org before cascading delete. Otherwise
        // a token from organisation A could destroy client+invoices+activities in B
        // by guessing the UUID — silent and catastrophic.
        val belongsToOrg = !CrmClientsTable.selectAll()
            .where { (CrmClientsTable.id eq cid) and (CrmClientsTable.organizationId eq org) }
            .empty()
        if (!belongsToOrg) return@transaction false
        CrmCommissionDetailsTable.deleteWhere { clientId eq cid }
        CrmActivitiesTable.deleteWhere { clientId eq cid }
        CrmPaymentsTable.deleteWhere { clientId eq cid }
        CrmInvoicesTable.deleteWhere { CrmInvoicesTable.clientId eq cid }
        CrmClientsTable.deleteWhere {
            (CrmClientsTable.id eq cid) and (CrmClientsTable.organizationId eq org)
        } > 0
    }

    /**
     * Returns true if the given agent is the one the client is currently assigned to.
     * Used to authorise agent-level edits/deletes on their own clients while letting
     * owners/managers act on anyone's.
     */
    fun isClientOwnedBy(orgId: String?, clientId: String, agentId: String): Boolean = transaction {
        val org = resolveOrgId(orgId)
        CrmClientsTable
            .select(CrmClientsTable.assignedTo)
            .where { (CrmClientsTable.id eq UUID.fromString(clientId)) and (CrmClientsTable.organizationId eq org) }
            .firstOrNull()
            ?.get(CrmClientsTable.assignedTo)
            ?.value
            ?.toString() == agentId
    }

    // ── Activities CRUD ───────────────────────────────────────────

    data class ActivityDto(
        val id: String, val agentId: String, val agentName: String, val clientId: String, val clientName: String,
        val actionType: String?, val channel: String?, val previousStatus: String?, val newStatus: String?,
        val planOffered: String?, val amount: Double?, val discountPercent: Int?, val callDuration: String?,
        val result: String?, val nextStep: String?, val nextDate: String?, val notes: String?, val createdAt: Long,
    )

    /**
     * Activities for a single client, newest first. Used by the client-details page
     * to render the per-client timeline. No ownership filtering here — the caller
     * is expected to have already authorised access to the client record.
     */
    fun listActivitiesForClient(orgId: String?, clientId: String): List<ActivityDto> = transaction {
        val org = resolveOrgId(orgId)
        CrmActivitiesTable
            .innerJoin(SalesAgentsTable, { CrmActivitiesTable.agentId }, { SalesAgentsTable.id })
            .innerJoin(CrmClientsTable, { CrmActivitiesTable.clientId }, { CrmClientsTable.id })
            .selectAll()
            .where {
                (CrmActivitiesTable.clientId eq UUID.fromString(clientId)) and
                    (CrmActivitiesTable.organizationId eq org)
            }
            .orderBy(CrmActivitiesTable.createdAt, SortOrder.DESC)
            .map {
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

    // `limit` is nullable on purpose: the dashboard preview passes a small cap (e.g. 10),
    // but the full /crm/activities page and the /crm/api/activities endpoint must NOT cap —
    // the front-end search/filter is client-side and can only match rows that were rendered.
    // Capping silently dropped older activities for managers (who scan org-wide) so a phone
    // search returned fewer rows than a call-center agent saw on the same data. listClients
    // is also uncapped, so the two stay in parity.
    fun listActivities(orgId: String?, agentId: String?, isManager: Boolean, limit: Int? = null): List<ActivityDto> = transaction {
        val org = resolveOrgId(orgId)
        val query = CrmActivitiesTable
            .innerJoin(SalesAgentsTable, { CrmActivitiesTable.agentId }, { SalesAgentsTable.id })
            .innerJoin(CrmClientsTable, { CrmActivitiesTable.clientId }, { CrmClientsTable.id })
            .selectAll()
            .where { CrmActivitiesTable.organizationId eq org }
            .orderBy(CrmActivitiesTable.createdAt, SortOrder.DESC)

        if (limit != null) query.limit(limit)

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
        orgId: String?,
        agentId: String, clientId: String, actionType: String?, channel: String?,
        previousStatus: String?, newStatus: String?, planOffered: String?,
        amount: Double?, discountPercent: Int?, callDuration: String?,
        result: String?, nextStep: String?, nextDate: String?, notes: String?,
    ): String = transaction {
        val org = resolveOrgId(orgId)
        // Blank-in-form ↔ missing-in-API: treat "" as null so we don't feed bogus values
        // into UUID.fromString / LocalDate.parse (which would fail as a confusing 400).
        val clientIdClean = clientId.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing clientId")
        val nextDateClean = nextDate?.takeIf { it.isNotBlank() }
        // Verify the client belongs to the same organisation as the activity. Without
        // this an attacker could log activities against another org's clients.
        val clientUuid = UUID.fromString(clientIdClean)
        val clientInOrg = !CrmClientsTable.selectAll()
            .where { (CrmClientsTable.id eq clientUuid) and (CrmClientsTable.organizationId eq org) }
            .empty()
        if (!clientInOrg) throw IllegalArgumentException("Client not in this organisation")
        val id = CrmActivitiesTable.insertAndGetId {
            it[CrmActivitiesTable.agentId] = UUID.fromString(agentId)
            it[CrmActivitiesTable.clientId] = clientUuid
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
            if (nextDateClean != null) it[CrmActivitiesTable.nextDate] = LocalDate.parse(nextDateClean)
            it[CrmActivitiesTable.notes] = notes
            it[CrmActivitiesTable.organizationId] = org
            // Same reason as createClient: don't rely on the frozen JVM-start default.
            it[createdAt] = Clock.System.now()
        }

        // Update client status + last contact + interaction count. The org check on the
        // UPDATE keeps it consistent with the insert above — same belt-and-braces.
        CrmClientsTable.update({
            (CrmClientsTable.id eq clientUuid) and (CrmClientsTable.organizationId eq org)
        }) {
            if (newStatus != null) it[status] = newStatus
            it[lastContactAt] = Clock.System.now()
            if (nextDateClean != null) it[nextActionDate] = LocalDate.parse(nextDateClean)
            with(SqlExpressionBuilder) {
                it.update(interactionCount, interactionCount + 1)
            }
            it[updatedAt] = Clock.System.now()
        }

        id.value.toString()
    }

    /**
     * Returns true if the given agent created this activity. Used for agent-level
     * authorisation when editing/deleting — owners and managers bypass this check.
     */
    fun isActivityOwnedBy(orgId: String?, activityId: String, agentId: String): Boolean = transaction {
        val org = resolveOrgId(orgId)
        CrmActivitiesTable
            .select(CrmActivitiesTable.agentId)
            .where {
                (CrmActivitiesTable.id eq UUID.fromString(activityId)) and
                    (CrmActivitiesTable.organizationId eq org)
            }
            .firstOrNull()
            ?.get(CrmActivitiesTable.agentId)
            ?.value
            ?.toString() == agentId
    }

    fun updateActivity(
        orgId: String?,
        id: String, actionType: String?, channel: String?,
        previousStatus: String?, newStatus: String?, planOffered: String?,
        amount: Double?, discountPercent: Int?, callDuration: String?,
        result: String?, nextStep: String?, nextDate: String?, notes: String?,
    ): Boolean = transaction {
        val org = resolveOrgId(orgId)
        val aid = UUID.fromString(id)
        // Blank strings from HTML forms should behave as "unset", not "invalid date".
        val nextDateClean = nextDate?.takeIf { it.isNotBlank() }
        val updated = CrmActivitiesTable.update({
            (CrmActivitiesTable.id eq aid) and (CrmActivitiesTable.organizationId eq org)
        }) {
            if (actionType != null) it[CrmActivitiesTable.actionType] = actionType
            if (channel != null) it[CrmActivitiesTable.channel] = channel
            if (previousStatus != null) it[CrmActivitiesTable.previousStatus] = previousStatus
            if (newStatus != null) it[CrmActivitiesTable.newStatus] = newStatus
            if (planOffered != null) it[CrmActivitiesTable.planOffered] = planOffered
            if (amount != null) it[CrmActivitiesTable.amount] = java.math.BigDecimal.valueOf(amount)
            if (discountPercent != null) it[CrmActivitiesTable.discountPercent] = discountPercent
            if (callDuration != null) it[CrmActivitiesTable.callDuration] = callDuration
            if (result != null) it[CrmActivitiesTable.result] = result
            if (nextStep != null) it[CrmActivitiesTable.nextStep] = nextStep
            if (nextDateClean != null) it[CrmActivitiesTable.nextDate] = LocalDate.parse(nextDateClean)
            if (notes != null) it[CrmActivitiesTable.notes] = notes
        }
        if (updated > 0 && newStatus != null) {
            // Keep the client's status in sync the same way createActivity does.
            val clientId = CrmActivitiesTable
                .select(CrmActivitiesTable.clientId)
                .where { CrmActivitiesTable.id eq aid }
                .firstOrNull()
                ?.get(CrmActivitiesTable.clientId)
                ?.value
            if (clientId != null) {
                CrmClientsTable.update({ CrmClientsTable.id eq clientId }) {
                    it[status] = newStatus
                    it[updatedAt] = Clock.System.now()
                }
            }
        }
        updated > 0
    }

    fun deleteActivity(orgId: String?, id: String): Boolean = transaction {
        val org = resolveOrgId(orgId)
        CrmActivitiesTable.deleteWhere {
            (CrmActivitiesTable.id eq UUID.fromString(id)) and
                (CrmActivitiesTable.organizationId eq org)
        } > 0
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

    fun getStats(orgId: String?, agentId: String?, isManager: Boolean): CrmStats = transaction {
        val org = resolveOrgId(orgId)
        // Every aggregation here is org-scoped. The base filters always include
        // `organization_id = ?`; agent-scoped filters layer on top for non-manager users.
        val clientFilter: Op<Boolean> = if (!isManager && agentId != null)
            (CrmClientsTable.organizationId eq org) and (CrmClientsTable.assignedTo eq UUID.fromString(agentId))
        else CrmClientsTable.organizationId eq org

        val totalClients = CrmClientsTable.selectAll().where { clientFilter }.count()

        val activityFilter: Op<Boolean> = if (!isManager && agentId != null)
            (CrmActivitiesTable.organizationId eq org) and (CrmActivitiesTable.agentId eq UUID.fromString(agentId))
        else CrmActivitiesTable.organizationId eq org

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
            // Per-agent stats: only count agents in the same org so a manager from
            // org A doesn't see anyone from org B.
            SalesAgentsTable.selectAll().where {
                (SalesAgentsTable.active eq true) and (SalesAgentsTable.organizationId eq org)
            }.map { agent ->
                val aid = agent[SalesAgentsTable.id].value
                val aidStr = aid.toString()
                val clientCount = CrmClientsTable.selectAll()
                    .where { (CrmClientsTable.assignedTo eq aid) and (CrmClientsTable.organizationId eq org) }
                    .count()
                val actCount = CrmActivitiesTable.selectAll()
                    .where { (CrmActivitiesTable.agentId eq aid) and (CrmActivitiesTable.organizationId eq org) }
                    .count()
                val sub = CrmClientsTable.selectAll().where {
                    (CrmClientsTable.assignedTo eq aid) and
                        (CrmClientsTable.organizationId eq org) and
                        (CrmClientsTable.status eq "مشترك")
                }.count()
                val pd = CrmClientsTable.selectAll().where {
                    (CrmClientsTable.assignedTo eq aid) and
                        (CrmClientsTable.organizationId eq org) and
                        (CrmClientsTable.status eq "مدفوع")
                }.count()
                val rev = CrmClientsTable.selectAll()
                    .where {
                        (CrmClientsTable.assignedTo eq aid) and
                            (CrmClientsTable.organizationId eq org) and
                            (CrmClientsTable.status inList listOf("مشترك", "مدفوع"))
                    }
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
            createdAt = this[CrmClientsTable.createdAt].toEpochMilliseconds(),
            updatedAt = this[CrmClientsTable.updatedAt].toEpochMilliseconds(),
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

    fun nextInvoiceNumber(orgId: String?): String = transaction {
        val org = resolveOrgId(orgId)
        // Per-org invoice numbering — each tenant gets its own INV-YYYY-NNNN sequence,
        // restarting at 0001 each year. Without scoping by org, two tenants would share
        // a counter and an invoice number could clash.
        val count = CrmInvoicesTable.selectAll()
            .where { CrmInvoicesTable.organizationId eq org }
            .count()
        val year = Clock.System.now().toLocalDateTime(CRM_TZ).year
        "INV-$year-${(count + 1).toString().padStart(4, '0')}"
    }

    fun listInvoices(orgId: String?, clientId: String? = null, status: String? = null): List<InvoiceDto> = transaction {
        val org = resolveOrgId(orgId)
        val query = CrmInvoicesTable
            .innerJoin(CrmClientsTable, { CrmInvoicesTable.clientId }, { CrmClientsTable.id })
            .selectAll()
            .where { CrmInvoicesTable.organizationId eq org }
            .orderBy(CrmInvoicesTable.createdAt, SortOrder.DESC)

        if (clientId != null) query.andWhere { CrmInvoicesTable.clientId eq UUID.fromString(clientId) }
        if (status != null) query.andWhere { CrmInvoicesTable.status eq status }

        val today = Clock.System.now().toLocalDateTime(CRM_TZ).date

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
        orgId: String?,
        clientId: String, plan: String, period: String, amount: Double,
        discountPercent: Int, dueDate: String, paymentMethod: String?,
        notes: String?, createdBy: String?,
    ): InvoiceDto = transaction {
        val org = resolveOrgId(orgId)
        val final = amount * (1 - discountPercent / 100.0)
        val invNum = nextInvoiceNumber(orgId)
        // Verify the client lives in the same org as the invoice we're creating.
        // Without this you could craft an invoice against another tenant's client.
        val clientUuid = UUID.fromString(clientId)
        val clientInOrg = !CrmClientsTable.selectAll()
            .where { (CrmClientsTable.id eq clientUuid) and (CrmClientsTable.organizationId eq org) }
            .empty()
        if (!clientInOrg) throw IllegalArgumentException("Client not in this organisation")
        CrmInvoicesTable.insertAndGetId {
            it[CrmInvoicesTable.clientId] = clientUuid
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
            it[CrmInvoicesTable.organizationId] = org
        }

        // Auto-mark overdue invoices for this client (also scoped to the same org).
        val today = Clock.System.now().toLocalDateTime(CRM_TZ).date
        CrmInvoicesTable.update({
            (CrmInvoicesTable.clientId eq clientUuid) and
            (CrmInvoicesTable.organizationId eq org) and
            (CrmInvoicesTable.status eq "غير مدفوع") and
            (CrmInvoicesTable.dueDate less today)
        }) {
            it[status] = "متأخر"
        }

        listInvoices(orgId, clientId).first()
    }

    fun recordPayment(
        orgId: String?,
        invoiceId: String, amount: Double, paymentMethod: String, notes: String?, receivedBy: String?,
    ): Boolean = transaction {
        val org = resolveOrgId(orgId)
        val invoiceUuid = UUID.fromString(invoiceId)
        val invoice = CrmInvoicesTable.selectAll()
            .where { (CrmInvoicesTable.id eq invoiceUuid) and (CrmInvoicesTable.organizationId eq org) }
            .firstOrNull() ?: return@transaction false

        CrmPaymentsTable.insert {
            it[CrmPaymentsTable.invoiceId] = invoiceUuid
            it[clientId] = invoice[CrmInvoicesTable.clientId]
            it[CrmPaymentsTable.amount] = java.math.BigDecimal.valueOf(amount)
            it[CrmPaymentsTable.paymentMethod] = paymentMethod
            it[CrmPaymentsTable.notes] = notes
            if (receivedBy != null) it[CrmPaymentsTable.receivedBy] = UUID.fromString(receivedBy)
            it[CrmPaymentsTable.organizationId] = org
        }

        val totalPaid = invoice[CrmInvoicesTable.paidAmount].toDouble() + amount
        val finalAmt = invoice[CrmInvoicesTable.finalAmount].toDouble()

        val today = Clock.System.now().toLocalDateTime(CRM_TZ).date

        CrmInvoicesTable.update({
            (CrmInvoicesTable.id eq invoiceUuid) and (CrmInvoicesTable.organizationId eq org)
        }) {
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

    fun getPaymentsForInvoice(orgId: String?, invoiceId: String): List<PaymentDto> = transaction {
        val org = resolveOrgId(orgId)
        CrmPaymentsTable
            .innerJoin(CrmClientsTable, { CrmPaymentsTable.clientId }, { CrmClientsTable.id })
            .selectAll()
            .where {
                (CrmPaymentsTable.invoiceId eq UUID.fromString(invoiceId)) and
                    (CrmPaymentsTable.organizationId eq org)
            }
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

    fun getBillingStats(orgId: String?): BillingStats = transaction {
        val org = resolveOrgId(orgId)
        val all = CrmInvoicesTable.selectAll()
            .where {
                (CrmInvoicesTable.status neq "ملغي") and (CrmInvoicesTable.organizationId eq org)
            }
            .toList()
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
        val startInstant = startDate.atStartOfDayIn(CRM_TZ)
        val endInstant = endDate.atStartOfDayIn(CRM_TZ)

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

    fun getAgentProfile(orgId: String?, agentId: String): AgentProfileDto? = transaction {
        val org = resolveOrgId(orgId)
        // Verify the agent belongs to the caller's org before returning anything.
        // Otherwise an authenticated user from org A could request the profile of an
        // agent in org B by guessing their UUID.
        val agent = SalesAgentsTable.selectAll()
            .where {
                (SalesAgentsTable.id eq UUID.fromString(agentId)) and
                    (SalesAgentsTable.organizationId eq org)
            }
            .firstOrNull() ?: return@transaction null

        val agentDto = AgentDto(
            agentId, agent[SalesAgentsTable.name], agent[SalesAgentsTable.email],
            agent[SalesAgentsTable.role], agent[SalesAgentsTable.photoUrl], agent[SalesAgentsTable.active],
        )

        val now = Clock.System.now().toLocalDateTime(CRM_TZ)
        val currentMonth = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}"
        val aid = UUID.fromString(agentId)

        val target = getTarget(agentId, currentMonth)
        val progress = getProgress(agentId, currentMonth)
        val reviews = listReviews(agentId)
        val pinnedReviews = reviews.filter { it.pinned }

        // Per-agent stats — clients/activities are already scoped by agentId, but layer
        // org_id on top so a stray cross-org agent UUID can't leak counts.
        val totalClients = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and (CrmClientsTable.organizationId eq org)
        }.count()
        val totalSubs = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and
                (CrmClientsTable.organizationId eq org) and
                (CrmClientsTable.status inList listOf("مشترك", "مدفوع"))
        }.count()
        val totalRevenue = CrmClientsTable.selectAll().where {
            (CrmClientsTable.assignedTo eq aid) and
                (CrmClientsTable.organizationId eq org) and
                (CrmClientsTable.status inList listOf("مشترك", "مدفوع"))
        }.sumOf { it[CrmClientsTable.monthlyAmount].toDouble() * (1 - it[CrmClientsTable.discountPercent] / 100.0) }
        val totalActivities = CrmActivitiesTable.selectAll().where {
            (CrmActivitiesTable.agentId eq aid) and (CrmActivitiesTable.organizationId eq org)
        }.count()

        val recentActivities = listActivities(orgId, agentId, isManager = false, limit = 10)

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
                it[CrmActivitiesTable.createdAt].toLocalDateTime(CRM_TZ)
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
        val today = Clock.System.now().toLocalDateTime(CRM_TZ).date
        CrmSalaryRecordsTable.update({ CrmSalaryRecordsTable.id eq UUID.fromString(recordId) }) {
            it[status] = "مدفوع"
            it[paidDate] = today
            it[updatedAt] = Clock.System.now()
        } > 0
    }
}
