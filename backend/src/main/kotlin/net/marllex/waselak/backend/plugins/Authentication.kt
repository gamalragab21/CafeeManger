package net.marllex.waselak.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import net.marllex.waselak.backend.config.JwtConfig

fun Application.configureAuthentication() {
    val jwtConfig = JwtConfig(environment.config)

    install(Authentication) {
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
    }
}

data class UserPrincipal(
    val userId: String,
    val vendorId: String,
    val role: String
) : Principal
