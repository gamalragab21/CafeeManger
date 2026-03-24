package net.marllex.waselak.feature.manager.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import net.marllex.waselak.core.domain.repository.CustomerRepository
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.ReservationRepository
import net.marllex.waselak.core.domain.repository.TableRepository
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.Reservation
import net.marllex.waselak.core.model.Table
import net.marllex.waselak.core.model.TableStatus
import net.marllex.waselak.core.common.extensions.currentTimeString
import net.marllex.waselak.core.common.extensions.todayDateString
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import net.marllex.waselak.core.common.logging.AppLogger

class TablesViewModel constructor(
    private val tableRepository: TableRepository,
    private val reservationRepository: ReservationRepository,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Tables" }


    data class UiState(
        val tables: List<Table> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val showAddDialog: Boolean = false,
        val editingTable: Table? = null,
        val dialogNumber: String = "",
        val dialogCapacity: String = "4",
        val isSaving: Boolean = false,
        val showFeatureNotAvailable: Boolean = false,
        val featureNotAvailableMessage: String = "",
        // Orders by table
        val ordersByTableId: Map<String, Order> = emptyMap(),
        val showOrderDetail: Boolean = false,
        val selectedOrder: Order? = null,
        // Reservations
        val reservationsByTableId: Map<String, Reservation> = emptyMap(),
        // Create reservation form
        val showReservationSheet: Boolean = false,
        val reservationTableId: String? = null,
        val reservationTableNumber: String = "",
        val reservationClientName: String = "",
        val reservationClientPhone: String = "",
        val reservationDate: String = "",
        val reservationTime: String = "",
        val reservationGuests: String = "1",
        val reservationNotes: String = "",
        val isSavingReservation: Boolean = false,
        // Customer search for reservation
        val customerSearchQuery: String = "",
        val customerSearchResults: List<Customer> = emptyList(),
        val selectedCustomer: Customer? = null,
        // Reservation detail view
        val showReservationDetail: Boolean = false,
        val selectedReservation: Reservation? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTables()
        loadReservations()
        loadOrders()
    }

    fun loadTables() {
        AppLogger.d(TAG, "loadTables called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                tableRepository.refreshTables().getOrThrow()
            } catch (e: Exception) {
                if (e.isFeatureNotAvailableOrOffline()) {
                    _uiState.update { it.copy(isLoading = false, showFeatureNotAvailable = true, featureNotAvailableMessage = e.message ?: "") }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                return@launch
            }
            tableRepository.getTables()
                .catch { e ->
                    if (e.isFeatureNotAvailableOrOffline()) {
                        _uiState.update { it.copy(isLoading = false, showFeatureNotAvailable = true, featureNotAvailableMessage = e.message ?: "") }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                }
                .collect { tables -> _uiState.update { it.copy(tables = tables, isLoading = false) } }
        }
    }

    private fun loadReservations() {
        viewModelScope.launch {
            try {
                reservationRepository.refreshReservations()
            } catch (_: Exception) {
                // Silent fail - reservations are supplementary
            }
            reservationRepository.getReservations()
                .catch { /* ignore */ }
                .collect { reservations ->
                    val map = reservations
                        .filter { it.status.name !in listOf("CANCELLED", "COMPLETED") }
                        .associateBy { it.tableId }
                    _uiState.update { it.copy(reservationsByTableId = map) }
                }
        }
    }

    private fun loadOrders() {
        viewModelScope.launch {
            try {
                orderRepository.refreshOrders(channel = "DINE_IN")
            } catch (_: Exception) {
                // Silent fail - orders are supplementary
            }
            orderRepository.getOrders(channel = "DINE_IN")
                .catch { /* ignore */ }
                .collect { orders ->
                    val map = orders
                        .filter { it.status.name !in listOf("COMPLETED", "CANCELED", "REFUNDED") }
                        .sortedByDescending { it.createdAt }
                        .associateBy { it.tableId ?: "" }
                    _uiState.update { it.copy(ordersByTableId = map) }
                }
        }
    }

    fun showOrderDetail(order: Order) {
        _uiState.update { it.copy(showOrderDetail = true, selectedOrder = order) }
    }

    fun dismissOrderDetail() {
        _uiState.update { it.copy(showOrderDetail = false, selectedOrder = null) }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingTable = null, dialogNumber = "", dialogCapacity = "4") }
    }

    fun showEditDialog(table: Table) {
        _uiState.update {
            it.copy(showAddDialog = true, editingTable = table, dialogNumber = table.number, dialogCapacity = table.capacity.toString())
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingTable = null) }
    }

    fun updateDialogNumber(v: String) { _uiState.update { it.copy(dialogNumber = v) } }
    fun updateDialogCapacity(v: String) { _uiState.update { it.copy(dialogCapacity = v) } }

    fun saveTable() {
        AppLogger.d(TAG, "saveTable called")
        val s = _uiState.value
        if (s.dialogNumber.isBlank()) return
        val capacity = s.dialogCapacity.toIntOrNull() ?: 4

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = if (s.editingTable != null) {
                tableRepository.updateTable(s.editingTable.id, s.dialogNumber, capacity)
            } else {
                tableRepository.createTable(s.dialogNumber, capacity)
            }
            result.onSuccess {
                    AppLogger.i(TAG, "Data loaded successfully")
                _uiState.update { it.copy(isSaving = false, showAddDialog = false) }
            }.onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateStatus(table: Table, newStatus: TableStatus) {
        if (table.status == newStatus) return  // no-op if same status
        viewModelScope.launch {
            tableRepository.updateTableStatus(table.id, newStatus.name)
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                    // Refresh to reset UI to actual server state
                    tableRepository.refreshTables()
                }
        }
    }

    fun dismissFeatureNotAvailable() {
        _uiState.update { it.copy(showFeatureNotAvailable = false) }
    }

    fun deleteTable(id: String) {
        AppLogger.d(TAG, "deleteTable called")
        viewModelScope.launch {
            tableRepository.deleteTable(id).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ─── Reservation Actions ──────────────────────────────────────

    private var customerSearchJob: Job? = null

    fun showReserveSheet(table: Table) {
        _uiState.update {
            it.copy(
                showReservationSheet = true,
                reservationTableId = table.id,
                reservationTableNumber = table.number,
                reservationClientName = "",
                reservationClientPhone = "",
                reservationDate = todayDateString(),
                reservationTime = currentTimeString(),
                reservationGuests = "1",
                reservationNotes = "",
                customerSearchQuery = "",
                customerSearchResults = emptyList(),
                selectedCustomer = null,
            )
        }
    }

    fun dismissReservationSheet() {
        _uiState.update {
            it.copy(
                showReservationSheet = false,
                reservationTableId = null,
                customerSearchQuery = "",
                customerSearchResults = emptyList(),
                selectedCustomer = null,
            )
        }
    }

    fun searchCustomer(query: String) {
        AppLogger.d(TAG, "searchCustomer called")
        _uiState.update { it.copy(customerSearchQuery = query, selectedCustomer = null) }
        customerSearchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(customerSearchResults = emptyList()) }
            return
        }
        customerSearchJob = viewModelScope.launch {
            delay(300) // debounce
            customerRepository.searchCustomers(query)
                .catch { /* ignore errors */ }
                .collect { customers ->
                    _uiState.update { it.copy(customerSearchResults = customers) }
                }
        }
    }

    fun selectCustomer(customer: Customer) {
        AppLogger.d(TAG, "selectCustomer called")
        _uiState.update {
            it.copy(
                selectedCustomer = customer,
                reservationClientName = customer.name ?: "",
                reservationClientPhone = customer.phone,
                customerSearchQuery = customer.phone,
                customerSearchResults = emptyList(),
            )
        }
    }

    fun clearSelectedCustomer() {
        _uiState.update {
            it.copy(
                selectedCustomer = null,
                reservationClientName = "",
                reservationClientPhone = "",
                customerSearchQuery = "",
                customerSearchResults = emptyList(),
            )
        }
    }

    fun updateReservationClientName(v: String) { _uiState.update { it.copy(reservationClientName = v) } }
    fun updateReservationClientPhone(v: String) { _uiState.update { it.copy(reservationClientPhone = v) } }
    fun updateReservationDate(v: String) { _uiState.update { it.copy(reservationDate = v) } }
    fun updateReservationTime(v: String) { _uiState.update { it.copy(reservationTime = v) } }
    fun updateReservationGuests(v: String) { _uiState.update { it.copy(reservationGuests = v) } }
    fun updateReservationNotes(v: String) { _uiState.update { it.copy(reservationNotes = v) } }

    fun saveReservation() {
        AppLogger.d(TAG, "saveReservation called")
        val s = _uiState.value
        if (s.reservationClientPhone.isBlank() || s.reservationDate.isBlank() || s.reservationTime.isBlank()) return
        val tableId = s.reservationTableId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingReservation = true) }
            reservationRepository.createReservation(
                tableId = tableId,
                clientName = s.reservationClientName.ifBlank { s.reservationClientPhone },
                clientPhone = s.reservationClientPhone,
                reservationDate = s.reservationDate,
                reservationTime = s.reservationTime,
                numberOfGuests = s.reservationGuests.toIntOrNull() ?: 1,
                notes = s.reservationNotes.ifBlank { null },
            ).onSuccess {
                _uiState.update { it.copy(isSavingReservation = false, showReservationSheet = false) }
                // Refresh tables to update status
                tableRepository.refreshTables()
                // Refresh reservations to update the map
                reservationRepository.refreshReservations()
            }.onFailure { e ->
                _uiState.update { it.copy(isSavingReservation = false, error = e.message) }
            }
        }
    }

    fun showReservationDetail(reservation: Reservation) {
        _uiState.update { it.copy(showReservationDetail = true, selectedReservation = reservation) }
    }

    fun dismissReservationDetail() {
        _uiState.update { it.copy(showReservationDetail = false, selectedReservation = null) }
    }

    fun cancelReservation(id: String) {
        AppLogger.d(TAG, "cancelReservation called")
        viewModelScope.launch {
            reservationRepository.updateReservationStatus(id, "CANCELLED")
                .onSuccess {
                    _uiState.update { it.copy(showReservationDetail = false, selectedReservation = null) }
                    tableRepository.refreshTables()
                    reservationRepository.refreshReservations()
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun completeReservation(id: String) {
        AppLogger.d(TAG, "completeReservation called")
        viewModelScope.launch {
            reservationRepository.updateReservationStatus(id, "COMPLETED")
                .onSuccess {
                    _uiState.update { it.copy(showReservationDetail = false, selectedReservation = null) }
                    tableRepository.refreshTables()
                    reservationRepository.refreshReservations()
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }
}
