package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Reservation

interface ReservationRepository {
    fun getReservations(): Flow<List<Reservation>>
    fun getReservationsForTable(tableId: String): Flow<List<Reservation>>

    suspend fun refreshReservations(
        date: String? = null,
        tableId: String? = null,
        status: String? = null,
    ): Result<List<Reservation>>

    suspend fun createReservation(
        tableId: String,
        clientName: String,
        clientPhone: String?,
        reservationDate: String,
        reservationTime: String,
        numberOfGuests: Int,
        notes: String?,
    ): Result<Reservation>

    suspend fun updateReservationStatus(id: String, status: String): Result<Reservation>

    suspend fun deleteReservation(id: String): Result<Unit>
}
