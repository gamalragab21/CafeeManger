package net.marllex.cafeemanger.backend.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.config.*
import java.util.*

class JwtConfig(config: ApplicationConfig) {
    private val jwtConfig = config.config("jwt")

    val secret: String = jwtConfig.property("secret").getString()
    val issuer: String = jwtConfig.property("issuer").getString()
    val audience: String = jwtConfig.property("audience").getString()
    val realm: String = jwtConfig.property("realm").getString()
    val accessTokenExpireMs: Long = jwtConfig.propertyOrNull("accessTokenExpireMs")?.getString()?.toLong() ?: 900_000L
    val refreshTokenExpireDays: Long = jwtConfig.propertyOrNull("refreshTokenExpireDays")?.getString()?.toLong() ?: 7L

    fun generateAccessToken(userId: String, vendorId: String, role: String): String {
        return JWT.create()
            .withSubject(userId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("vendor_id", vendorId)
            .withClaim("role", role)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenExpireMs))
            .sign(Algorithm.HMAC256(secret))
    }

    fun generateRefreshToken(userId: String): String {
        val expireDaysMs = refreshTokenExpireDays * 24 * 60 * 60 * 1000
        return JWT.create()
            .withSubject(userId)
            .withIssuer(issuer)
            .withClaim("type", "refresh")
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + expireDaysMs))
            .sign(Algorithm.HMAC256(secret))
    }

    fun verifyRefreshToken(token: String): String? {
        return try {
            val decoded = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(issuer)
                .build()
                .verify(token)
            if (decoded.getClaim("type").asString() == "refresh") {
                decoded.subject
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
