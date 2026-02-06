package net.marllex.cafeemanger.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.cafeemanger.core.database.dao.UserDao
import net.marllex.cafeemanger.core.database.mapper.toDomain
import net.marllex.cafeemanger.core.database.mapper.toEntity
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.domain.repository.UserManagementRepository
import net.marllex.cafeemanger.core.model.User
import net.marllex.cafeemanger.core.model.UserRole
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.CreateUserRequest
import net.marllex.cafeemanger.core.network.dto.UpdateUserRequest
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class UserManagementRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
    private val userDao: UserDao,
    private val authRepository: AuthRepository,
) : UserManagementRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getUsers(role: UserRole?): Flow<List<User>> =
        if (role != null) {
            userDao.getUsersByRole(vendorId, role.name).map { list -> list.map { it.toDomain() } }
        } else {
            userDao.getUsers(vendorId).map { list -> list.map { it.toDomain() } }
        }

    override suspend fun refreshUsers(): Result<List<User>> = runCatching {
        val response = api.getUsers()
        val users = response.map { it.toDomain() }
        userDao.deleteAllUsers(vendorId)
        userDao.insertUsers(users.map { it.toEntity() })
        users
    }

    override suspend fun createUser(
        role: UserRole, name: String, phone: String,
        email: String?, password: String
    ): Result<User> = runCatching {
        val response = api.createUser(CreateUserRequest(role.name, name, phone, email, password))
        val user = response.toDomain()
        userDao.insertUser(user.toEntity())
        user
    }

    override suspend fun updateUser(
        id: String, name: String?, phone: String?,
        email: String?, active: Boolean?
    ): Result<User> = runCatching {
        val response = api.updateUser(id, UpdateUserRequest(name, phone, email, active))
        val user = response.toDomain()
        userDao.insertUser(user.toEntity())
        user
    }

    override suspend fun deleteUser(id: String): Result<Unit> = runCatching {
        api.deleteUser(id)
        userDao.deleteUser(id)
    }
}
