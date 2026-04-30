package net.marllex.waselak.backend.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.domain.service.CrmService
import net.marllex.waselak.backend.plugins.AdminPrincipal
import org.koin.java.KoinJavaComponent

/**
 * Super-admin endpoints for provisioning and managing CRM tenant organisations.
 *
 * Mounted under the existing admin-jwt-bearer authentication so only platform admins
 * (e.g. the Waselak operations team) can create new tenants. Per-tenant CRM auth
 * (`crm-jwt`) is a different concern — those routes live in CrmRoutes.kt and are
 * scoped to the caller's organisation.
 *
 * Endpoints:
 *  - GET    /api/v1/cms/crm/organizations
 *  - POST   /api/v1/cms/crm/organizations          ← create org + first owner
 *  - PATCH  /api/v1/cms/crm/organizations/{id}/active   ← suspend / re-enable a tenant
 */

@Serializable
private data class ProvisionOrgRequest(
    val name: String,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val planTier: String? = null,
    val logoUrl: String? = null,
    val ownerName: String,
    val ownerEmail: String,
    val ownerPhone: String? = null,
    val ownerPassword: String,
)

@Serializable
private data class ProvisionOrgResponse(
    val organization_id: String,
    val organization_name: String,
    val owner_agent_id: String,
    val owner_email: String,
    val owner_name: String,
)

@Serializable
private data class OrganizationResponseDto(
    val id: String,
    val name: String,
    val contact_email: String? = null,
    val contact_phone: String? = null,
    val plan_tier: String,
    val active: Boolean,
    val agent_count: Long,
    val client_count: Long,
    val created_at: Long,
)

@Serializable
private data class SetActiveRequest(val active: Boolean)

fun Route.crmSuperAdminRoutes() {
    val crmService by KoinJavaComponent.inject<CrmService>(clazz = CrmService::class.java)

    // All endpoints below require admin-jwt-bearer auth — the same JWT scheme used by
    // the rest of the CMS admin API. Inside, we additionally check `isSuperAdmin` so
    // a regular admin role (if we add one later) can't provision new tenants.
    authenticate("admin-jwt-bearer") {
        route("/api/v1/cms/crm/organizations") {

            get {
                val principal = call.principal<AdminPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Super admin only")
                    )
                }
                val orgs = crmService.listAllOrganizations().map {
                    OrganizationResponseDto(
                        id = it.id,
                        name = it.name,
                        contact_email = it.contactEmail,
                        contact_phone = it.contactPhone,
                        plan_tier = it.planTier,
                        active = it.active,
                        agent_count = it.agentCount,
                        client_count = it.clientCount,
                        created_at = it.createdAt,
                    )
                }
                call.respond(HttpStatusCode.OK, orgs)
            }

            post {
                val principal = call.principal<AdminPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Super admin only")
                    )
                }
                val req = call.receive<ProvisionOrgRequest>()
                val result = crmService.provisionOrganization(
                    name = req.name,
                    contactEmail = req.contactEmail,
                    contactPhone = req.contactPhone,
                    planTier = req.planTier ?: "starter",
                    logoUrl = req.logoUrl,
                    ownerName = req.ownerName,
                    ownerEmail = req.ownerEmail,
                    ownerPhone = req.ownerPhone,
                    ownerPassword = req.ownerPassword,
                )
                // Return 201 Created with the credentials so the operator can hand them
                // to the buyer. Password is not echoed back — the buyer was given it
                // out-of-band when the operator entered it.
                call.respond(
                    HttpStatusCode.Created,
                    ProvisionOrgResponse(
                        organization_id = result.organizationId,
                        organization_name = result.organizationName,
                        owner_agent_id = result.ownerAgentId,
                        owner_email = result.ownerEmail,
                        owner_name = result.ownerName,
                    )
                )
            }

            patch("/{id}/active") {
                val principal = call.principal<AdminPrincipal>()!!
                if (!principal.isSuperAdmin) {
                    return@patch call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Super admin only")
                    )
                }
                val orgId = call.parameters["id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
                val req = call.receive<SetActiveRequest>()
                val ok = crmService.setOrganizationActive(orgId, req.active)
                if (ok) call.respond(HttpStatusCode.OK, mapOf("status" to "ok", "active" to req.active))
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Organisation not found"))
            }
        }
    }
}
