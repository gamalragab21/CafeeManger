package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.User
import net.marllex.waselak.core.model.UserRole

interface UserManagementRepository {
    fun getUsers(role: UserRole? = null): Flow<List<User>>
    suspend fun refreshUsers(): Result<List<User>>
    suspend fun createUser(
        role: UserRole, name: String, phone: String,
        email: String?, password: String
    ): Result<User>
    suspend fun updateUser(
        id: String, name: String?, phone: String?,
        email: String?, active: Boolean?,
        role: String? = null, password: String? = null
    ): Result<User>
    suspend fun deleteUser(id: String): Result<Unit>
}
