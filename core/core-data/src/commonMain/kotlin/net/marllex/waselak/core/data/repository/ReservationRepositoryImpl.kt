package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.ReservationDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.ReservationRepository
import net.marllex.waselak.core.model.Reservation
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateReservationRequest
import net.marllex.waselak.core.network.dto.UpdateReservationStatusRequest
import net.marllex.waselak.core.network.mapper.toDomain

class ReservationRepositoryImpl(
    private val api: WaselakApiClient,
    private val reservationDao: ReservationDao,
    private val authRepository: AuthRepository,
) : ReservationRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getReservations(): Flow<List<Reservation>> =
        reservationDao.getReservations(vendorId).map { list -> list.map { it.toDomain() } }

    override fun getReservationsForTable(tableId: String): Flow<List<Reservation>> =
        reservationDao.getReservationsByTableId(vendorId, tableId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshReservations(
        date: String?,
        tableId: String?,
        status: String?,
    ): Result<List<Reservation>> = runCatching {
        val response = api.getReservations(date = date, tableId = tableId, status = status)
        val reservations = response.map { it.toDomain() }
        reservationDao.deleteAllReservations(vendorId)
        reservationDao.insertReservations(reservations.map { it.toDbEntity() })
        reservations
    }

    override suspend fun createReservation(
        tableId: String,
        clientName: String,
        clientPhone: String?,
        reservationDate: String,
        reservationTime: String,
        numberOfGuests: Int,
        notes: String?,
    ): Result<Reservation> = runCatching {
        val response = api.createReservation(
            CreateReservationRequest(
                tableId = tableId,
                clientName = clientName,
                clientPhone = clientPhone,
                reservationDate = reservationDate,
                reservationTime = reservationTime,
                numberOfGuests = numberOfGuests,
                notes = notes,
            )
        )
        val reservation = response.toDomain()
        reservationDao.insertReservation(reservation.toDbEntity())
        reservation
    }

    override suspend fun updateReservationStatus(id: String, status: String): Result<Reservation> = runCatching {
        val response = api.updateReservationStatus(id, UpdateReservationStatusRequest(status))
        val reservation = response.toDomain()
        reservationDao.insertReservation(reservation.toDbEntity())
        reservation
    }

    override suspend fun deleteReservation(id: String): Result<Unit> = runCatching {
        api.deleteReservation(id)
        reservationDao.deleteReservation(id)
    }
}
