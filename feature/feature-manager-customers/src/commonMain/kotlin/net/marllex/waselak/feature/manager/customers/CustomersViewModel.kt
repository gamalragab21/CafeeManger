package net.marllex.waselak.feature.manager.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.CustomerRepository
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.CustomerAddress
import net.marllex.waselak.core.model.Order

enum class CustomerSortBy {
    ORDER_COUNT_DESC,
    TOTAL_SPENT_DESC,
    LAST_ORDER_DESC,
    NAME_ASC,
}

class CustomersViewModel(
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    data class UiState(
        val customers: List<Customer> = emptyList(),
        val searchQuery: String = "",
        val sortBy: CustomerSortBy = CustomerSortBy.ORDER_COUNT_DESC,
        val selectedCustomer: Customer? = null,
        val selectedCustomerOrders: List<Order> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadCustomers()
    }

    fun loadCustomers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            customerRepository.refreshCustomers()
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }

            customerRepository.getCustomers().collect { customers ->
                _uiState.update { state ->
                    state.copy(
                        customers = sortAndFilter(customers, state.searchQuery, state.sortBy),
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { state ->
            state.copy(searchQuery = query)
        }
        viewModelScope.launch {
            if (query.isBlank()) {
                customerRepository.getCustomers().collect { customers ->
                    _uiState.update { state ->
                        state.copy(customers = sortAndFilter(customers, state.searchQuery, state.sortBy))
                    }
                }
            } else {
                customerRepository.searchCustomers(query).collect { customers ->
                    _uiState.update { state ->
                        state.copy(customers = sortAndFilter(customers, "", state.sortBy))
                    }
                }
            }
        }
    }

    fun setSortBy(sortBy: CustomerSortBy) {
        _uiState.update { state ->
            state.copy(
                sortBy = sortBy,
                customers = sortAndFilter(state.customers, state.searchQuery, sortBy),
            )
        }
    }

    fun selectCustomer(customer: Customer?) {
        _uiState.update { it.copy(selectedCustomer = customer, selectedCustomerOrders = emptyList()) }
        if (customer != null) {
            viewModelScope.launch {
                customerRepository.getCustomerRecentOrders(customer.id, limit = 5)
                    .onSuccess { orders ->
                        _uiState.update { it.copy(selectedCustomerOrders = orders) }
                    }
            }
        }
    }

    fun deleteCustomer(id: String) {
        viewModelScope.launch {
            customerRepository.deleteCustomer(id).onSuccess {
                _uiState.update { it.copy(selectedCustomer = null) }
            }
        }
    }

    private fun sortAndFilter(
        customers: List<Customer>,
        query: String,
        sortBy: CustomerSortBy,
    ): List<Customer> {
        val filtered = if (query.isBlank()) customers
        else customers.filter {
            it.phone.contains(query, ignoreCase = true) ||
                    it.name?.contains(query, ignoreCase = true) == true
        }
        return when (sortBy) {
            CustomerSortBy.ORDER_COUNT_DESC -> filtered.sortedByDescending { it.orderCount }
            CustomerSortBy.TOTAL_SPENT_DESC -> filtered.sortedByDescending { it.totalSpent }
            CustomerSortBy.LAST_ORDER_DESC -> filtered.sortedByDescending { it.lastOrderAt ?: 0L }
            CustomerSortBy.NAME_ASC -> filtered.sortedBy { it.name ?: it.phone }
        }
    }
}
