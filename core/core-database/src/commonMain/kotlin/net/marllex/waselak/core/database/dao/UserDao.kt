package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Users

class UserDao(private val db: WaselakDatabase) {
    private val queries get() = db.userQueries

    fun getUsers(vendorId: String): Flow<List<Users>> =
        queries.getUsers(vendorId).asFlow().mapToList(Dispatchers.IO)

    fun getUsersByRole(vendorId: String, role: String): Flow<List<Users>> =
        queries.getUsersByRole(vendorId, role).asFlow().mapToList(Dispatchers.IO)

    fun getUserById(id: String): Flow<Users?> =
        queries.getUserById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    suspend fun insertUsers(users: List<Users>) {
        db.transaction {
            users.forEach { user ->
                queries.insertUser(
                    id = user.id,
                    vendor_id = user.vendor_id,
                    role = user.role,
                    name = user.name,
                    phone = user.phone,
                    email = user.email,
                    active = user.active,
                    created_at = user.created_at
                )
            }
        }
    }

    suspend fun insertUser(user: Users) {
        queries.insertUser(
            id = user.id,
            vendor_id = user.vendor_id,
            role = user.role,
            name = user.name,
            phone = user.phone,
            email = user.email,
            active = user.active,
            created_at = user.created_at
        )
    }

    suspend fun deleteUser(id: String) {
        queries.deleteUser(id)
    }

    suspend fun deleteAllUsers(vendorId: String) {
        queries.deleteAllUsers(vendorId)
    }
}
