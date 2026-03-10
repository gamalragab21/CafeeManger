package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.Reservations
import net.marllex.waselak.core.database.WaselakDatabase

class ReservationDao(private val db: WaselakDatabase) {
    private val queries get() = db.reservationQueries

    fun getReservations(vendorId: String): Flow<List<Reservations>> =
        queries.getReservations(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getReservationsByTableId(vendorId: String, tableId: String): Flow<List<Reservations>> =
        queries.getReservationsByTableId(vendorId, tableId).asFlow().mapToList(Dispatchers.Default)

    fun getActiveReservationForTable(tableId: String): Flow<Reservations?> =
        queries.getActiveReservationForTable(tableId).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun insertReservations(reservations: List<Reservations>) {
        db.transaction {
            reservations.forEach { r ->
                queries.insertReservation(
                    id = r.id,
                    vendor_id = r.vendor_id,
                    table_id = r.table_id,
                    table_number = r.table_number,
                    client_name = r.client_name,
                    client_phone = r.client_phone,
                    reservation_date = r.reservation_date,
                    reservation_time = r.reservation_time,
                    number_of_guests = r.number_of_guests,
                    notes = r.notes,
                    status = r.status,
                    order_id = r.order_id,
                    created_by = r.created_by,
                    created_at = r.created_at,
                    updated_at = r.updated_at,
                )
            }
        }
    }

    suspend fun insertReservation(r: Reservations) {
        queries.insertReservation(
            id = r.id,
            vendor_id = r.vendor_id,
            table_id = r.table_id,
            table_number = r.table_number,
            client_name = r.client_name,
            client_phone = r.client_phone,
            reservation_date = r.reservation_date,
            reservation_time = r.reservation_time,
            number_of_guests = r.number_of_guests,
            notes = r.notes,
            status = r.status,
            order_id = r.order_id,
            created_by = r.created_by,
            created_at = r.created_at,
            updated_at = r.updated_at,
        )
    }

    suspend fun deleteReservation(id: String) {
        queries.deleteReservation(id)
    }

    suspend fun deleteAllReservations(vendorId: String) {
        queries.deleteAllReservations(vendorId)
    }
}
