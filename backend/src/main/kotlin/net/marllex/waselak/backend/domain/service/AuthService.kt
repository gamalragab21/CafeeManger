package net.marllex.waselak.backend.domain.service

import kotlinx.datetime.Clock
import net.marllex.waselak.backend.config.JwtConfig
import net.marllex.waselak.backend.data.database.RefreshTokensTable
import net.marllex.waselak.backend.data.database.UsersTable
import net.marllex.waselak.backend.data.database.VendorsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration.Companion.days

class AuthService(private val jwtConfig: JwtConfig) {

    data class AuthResult(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val vendorId: String,
        val role: String,
        val name: String,
        val phone: String,
        val email: String?
    )

    /**
     * Hash a token using SHA-256 for secure storage.
     * We never store raw refresh tokens in the database.
     */
    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Store a refresh token in the database, invalidating all previous sessions for this user.
     * This enforces single-session: only the latest refresh token is valid.
     */
    private fun storeRefreshToken(userId: UUID, refreshToken: String) {
        // Delete all existing refresh tokens for this user (invalidate other sessions)
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }

        // Insert the new refresh token
        val expiresAt = Clock.System.now().plus(jwtConfig.refreshTokenExpireDays.days)
        RefreshTokensTable.insert {
            it[RefreshTokensTable.userId] = userId
            it[tokenHash] = hashToken(refreshToken)
            it[RefreshTokensTable.expiresAt] = expiresAt
            it[createdAt] = Clock.System.now()
        }
    }

    fun login(phone: String, password: String): AuthResult {
        return transaction {
            val user = UsersTable.selectAll()
                .where { UsersTable.phone eq phone }
                .firstOrNull() ?: throw IllegalArgumentException("Invalid credentials")

            if (!user[UsersTable.active]) {
                throw IllegalStateException("Account is disabled")
            }

            val passwordValid = BCrypt.checkpw(password, user[UsersTable.passwordHash])
            if (!passwordValid) {
                throw IllegalArgumentException("Invalid credentials")
            }

            // Check if vendor is suspended
            val vendorRow = VendorsTable.selectAll()
                .where { VendorsTable.id eq user[UsersTable.vendorId] }
                .firstOrNull() ?: throw NoSuchElementException("Vendor not found")
            if (vendorRow[VendorsTable.isSuspended]) {
                val reason = vendorRow[VendorsTable.suspensionReason] ?: "No reason provided"
                throw IllegalStateException("Your vendor account is suspended: $reason")
            }

            val userId = user[UsersTable.id].value
            val userIdStr = userId.toString()
            val vendorId = user[UsersTable.vendorId].toString()
            val role = user[UsersTable.role]

            val refreshToken = jwtConfig.generateRefreshToken(userIdStr)

            // Single-session enforcement: invalidate all previous sessions, store new token
            storeRefreshToken(userId, refreshToken)

            AuthResult(
                accessToken = jwtConfig.generateAccessToken(userIdStr, vendorId, role),
                refreshToken = refreshToken,
                userId = userIdStr,
                vendorId = vendorId,
                role = role,
                name = user[UsersTable.name],
                phone = user[UsersTable.phone],
                email = user[UsersTable.email]
            )
        }
    }

    fun refreshToken(refreshToken: String): AuthResult {
        val userId = jwtConfig.verifyRefreshToken(refreshToken)
            ?: throw IllegalArgumentException("Invalid refresh token")

        return transaction {
            // Verify the refresh token exists in DB (single-session check)
            val tokenHash = hashToken(refreshToken)
            val storedToken = RefreshTokensTable.selectAll()
                .where { RefreshTokensTable.tokenHash eq tokenHash }
                .firstOrNull()
                ?: throw SecurityException("Session expired. Please login again.")

            val user = UsersTable.selectAll()
                .where { UsersTable.id eq UUID.fromString(userId) }
                .firstOrNull() ?: throw NoSuchElementException("User not found")

            if (!user[UsersTable.active]) {
                throw IllegalStateException("Account is disabled")
            }

            // Check if vendor is suspended
            val vendorCheck = VendorsTable.selectAll()
                .where { VendorsTable.id eq user[UsersTable.vendorId] }
                .firstOrNull() ?: throw NoSuchElementException("Vendor not found")
            if (vendorCheck[VendorsTable.isSuspended]) {
                val reason = vendorCheck[VendorsTable.suspensionReason] ?: "No reason provided"
                throw IllegalStateException("Your vendor account is suspended: $reason")
            }

            val userUUID = user[UsersTable.id].value
            val vendorId = user[UsersTable.vendorId].toString()
            val role = user[UsersTable.role]

            val newRefreshToken = jwtConfig.generateRefreshToken(userId)

            // Token rotation: delete old token, store new one
            RefreshTokensTable.deleteWhere { RefreshTokensTable.tokenHash eq tokenHash }
            val expiresAt = Clock.System.now().plus(jwtConfig.refreshTokenExpireDays.days)
            RefreshTokensTable.insert {
                it[RefreshTokensTable.userId] = userUUID
                it[RefreshTokensTable.tokenHash] = hashToken(newRefreshToken)
                it[RefreshTokensTable.expiresAt] = expiresAt
                it[createdAt] = Clock.System.now()
            }

            AuthResult(
                accessToken = jwtConfig.generateAccessToken(userId, vendorId, role),
                refreshToken = newRefreshToken,
                userId = userId,
                vendorId = vendorId,
                role = role,
                name = user[UsersTable.name],
                phone = user[UsersTable.phone],
                email = user[UsersTable.email]
            )
        }
    }

    companion object {
        fun hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(12))
    }
}
