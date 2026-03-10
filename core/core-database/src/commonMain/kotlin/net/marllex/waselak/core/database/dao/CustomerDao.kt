package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Customers
import net.marllex.waselak.core.database.Customer_addresses

class CustomerDao(private val db: WaselakDatabase) {
    private val customerQueries get() = db.customerQueries
    private val addressQueries get() = db.customerAddressQueries

    // ─── Customers ──────────────────────────────────────────────
    fun getCustomers(vendorId: String): Flow<List<Customers>> =
        customerQueries.getCustomers(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getCustomerById(id: String): Flow<Customers?> =
        customerQueries.getCustomerById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    fun getCustomerByPhone(vendorId: String, phone: String): Flow<Customers?> =
        customerQueries.getCustomerByPhone(vendorId, phone).asFlow().mapToOneOrNull(Dispatchers.Default)

    fun searchCustomers(vendorId: String, query: String): Flow<List<Customers>> =
        customerQueries.searchCustomers(vendorId, "%$query%", "%$query%").asFlow().mapToList(Dispatchers.Default)

    suspend fun insertCustomers(customers: List<Customers>) {
        db.transaction {
            customers.forEach { customer -> insertCustomerInternal(customer) }
        }
    }

    suspend fun insertCustomer(customer: Customers) {
        insertCustomerInternal(customer)
    }

    private fun insertCustomerInternal(customer: Customers) {
        customerQueries.insertCustomer(
            id = customer.id,
            vendor_id = customer.vendor_id,
            name = customer.name,
            phone = customer.phone,
            notes = customer.notes,
            order_count = customer.order_count,
            total_spent = customer.total_spent,
            points_balance = customer.points_balance,
            last_order_at = customer.last_order_at,
            created_at = customer.created_at,
            updated_at = customer.updated_at
        )
    }

    suspend fun deleteCustomer(id: String) {
        customerQueries.deleteCustomer(id)
    }

    suspend fun deleteAllCustomers(vendorId: String) {
        customerQueries.deleteAllCustomers(vendorId)
    }

    // ─── Customer Addresses ─────────────────────────────────────
    fun getAddressesByCustomerId(customerId: String): Flow<List<Customer_addresses>> =
        addressQueries.getAddressesByCustomerId(customerId).asFlow().mapToList(Dispatchers.Default)

    suspend fun getAddressesListByCustomerId(customerId: String): List<Customer_addresses> =
        addressQueries.getAddressesByCustomerId(customerId).executeAsList()

    fun getDefaultAddress(customerId: String): Flow<Customer_addresses?> =
        addressQueries.getDefaultAddress(customerId).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun insertAddresses(addresses: List<Customer_addresses>) {
        db.transaction {
            addresses.forEach { address -> insertAddressInternal(address) }
        }
    }

    suspend fun insertAddress(address: Customer_addresses) {
        insertAddressInternal(address)
    }

    private fun insertAddressInternal(address: Customer_addresses) {
        addressQueries.insertAddress(
            id = address.id,
            customer_id = address.customer_id,
            label = address.label,
            address = address.address,
            geo_lat = address.geo_lat,
            geo_lng = address.geo_lng,
            delivery_zone_id = address.delivery_zone_id,
            delivery_fee = address.delivery_fee,
            is_default = address.is_default,
            created_at = address.created_at
        )
    }

    suspend fun deleteAddress(id: String) {
        addressQueries.deleteAddress(id)
    }

    suspend fun deleteAddressesByCustomerId(customerId: String) {
        addressQueries.deleteAddressesByCustomerId(customerId)
    }
}
