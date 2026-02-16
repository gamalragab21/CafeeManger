package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        customers
    }

    // ─── Phone lookup ────────────────────────────────────────────

    override suspend fun lookupCustomerByPhone(phone: String): Result<Customer?> = runCatching {
        val response = api.getCustomerByPhone(phone)
        response?.let { dto ->
            val customer = dto.toDomain()
            // Cache locally
            customerDao.insertCustomer(customer.toDbEntity())
            customerDao.deleteAddressesByCustomerId(customer.id)
            customerDao.insertAddresses(customer.addresses.map { it.toDbEntity() })
            customer
        }
    }

    // ─── CRUD ────────────────────────────────────────────────────

    override suspend fun createCustomer(
        phone: String,
        name: String?,
        notes: String?,
    ): Result<Customer> = runCatching {
        val response = api.createCustomer(
            CreateCustomerRequest(phone = phone, name = name, notes = notes)
        )
        val customer = response.toDomain()
        customerDao.insertCustomer(customer.toDbEntity())
        customer
    }

    override suspend fun updateCustomer(
        id: String,
        name: String?,
        phone: String?,
        notes: String?,
    ): Result<Customer> = runCatching {
        val response = api.updateCustomer(
            id, UpdateCustomerRequest(name = name, phone = phone, notes = notes)
        )
        val customer = response.toDomain()
        customerDao.insertCustomer(customer.toDbEntity())
        customer
    }

    override suspend fun deleteCustomer(id: String): Result<Unit> = runCatching {
        api.deleteCustomer(id)
        customerDao.deleteAddressesByCustomerId(id)
        customerDao.deleteCustomer(id)
    }

    // ─── Addresses ───────────────────────────────────────────────

    override suspend fun getCustomerAddresses(customerId: String): Result<List<CustomerAddress>> =
        runCatching {
            val response = api.getCustomerAddresses(customerId)
            val addresses = response.map { it.toDomain() }
            // Cache locally
            customerDao.deleteAddressesByCustomerId(customerId)
            customerDao.insertAddresses(addresses.map { it.toDbEntity() })
            addresses
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
        addr
    }

    override suspend fun deleteAddress(customerId: String, addressId: String): Result<Unit> =
        runCatching {
            api.deleteCustomerAddress(customerId, addressId)
            customerDao.deleteAddress(addressId)
        }

    // ─── Order history ───────────────────────────────────────────

    override suspend fun getCustomerRecentOrders(
        customerId: String,
        limit: Int,
    ): Result<List<Order>> = runCatching {
        val response = api.getCustomerOrders(customerId, limit)
        response.orders.map { it.toDomain() }
    }
}
