package net.marllex.waselak.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import net.marllex.waselak.backend.config.AdminJwtConfig
import net.marllex.waselak.backend.config.JwtConfig

fun Application.configureAuthentication() {
    val jwtConfig = JwtConfig(environment.config)
    val adminJwtConfig = AdminJwtConfig(environment.config)

    install(Authentication) {
        // App JWT (Authorization header)
        jwt("auth-jwt") {
            realm = jwtConfig.realm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                    .withIssuer(jwtConfig.issuer)
                    .withAudience(jwtConfig.audience)
                    .build()
            )
            validate { credential ->
                val vendorId = credential.payload.getClaim("vendor_id").asString()
                val role = credential.payload.getClaim("role").asString()
                val userId = credential.payload.subject

                if (vendorId != null && role != null && userId != null) {
                    UserPrincipal(
                        userId = userId,
                        vendorId = vendorId,
                        role = role
                    )
                } else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized", "message" to "Invalid or expired token")
                )
            }
        }

        // Admin JWT (cookie-based, for web dashboard)
        jwt("admin-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(adminJwtConfig.secret))
                    .withIssuer(adminJwtConfig.issuer)
                    .withAudience(adminJwtConfig.audience)
                    .build()
            )
            authHeader { call ->
                val token = call.request.cookies["admin_token"]
                token?.let {
                    try {
                        io.ktor.http.auth.parseAuthorizationHeader("Bearer $it")
                    } catch (_: Exception) { null }
                }
            }
            validate { credential ->
                val adminId = credential.payload.subject
                val email = credential.payload.getClaim("email").asString()
                val type = credential.payload.getClaim("type").asString()

                if (adminId != null && email != null && type == "admin") {
                    val role = credential.payload.getClaim("role").asString() ?: "super_admin"
                    AdminPrincipal(adminId = adminId, email = email, role = role)
                } else null
            }
            challenge { _, _ ->
                call.respondRedirect("/admin/login")
            }
        }

        // Admin JWT (Bearer header, for CMP admin app)
        jwt("admin-jwt-bearer") {
            verifier(
                JWT.require(Algorithm.HMAC256(adminJwtConfig.secret))
                    .withIssuer(adminJwtConfig.issuer)
                    .withAudience(adminJwtConfig.audience)
                    .build()
            )
            validate { credential ->
                val adminId = credential.payload.subject
                val email = credential.payload.getClaim("email").asString()
                val type = credential.payload.getClaim("type").asString()

                if (adminId != null && email != null && type == "admin") {
                    val role = credential.payload.getClaim("role").asString() ?: "super_admin"
                    AdminPrincipal(adminId = adminId, email = email, role = role)
                } else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized", "message" to "Invalid or expired admin token")
                )
            }
        }
        // CRM JWT (cookie-based, for sales CRM dashboard)
        jwt("crm-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(adminJwtConfig.secret))
                    .withIssuer(adminJwtConfig.issuer)
                    .withAudience(adminJwtConfig.audience)
                    .build()
            )
            authHeader { call ->
                val token = call.request.cookies["crm_token"]
                token?.let {
                    try {
                        io.ktor.http.auth.parseAuthorizationHeader("Bearer $it")
                    } catch (_: Exception) { null }
                }
            }
            validate { credential ->
                val agentId = credential.payload.subject
                val email = credential.payload.getClaim("email").asString()
                val role = credential.payload.getClaim("role").asString()
                val name = credential.payload.getClaim("name").asString()
                val type = credential.payload.getClaim("type").asString()
                // organization_id arrived in v1.9 multi-tenant. Tokens minted before then
                // (still in someone's cookie) won't have the claim — for those legacy
                // sessions we fall back to null and the routes treat that as "Waselak's
                // own organization" or refuse the request, depending on context.
                val organizationId = credential.payload.getClaim("organization_id").asString()

                if (agentId != null && type == "crm") {
                    CrmPrincipal(
                        agentId = agentId,
                        email = email ?: "",
                        role = role ?: "",
                        name = name ?: "",
                        organizationId = organizationId,
                    )
                } else null
            }
            challenge { _, _ ->
                call.respondRedirect("/crm/login")
            }
        }
    }
}

data class UserPrincipal(
    val userId: String,
    val vendorId: String,
    val role: String
) : Principal

data class AdminPrincipal(
    val adminId: String,
    val email: String,
    val role: String = "super_admin"
) : Principal {
    val isSuperAdmin: Boolean get() = role == "super_admin"
}

data class CrmPrincipal(
    val agentId: String,
    val email: String,
    val role: String,
    val name: String,
    /**
     * The organization (tenant) this CRM session belongs to. NULL only for legacy
     * tokens minted before v1.9 multi-tenant — every new login carries it. Service
     * methods MUST scope by this value when reading or writing CRM data; a missing
     * filter would leak another tenant's records.
     */
    val organizationId: String? = null,
) : Principal {
    /**
     * Platform super-admin (the Waselak operations team). Can manage every tenant
     * organisation — see the list, create new ones, suspend, or hard-delete. Has
     * full owner-level powers in their own org plus access to the /crm/super/ routes.
     * (Avoid writing `/​*` literally inside KDoc — Kotlin nests block comments and an
     * unclosed inner block would swallow the rest of the file.)
     * Promoted via the v1.9 migration that ran the first time multi-tenancy shipped.
     */
    val isSuperAdmin: Boolean get() = role == "super_admin"
    // super_admin counts as an owner for permission checks — they have all the powers
    // an org owner has (and more). Without this, super_admins would be locked out of
    // routes that gate on `principal.isOwner`.
    val isOwner: Boolean get() = role == "owner" || isSuperAdmin
    val isManager: Boolean get() = isOwner || role == "مدير مبيعات"
    val isSales: Boolean get() = role == "مندوب مبيعات"
    val isCallCenter: Boolean get() = role == "كول سنتر"
    // Only owners and sales managers see the full CRM. Every other role (sales reps,
    // call-center, delivery, etc.) is scoped to records they own — clients assigned
    // to them and activities they recorded. Editing/deleting own records still works
    // (isClientOwnedBy / isActivityOwnedBy); the 400 they used to hit is prevented by
    // the empty-string hardening in CrmService, not by an expanded canSeeAll.
    val canSeeAll: Boolean get() = isOwner || role == "مدير مبيعات"
    val canSeeAnalytics: Boolean get() = isOwner
    val canManageAgents: Boolean get() = isOwner
}
