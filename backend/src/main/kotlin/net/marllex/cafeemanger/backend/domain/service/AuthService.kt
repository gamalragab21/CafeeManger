package net.marllex.cafeemanger.backend.domain.service

import kotlinx.datetime.Clock
import net.marllex.cafeemanger.backend.config.JwtConfig
import net.marllex.cafeemanger.backend.data.database.UsersTable
import net.marllex.cafeemanger.backend.data.database.VendorsTable
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

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

            val userId = user[UsersTable.id].toString()
            val vendorId = user[UsersTable.vendorId].toString()
            val role = user[UsersTable.role]

            AuthResult(
                accessToken = jwtConfig.generateAccessToken(userId, vendorId, role),
                refreshToken = jwtConfig.generateRefreshToken(userId),
                userId = userId,
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
            val user = UsersTable.selectAll()
                .where { UsersTable.id eq java.util.UUID.fromString(userId) }
                .firstOrNull() ?: throw NoSuchElementException("User not found")

            if (!user[UsersTable.active]) {
                throw IllegalStateException("Account is disabled")
            }

            val vendorId = user[UsersTable.vendorId].toString()
            val role = user[UsersTable.role]

            AuthResult(
                accessToken = jwtConfig.generateAccessToken(userId, vendorId, role),
                refreshToken = jwtConfig.generateRefreshToken(userId),
                userId = userId,
                vendorId = vendorId,
                role = role,
                name = user[UsersTable.name],
                phone = user[UsersTable.phone],
                email = user[UsersTable.email]
            )
        }
    }

    fun register(
        vendorName: String,
        vendorAddress: String,
        vendorPhone: String,
        managerName: String,
        managerPhone: String,
        managerEmail: String?,
        password: String
    ): AuthResult {
        return transaction {
            // Check global phone uniqueness (login queries by phone across all vendors)
            val existingUser = UsersTable.selectAll()
                .where { UsersTable.phone eq managerPhone }
                .firstOrNull()
            if (existingUser != null) {
                throw IllegalStateException("A user with this phone number already exists")
            }

            // Create Vendor
            val vendorId = VendorsTable.insertAndGetId {
                it[name] = vendorName
                it[address] = vendorAddress
                it[contactPhone] = vendorPhone
                it[createdAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }

            // Create Manager user
            val passwordHash = hashPassword(password)
            val userId = UsersTable.insertAndGetId {
                it[UsersTable.vendorId] = vendorId.value
                it[role] = "MANAGER"
                it[name] = managerName
                it[phone] = managerPhone
                it[email] = managerEmail
                it[UsersTable.passwordHash] = passwordHash
                it[active] = true
                it[createdAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }

            val userIdStr = userId.toString()
            val vendorIdStr = vendorId.toString()

            AuthResult(
                accessToken = jwtConfig.generateAccessToken(userIdStr, vendorIdStr, "MANAGER"),
                refreshToken = jwtConfig.generateRefreshToken(userIdStr),
                userId = userIdStr,
                vendorId = vendorIdStr,
                role = "MANAGER",
                name = managerName,
                phone = managerPhone,
                email = managerEmail
            )
        }
    }

    companion object {
        fun hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(12))
    }
}
