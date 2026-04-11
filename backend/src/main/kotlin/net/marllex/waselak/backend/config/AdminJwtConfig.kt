package net.marllex.waselak.backend.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.config.*
import java.util.*

class AdminJwtConfig(config: ApplicationConfig) {
    private val jwtConfig = config.config("admin-jwt")

    val secret: String = jwtConfig.property("secret").getString()
    val issuer: String = jwtConfig.property("issuer").getString()
    val audience: String = jwtConfig.property("audience").getString()
    val expireMs: Long = jwtConfig.propertyOrNull("expireMs")?.getString()?.toLong() ?: 3_600_000L // 1 hour default
    val refreshExpireDays: Long = jwtConfig.propertyOrNull("refreshExpireDays")?.getString()?.toLong() ?: 30L

    fun generateToken(adminId: String, email: String, role: String = "super_admin"): String {
        return JWT.create()
            .withSubject(adminId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("email", email)
            .withClaim("type", "admin")
            .withClaim("role", role)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + expireMs))
            .sign(Algorithm.HMAC256(secret))
    }

    fun generateRefreshToken(adminId: String): String {
        val refreshExpireMs = refreshExpireDays * 24 * 60 * 60 * 1000
        return JWT.create()
            .withSubject(adminId)
            .withIssuer(issuer)
            .withClaim("type", "admin_refresh")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + refreshExpireMs))
            .sign(Algorithm.HMAC256(secret))
    }

    fun verifyToken(token: String): DecodedJWT? {
        return try {
            JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
                .verify(token)
        } catch (_: Exception) {
            null
        }
    }

    fun verifyRefreshToken(token: String): String? {
        return try {
            val decoded = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(issuer)
                .build()
                .verify(token)
            if (decoded.getClaim("type").asString() == "admin_refresh") {
                decoded.subject
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
