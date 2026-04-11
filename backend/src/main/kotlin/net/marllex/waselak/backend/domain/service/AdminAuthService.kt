package net.marllex.waselak.backend.domain.service

import kotlinx.datetime.Clock
import net.marllex.waselak.backend.config.AdminJwtConfig
import net.marllex.waselak.backend.data.database.AdminRefreshTokensTable
import net.marllex.waselak.backend.data.database.AdminUsersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration.Companion.days

class AdminAuthService(private val adminJwtConfig: AdminJwtConfig) {

    data class AdminInfo(
        val id: String,
        val name: String,
        val email: String,
        val role: String,
        val token: String,
        val refreshToken: String,
    )

    data class TokenPair(
        val token: String,
        val refreshToken: String,
    )

    fun login(email: String, password: String): AdminInfo? {
        return transaction {
            val admin = AdminUsersTable.selectAll()
                .where { AdminUsersTable.email eq email }
                .firstOrNull() ?: return@transaction null

            if (!admin[AdminUsersTable.active]) return@transaction null

            val storedHash = admin[AdminUsersTable.passwordHash]
            if (!BCrypt.checkpw(password, storedHash)) return@transaction null

            val adminId = admin[AdminUsersTable.id].value.toString()

            // Update last login time
            AdminUsersTable.update({ AdminUsersTable.id eq admin[AdminUsersTable.id] }) {
                it[lastLoginAt] = Clock.System.now()
            }

            val adminRole = admin[AdminUsersTable.role]
            val token = adminJwtConfig.generateToken(adminId, email, adminRole)
            val refreshToken = adminJwtConfig.generateRefreshToken(adminId)

            // Store refresh token hash in DB
            AdminRefreshTokensTable.insert {
                it[AdminRefreshTokensTable.adminId] = admin[AdminUsersTable.id]
                it[tokenHash] = hashToken(refreshToken)
                it[expiresAt] = Clock.System.now().plus(adminJwtConfig.refreshExpireDays.days)
                it[createdAt] = Clock.System.now()
            }

            AdminInfo(
                id = adminId,
                name = admin[AdminUsersTable.name],
                email = email,
                role = adminRole,
                token = token,
                refreshToken = refreshToken,
            )
        }
    }

    fun refreshToken(refreshToken: String): TokenPair? {
        val adminId = adminJwtConfig.verifyRefreshToken(refreshToken) ?: return null

        return transaction {
            val tokenHash = hashToken(refreshToken)

            // Verify the refresh token exists in DB
            val storedToken = AdminRefreshTokensTable.selectAll()
                .where { AdminRefreshTokensTable.tokenHash eq tokenHash }
                .firstOrNull() ?: return@transaction null

            // Check if expired
            if (storedToken[AdminRefreshTokensTable.expiresAt] < Clock.System.now()) {
                AdminRefreshTokensTable.deleteWhere { AdminRefreshTokensTable.tokenHash eq tokenHash }
                return@transaction null
            }

            // Verify admin is still active
            val admin = AdminUsersTable.selectAll()
                .where { AdminUsersTable.id eq UUID.fromString(adminId) }
                .firstOrNull() ?: return@transaction null

            if (!admin[AdminUsersTable.active]) return@transaction null

            // Delete old refresh token (rotate)
            AdminRefreshTokensTable.deleteWhere { AdminRefreshTokensTable.tokenHash eq tokenHash }

            // Generate new tokens
            val email = admin[AdminUsersTable.email]
            val adminRole = admin[AdminUsersTable.role]
            val newAccessToken = adminJwtConfig.generateToken(adminId, email, adminRole)
            val newRefreshToken = adminJwtConfig.generateRefreshToken(adminId)

            // Store new refresh token
            AdminRefreshTokensTable.insert {
                it[AdminRefreshTokensTable.adminId] = admin[AdminUsersTable.id]
                it[AdminRefreshTokensTable.tokenHash] = hashToken(newRefreshToken)
                it[expiresAt] = Clock.System.now().plus(adminJwtConfig.refreshExpireDays.days)
                it[createdAt] = Clock.System.now()
            }

            TokenPair(token = newAccessToken, refreshToken = newRefreshToken)
        }
    }

    fun invalidateAllTokens(adminId: String) {
        transaction {
            AdminRefreshTokensTable.deleteWhere {
                AdminRefreshTokensTable.adminId eq UUID.fromString(adminId)
            }
        }
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
