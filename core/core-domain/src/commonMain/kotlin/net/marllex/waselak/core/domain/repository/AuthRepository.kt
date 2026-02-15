package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.User

interface AuthRepository {
    val currentUser: Flow<User?>
    val isLoggedIn: Flow<Boolean>
    suspend fun login(phone: String, password: String): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun refreshToken(): Result<Unit>
    fun getCurrentUserId(): String?
    fun getCurrentVendorId(): String?
}
