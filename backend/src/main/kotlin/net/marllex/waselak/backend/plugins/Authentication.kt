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
                    AdminPrincipal(adminId = adminId, email = email)
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
                    AdminPrincipal(adminId = adminId, email = email)
                } else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized", "message" to "Invalid or expired admin token")
                )
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
    val email: String
) : Principal
