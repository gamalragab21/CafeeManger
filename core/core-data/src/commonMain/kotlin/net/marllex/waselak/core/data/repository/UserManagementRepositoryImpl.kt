package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.UserDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.UserManagementRepository
import net.marllex.waselak.core.model.User
import net.marllex.waselak.core.model.UserRole
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.dto.CreateUserRequest
import net.marllex.waselak.core.network.dto.UpdateUserRequest
import net.marllex.waselak.core.network.mapper.toDomain

class UserManagementRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val userDao: UserDao,
    private val authRepository: AuthRepository,
) : UserManagementRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getUsers(role: UserRole?): Flow<List<User>> {
        AppLogger.d("UserRepo", "Reading users from local DB: role=${role?.name}")
        return if (role != null) {
            userDao.getUsersByRole(vendorId, role.name).map { list -> list.map { it.toDomain() } }
        } else {
            userDao.getUsers(vendorId).map { list -> list.map { it.toDomain() } }
        }
    }

    override suspend fun refreshUsers(): Result<List<User>> = runCatching {
        AppLogger.d("UserRepo", "Refreshing users from API")
        val response = api.getUsers()
        val users = response.map { it.toDomain() }
        AppLogger.d("UserRepo", "Saving ${users.size} users to local DB")
        userDao.deleteAllUsers(vendorId)
        userDao.insertUsers(users.map { it.toDbEntity() })
        AppLogger.i("UserRepo", "Fetched and saved ${users.size} users from API")
        users
    }.onFailure { e ->
        AppLogger.e("UserRepo", "Failed to refresh users", e)
    }

    override suspend fun createUser(
        role: UserRole, name: String, phone: String,
        email: String?, password: String
    ): Result<User> = runCatching {
        AppLogger.d("UserRepo", "Creating user: role=${role.name}, name=$name")
        val response = api.createUser(CreateUserRequest(role.name, name, phone, email, password))
        val user = response.toDomain()
        AppLogger.d("UserRepo", "Saving created user to local DB: id=${user.id}")
        userDao.insertUser(user.toDbEntity())
        AppLogger.i("UserRepo", "User created: id=${user.id}, role=${role.name}")
        user
    }.onFailure { e ->
        AppLogger.e("UserRepo", "Failed to create user: name=$name, role=${role.name}", e)
    }

    override suspend fun updateUser(
        id: String, name: String?, phone: String?,
        email: String?, active: Boolean?,
        role: String?, password: String?
    ): Result<User> = runCatching {
        AppLogger.d("UserRepo", "Updating user: id=$id")
        val response = api.updateUser(id, UpdateUserRequest(name = name, phone = phone, email = email, active = active, role = role, password = password))
        val user = response.toDomain()
        AppLogger.d("UserRepo", "Saving updated user to local DB: id=${user.id}")
        userDao.insertUser(user.toDbEntity())
        AppLogger.i("UserRepo", "User updated: id=${user.id}, active=$active, role=$role")
        user
    }.onFailure { e ->
        AppLogger.e("UserRepo", "Failed to update user: id=$id", e)
    }

    override suspend fun deleteUser(id: String): Result<Unit> = runCatching {
        AppLogger.d("UserRepo", "Deleting user: id=$id")
        api.deleteUser(id)
        AppLogger.d("UserRepo", "Removing user from local DB: id=$id")
        userDao.deleteUser(id)
        AppLogger.i("UserRepo", "User deleted: id=$id")
    }.onFailure { e ->
        AppLogger.e("UserRepo", "Failed to delete user: id=$id", e)
    }
}
