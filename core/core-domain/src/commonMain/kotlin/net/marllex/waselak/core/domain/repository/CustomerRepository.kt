package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.CustomerAddress
import net.marllex.waselak.core.model.Order

interface CustomerRepository {
    // ─── Reactive reads (from local DB) ──────────────────────────
    fun getCustomers(): Flow<List<Customer>>
    fun getCustomerById(id: String): Flow<Customer?>
    fun getCustomerByPhone(phone: String): Flow<Customer?>
    fun searchCustomers(query: String): Flow<List<Customer>>

    // ─── Network refresh → local DB ─────────────────────────────
    suspend fun refreshCustomers(): Result<List<Customer>>

    // ─── Phone lookup (API call, returns null if not found) ─────
    suspend fun lookupCustomerByPhone(phone: String): Result<Customer?>

    // ─── CRUD ────────────────────────────────────────────────────
    suspend fun createCustomer(phone: String, name: String? = null, notes: String? = null): Result<Customer>
    suspend fun updateCustomer(id: String, name: String? = null, phone: String? = null, notes: String? = null): Result<Customer>
    suspend fun deleteCustomer(id: String): Result<Unit>

    // ─── Addresses ───────────────────────────────────────────────
    suspend fun getCustomerAddresses(customerId: String): Result<List<CustomerAddress>>
    suspend fun addAddress(
        customerId: String,
        address: String,
        label: String? = null,
        geoLat: Double? = null,
        geoLng: Double? = null,
        deliveryZoneId: String? = null,
        isDefault: Boolean = false,
    ): Result<CustomerAddress>
    suspend fun deleteAddress(customerId: String, addressId: String): Result<Unit>

    // ─── Order history ───────────────────────────────────────────
    suspend fun getCustomerRecentOrders(customerId: String, limit: Int = 3): Result<List<Order>>
}
