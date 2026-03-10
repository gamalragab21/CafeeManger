package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.CustomerDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.CustomerRepository
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.CustomerAddress
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.PointsTransaction
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateCustomerAddressRequest
import net.marllex.waselak.core.network.dto.CreateCustomerRequest
import net.marllex.waselak.core.network.dto.UpdateCustomerRequest
import net.marllex.waselak.core.network.mapper.toDomain

class CustomerRepositoryImpl(
    private val api: WaselakApiClient,
    private val customerDao: CustomerDao,
    private val authRepository: AuthRepository,
) : CustomerRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    // ─── Reactive reads ──────────────────────────────────────────

    override fun getCustomers(): Flow<List<Customer>> =
        customerDao.getCustomers(vendorId).map { list ->
            list.map { customer ->
                val addresses = customerDao.getAddressesListByCustomerId(customer.id)
                customer.toDomain(addresses.map { it.toDomain() })
            }
        }

    override fun getCustomerById(id: String): Flow<Customer?> =
        customerDao.getCustomerById(id).map { entity ->
            entity?.let { customer ->
                val addresses = customerDao.getAddressesListByCustomerId(customer.id)
                customer.toDomain(addresses.map { it.toDomain() })
            }
        }

    override fun getCustomerByPhone(phone: String): Flow<Customer?> =
        customerDao.getCustomerByPhone(vendorId, phone).map { entity ->
            entity?.let { customer ->
                val addresses = customerDao.getAddressesListByCustomerId(customer.id)
                customer.toDomain(addresses.map { it.toDomain() })
            }
        }

    override fun searchCustomers(query: String): Flow<List<Customer>> =
        customerDao.searchCustomers(vendorId, query).map { list ->
            list.map { customer ->
                val addresses = customerDao.getAddressesListByCustomerId(customer.id)
                customer.toDomain(addresses.map { it.toDomain() })
            }
        }

    // ─── Network refresh ─────────────────────────────────────────

    override suspend fun refreshCustomers(): Result<List<Customer>> = runCatching {
        AppLogger.d(TAG, "Refreshing customers")
        val response = api.getCustomers()
        val customers = response.map { it.toDomain() }
        // Clear and re-insert
        customerDao.deleteAllCustomers(vendorId)
        customerDao.insertCustomers(customers.map { it.toDbEntity() })
        // Insert addresses for each customer
        customers.forEach { customer ->
            customerDao.deleteAddressesByCustomerId(customer.id)
            customerDao.insertAddresses(customer.addresses.map { it.toDbEntity() })
        }
        AppLogger.i(TAG, "Fetched ${customers.size} customers")
        customers
    }

    // ─── Phone lookup ────────────────────────────────────────────

    override suspend fun lookupCustomerByPhone(phone: String): Result<Customer?> = runCatching {
        AppLogger.d(TAG, "Looking up customer by phone")
        try {
            val response = api.getCustomerByPhone(phone)
            response?.let { dto ->
                val customer = dto.toDomain()
                customerDao.insertCustomer(customer.toDbEntity())
                customerDao.deleteAddressesByCustomerId(customer.id)
                customerDao.insertAddresses(customer.addresses.map { it.toDbEntity() })
                customer
            }
        } catch (_: Exception) {
            // Offline fallback: search local DB
            customerDao.getCustomerByPhone(vendorId, phone).firstOrNull()?.let { entity ->
                val addresses = customerDao.getAddressesListByCustomerId(entity.id)
                entity.toDomain(addresses.map { it.toDomain() })
            }
        }
    }

    // ─── CRUD ────────────────────────────────────────────────────

    override suspend fun createCustomer(
        phone: String,
        name: String?,
        notes: String?,
    ): Result<Customer> = runCatching {
        AppLogger.d(TAG, "Creating customer: phone=$phone")
        val response = api.createCustomer(
            CreateCustomerRequest(phone = phone, name = name, notes = notes)
        )
        val customer = response.toDomain()
        customerDao.insertCustomer(customer.toDbEntity())
        AppLogger.i(TAG, "Customer created: id=${customer.id}")
        customer
    }

    override suspend fun updateCustomer(
        id: String,
        name: String?,
        phone: String?,
        notes: String?,
    ): Result<Customer> = runCatching {
        AppLogger.d(TAG, "Updating customer: id=$id")
        val response = api.updateCustomer(
            id, UpdateCustomerRequest(name = name, phone = phone, notes = notes)
        )
        val customer = response.toDomain()
        customerDao.insertCustomer(customer.toDbEntity())
        customer
    }

    override suspend fun deleteCustomer(id: String): Result<Unit> = runCatching {
        AppLogger.d(TAG, "Deleting customer: id=$id")
        api.deleteCustomer(id)
        customerDao.deleteAddressesByCustomerId(id)
        customerDao.deleteCustomer(id)
        AppLogger.i(TAG, "Customer deleted: id=$id")
    }

    // ─── Addresses ───────────────────────────────────────────────

    override suspend fun getCustomerAddresses(customerId: String): Result<List<CustomerAddress>> =
        runCatching {
            try {
                val response = api.getCustomerAddresses(customerId)
                val addresses = response.map { it.toDomain() }
                customerDao.deleteAddressesByCustomerId(customerId)
                customerDao.insertAddresses(addresses.map { it.toDbEntity() })
                addresses
            } catch (_: Exception) {
                // Offline fallback: return cached addresses
                customerDao.getAddressesListByCustomerId(customerId).map { it.toDomain() }
            }
        }

    override suspend fun addAddress(
        customerId: String,
        address: String,
        label: String?,
        geoLat: Double?,
        geoLng: Double?,
        deliveryZoneId: String?,
        isDefault: Boolean,
    ): Result<CustomerAddress> = runCatching {
        AppLogger.d(TAG, "Adding address for customer=$customerId")
        val response = api.createCustomerAddress(
            customerId,
            CreateCustomerAddressRequest(
                label = label,
                address = address,
                geoLat = geoLat,
                geoLng = geoLng,
                deliveryZoneId = deliveryZoneId,
                isDefault = isDefault,
            )
        )
        val addr = response.toDomain()
        customerDao.insertAddress(addr.toDbEntity())
        AppLogger.i(TAG, "Address added: id=${addr.id}")
        addr
    }

    override suspend fun deleteAddress(customerId: String, addressId: String): Result<Unit> =
        runCatching {
            AppLogger.d(TAG, "Deleting address: id=$addressId")
            api.deleteCustomerAddress(customerId, addressId)
            customerDao.deleteAddress(addressId)
        }

    // ─── Order history ───────────────────────────────────────────

    override suspend fun getCustomerRecentOrders(
        customerId: String,
        limit: Int,
    ): Result<List<Order>> = runCatching {
        AppLogger.d(TAG, "Fetching recent orders for customer=$customerId")
        val response = api.getCustomerOrders(customerId, limit)
        response.orders.map { it.toDomain() }
    }

    // ─── Points & Discount history ─────────────────────────────

    override suspend fun getCustomerPointsHistory(
        customerId: String,
    ): Result<List<PointsTransaction>> = runCatching {
        AppLogger.d(TAG, "Fetching points history for customer=$customerId")
        api.getCustomerPointsHistory(customerId).map { it.toDomain() }
    }

    override suspend fun getCustomerDiscountOrders(
        customerId: String,
        limit: Int,
    ): Result<List<Order>> = runCatching {
        AppLogger.d(TAG, "Fetching discount orders for customer=$customerId")
        api.getCustomerDiscountOrders(customerId, limit).map { it.toDomain() }
    }

    private companion object {
        const val TAG = "CustomerRepo"
    }
}
