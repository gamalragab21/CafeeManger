package net.marllex.waselak.feature.manager.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.CustomerRepository
import net.marllex.waselak.core.domain.repository.InstallmentRepository
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.CustomerAddress
import net.marllex.waselak.core.model.InstallmentPlan
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.PointsTransaction
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import net.marllex.waselak.core.network.userFriendlyMessage
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

enum class CustomerSortBy {
    ORDER_COUNT_DESC,
    TOTAL_SPENT_DESC,
    LAST_ORDER_DESC,
    NAME_ASC,
}

enum class LoyaltyFilter { HAS_POINTS, NO_POINTS }

class CustomersViewModel(
    private val customerRepository: CustomerRepository,
    private val installmentRepository: InstallmentRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Customers" }


    data class UiState(
        val allCustomers: List<Customer> = emptyList(),
        val customers: List<Customer> = emptyList(),
        val searchQuery: String = "",
        val sortBy: CustomerSortBy = CustomerSortBy.ORDER_COUNT_DESC,
        val selectedCustomer: Customer? = null,
        val selectedCustomerOrders: List<Order> = emptyList(),
        val pointsHistory: List<PointsTransaction> = emptyList(),
        val discountOrders: List<Order> = emptyList(),
        val installmentPlans: List<InstallmentPlan> = emptyList(),
        val isLoadingPointsHistory: Boolean = false,
        val isLoadingDiscountOrders: Boolean = false,
        val isLoadingInstallments: Boolean = false,
        val loyaltyFilter: LoyaltyFilter? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        // Edit customer
        val showEditDialog: Boolean = false,
        val editName: String = "",
        val editPhone: String = "",
        val editNotes: String = "",
        val isSaving: Boolean = false,
        val showFeatureNotAvailable: Boolean = false,
        val featureNotAvailableMessage: String = "",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadCustomers()
    }

    fun loadCustomers() {
        AppLogger.d(TAG, "loadCustomers called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            customerRepository.refreshCustomers()
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e)
                    if (e.isFeatureNotAvailableOrOffline()) {
                        _uiState.update { it.copy(isLoading = false, showFeatureNotAvailable = true, featureNotAvailableMessage = e.message ?: "") }
                        return@launch
                    } else {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }

            customerRepository.getCustomers().collect { customers ->
                _uiState.update { state ->
                    state.copy(
                        allCustomers = customers,
                        customers = sortAndFilter(customers, state.searchQuery, state.sortBy, state.loyaltyFilter),
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun search(query: String) {
        AppLogger.d(TAG, "search called")
        _uiState.update { state ->
            state.copy(searchQuery = query)
        }
        viewModelScope.launch {
            if (query.isBlank()) {
                customerRepository.getCustomers().collect { customers ->
                    _uiState.update { state ->
                        state.copy(
                            allCustomers = customers,
                            customers = sortAndFilter(customers, state.searchQuery, state.sortBy, state.loyaltyFilter),
                        )
                    }
                }
            } else {
                customerRepository.searchCustomers(query).collect { customers ->
                    _uiState.update { state ->
                        state.copy(
                            allCustomers = customers,
                            customers = sortAndFilter(customers, "", state.sortBy, state.loyaltyFilter),
                        )
                    }
                }
            }
        }
    }

    fun setSortBy(sortBy: CustomerSortBy) {
        _uiState.update { state ->
            state.copy(
                sortBy = sortBy,
                customers = sortAndFilter(state.allCustomers, state.searchQuery, sortBy, state.loyaltyFilter),
            )
        }
    }

    fun selectCustomer(customer: Customer?) {
        AppLogger.d(TAG, "selectCustomer called")
        _uiState.update {
            it.copy(
                selectedCustomer = customer,
                selectedCustomerOrders = emptyList(),
                pointsHistory = emptyList(),
                discountOrders = emptyList(),
                installmentPlans = emptyList(),
            )
        }
        if (customer != null) {
            viewModelScope.launch {
                customerRepository.getCustomerRecentOrders(customer.id, limit = 5)
                    .onSuccess { orders ->
                    AppLogger.i(TAG, "Data loaded successfully")
                        _uiState.update { it.copy(selectedCustomerOrders = orders) }
                    }
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingPointsHistory = true) }
                customerRepository.getCustomerPointsHistory(customer.id)
                    .onSuccess { pts -> _uiState.update { it.copy(pointsHistory = pts, isLoadingPointsHistory = false) } }
                    .onFailure { _uiState.update { it.copy(isLoadingPointsHistory = false) } }
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingDiscountOrders = true) }
                customerRepository.getCustomerDiscountOrders(customer.id)
                    .onSuccess { orders -> _uiState.update { it.copy(discountOrders = orders, isLoadingDiscountOrders = false) } }
                    .onFailure { _uiState.update { it.copy(isLoadingDiscountOrders = false) } }
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingInstallments = true) }
                installmentRepository.getCustomerPlans(customer.id)
                    .onSuccess { plans -> _uiState.update { it.copy(installmentPlans = plans, isLoadingInstallments = false) } }
                    .onFailure { _uiState.update { it.copy(isLoadingInstallments = false) } }
            }
        }
    }

    fun setLoyaltyFilter(filter: LoyaltyFilter?) {
        _uiState.update { state ->
            state.copy(
                loyaltyFilter = filter,
                customers = sortAndFilter(state.allCustomers, state.searchQuery, state.sortBy, filter),
            )
        }
    }

    fun dismissFeatureNotAvailable() {
        _uiState.update { it.copy(showFeatureNotAvailable = false) }
    }

    fun deleteCustomer(id: String) {
        AppLogger.d(TAG, "deleteCustomer called")
        viewModelScope.launch {
            customerRepository.deleteCustomer(id).onSuccess {
                _uiState.update { it.copy(selectedCustomer = null) }
                loadCustomers()
            }
        }
    }

    // ── Edit Customer ──

    fun showEditDialog() {
        val customer = _uiState.value.selectedCustomer ?: return
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editName = customer.name ?: "",
                editPhone = customer.phone,
                editNotes = customer.notes ?: "",
            )
        }
    }

    fun dismissEditDialog() { _uiState.update { it.copy(showEditDialog = false) } }
    fun onEditName(v: String) { _uiState.update { it.copy(editName = v) } }
    fun onEditPhone(v: String) { _uiState.update { it.copy(editPhone = v.filter { c -> c.isDigit() || c == '+' }) } }
    fun onEditNotes(v: String) { _uiState.update { it.copy(editNotes = v) } }

    fun saveCustomer() {
        val s = _uiState.value
        val customer = s.selectedCustomer ?: return
        if (s.editName.isBlank() || s.editPhone.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            customerRepository.updateCustomer(
                id = customer.id,
                name = s.editName,
                phone = s.editPhone,
                notes = s.editNotes.ifBlank { null },
            ).onSuccess { updated ->
                AppLogger.i(TAG, "Customer updated")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showEditDialog = false,
                        selectedCustomer = updated,
                    )
                }
                loadCustomers()
            }.onFailure { e ->
                CrashReporter.captureException(e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    private fun sortAndFilter(
        customers: List<Customer>,
        query: String,
        sortBy: CustomerSortBy,
        loyaltyFilter: LoyaltyFilter? = null,
    ): List<Customer> {
        val searchFiltered = if (query.isBlank()) customers
        else customers.filter {
            it.phone.contains(query, ignoreCase = true) ||
                    it.name?.contains(query, ignoreCase = true) == true
        }

        // Apply loyalty filter
        val loyaltyFiltered = when (loyaltyFilter) {
            LoyaltyFilter.HAS_POINTS -> searchFiltered.filter { it.pointsBalance > 0 }
            LoyaltyFilter.NO_POINTS -> searchFiltered.filter { it.pointsBalance == 0 }
            null -> searchFiltered
        }

        return when (sortBy) {
            CustomerSortBy.ORDER_COUNT_DESC -> loyaltyFiltered.sortedByDescending { it.orderCount }
            CustomerSortBy.TOTAL_SPENT_DESC -> loyaltyFiltered.sortedByDescending { it.totalSpent }
            CustomerSortBy.LAST_ORDER_DESC -> loyaltyFiltered.sortedByDescending { it.lastOrderAt ?: 0L }
            CustomerSortBy.NAME_ASC -> loyaltyFiltered.sortedBy { it.name ?: it.phone }
        }
    }
}
