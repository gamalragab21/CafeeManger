package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.plugins.routeTrace
import net.marllex.waselak.backend.data.database.CustomerAddressesTable
import net.marllex.waselak.backend.domain.service.PlanService
import org.koin.java.KoinJavaComponent
import net.marllex.waselak.backend.data.database.CustomersTable
import net.marllex.waselak.backend.data.database.OrderItemsTable
import net.marllex.waselak.backend.data.database.OrdersTable
import net.marllex.waselak.backend.data.database.PointsTransactionsTable
import net.marllex.waselak.backend.data.database.UsersTable
import net.marllex.waselak.backend.data.database.TablesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

// ─── DTOs ────────────────────────────────────────────────────────

@Serializable
data class CustomerDto(
    val id: String,
    val vendor_id: String,
    val name: String? = null,
    val phone: String,
    val notes: String? = null,
    val order_count: Int = 0,
    val total_spent: Double = 0.0,
    val points_balance: Int = 0,
    val last_order_at: Long? = null,
    val addresses: List<CustomerAddressDto> = emptyList(),
    val created_at: Long,
    val updated_at: Long? = null,
)

@Serializable
data class CustomerAddressDto(
    val id: String,
    val customer_id: String,
    val label: String? = null,
    val address: String,
    val geo_lat: Double? = null,
    val geo_lng: Double? = null,
    val delivery_zone_id: String? = null,
    val delivery_fee: Double? = null,
    val is_default: Boolean = false,
    val created_at: Long,
)

@Serializable
data class CreateCustomerDto(
    val phone: String,
    val name: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateCustomerDto(
    val name: String? = null,
    val phone: String? = null,
    val notes: String? = null,
)

@Serializable
data class CreateCustomerAddressDto(
    val label: String? = null,
    val address: String,
    val geo_lat: Double? = null,
    val geo_lng: Double? = null,
    val delivery_zone_id: String? = null,
    val is_default: Boolean = false,
)

@Serializable
data class CustomerOrderHistoryDto(
    val orders: List<OrderDto>,
    val total: Int,
)

@Serializable
data class PointsTransactionDto(
    val id: String,
    val customer_id: String,
    val vendor_id: String,
    val order_id: String? = null,
    val type: String,
    val points: Int,
    val description: String? = null,
    val created_at: Long,
)

// ─── Routes ──────────────────────────────────────────────────────

fun Route.customerRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/customers") {

        // GET /api/v1/customers(?search=)
        get {
            val trace = call.routeTrace()
            trace.step("List customers started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "CUSTOMER")
            val search = call.parameters["search"]
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Parameters parsed", mapOf("search" to (search ?: "null")))

            val customers = transaction {
                var query = CustomersTable.selectAll()
                    .where { CustomersTable.vendorId eq vendorUUID }

                if (!search.isNullOrBlank()) {
                    query = query.andWhere {
                        (CustomersTable.phone like "%$search%") or
                        (CustomersTable.name like "%$search%")
                    }
                }

                query.orderBy(CustomersTable.orderCount, SortOrder.DESC)
                    .map { row ->
                        val customerId = row[CustomersTable.id].value
                        val addresses = CustomerAddressesTable.selectAll()
                            .where { CustomerAddressesTable.customerId eq customerId }
                            .map { it.toAddressDto() }
                        row.toCustomerDto(addresses)
                    }
            }
            trace.step("Customers fetched", mapOf("count" to customers.size.toString()))
            trace.step("List customers completed")
            call.respond(HttpStatusCode.OK, customers)
        }

        // GET /api/v1/customers/by-phone?phone=
        get("/by-phone") {
            val trace = call.routeTrace()
            trace.step("Get customer by phone started")
            val principal = currentUser()
            val phone = call.parameters["phone"]
                ?: throw IllegalArgumentException("phone parameter is required")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Phone parsed", mapOf("phone" to phone))

            val customer = transaction {
                CustomersTable.selectAll()
                    .where {
                        (CustomersTable.vendorId eq vendorUUID) and
                        (CustomersTable.phone eq phone)
                    }
                    .firstOrNull()
                    ?.let { row ->
                        val customerId = row[CustomersTable.id].value
                        val addresses = CustomerAddressesTable.selectAll()
                            .where { CustomerAddressesTable.customerId eq customerId }
                            .map { it.toAddressDto() }
                        row.toCustomerDto(addresses)
                    }
            }

            if (customer != null) {
                trace.step("Customer found by phone", mapOf("customerId" to customer.id, "name" to (customer.name ?: "null")))
                trace.step("Get customer by phone completed")
                call.respond(HttpStatusCode.OK, customer)
            } else {
                trace.step("Customer not found by phone", mapOf("phone" to phone))
                trace.step("Get customer by phone completed")
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Customer not found"))
            }
        }

        // GET /api/v1/customers/{id}
        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get customer by ID started")
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Customer ID parsed", mapOf("customerId" to id))

            val customer = transaction {
                CustomersTable.selectAll()
                    .where {
                        (CustomersTable.id eq UUID.fromString(id)) and
                        (CustomersTable.vendorId eq vendorUUID)
                    }
                    .firstOrNull()
                    ?.let { row ->
                        val customerId = row[CustomersTable.id].value
                        val addresses = CustomerAddressesTable.selectAll()
                            .where { CustomerAddressesTable.customerId eq customerId }
                            .map { it.toAddressDto() }
                        row.toCustomerDto(addresses)
                    }
                    ?: throw NoSuchElementException("Customer not found")
            }
            trace.step("Customer fetched", mapOf("customerId" to customer.id, "name" to (customer.name ?: "null"), "addressCount" to customer.addresses.size.toString()))
            trace.step("Get customer by ID completed")
            call.respond(HttpStatusCode.OK, customer)
        }

        // POST /api/v1/customers
        post {
            val trace = call.routeTrace()
            trace.step("Create customer started")
            val principal = currentUser()
            val request = call.receive<CreateCustomerDto>()
            require(request.phone.isNotBlank()) { "Phone is required" }
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Request parsed", mapOf("phone" to request.phone, "name" to (request.name ?: "null")))

            val customer = transaction {
                // Check if customer with same phone already exists for this vendor
                val existing = CustomersTable.selectAll()
                    .where {
                        (CustomersTable.vendorId eq vendorUUID) and
                        (CustomersTable.phone eq request.phone)
                    }
                    .firstOrNull()

                if (existing != null) {
                    throw IllegalStateException("PHONE_EXISTS:${existing[CustomersTable.name] ?: existing[CustomersTable.phone]}")
                }

                val now = Clock.System.now()
                val id = CustomersTable.insertAndGetId {
                    it[CustomersTable.vendorId] = vendorUUID
                    it[name] = request.name
                    it[phone] = request.phone
                    it[notes] = request.notes
                    it[orderCount] = 0
                    it[totalSpent] = java.math.BigDecimal.ZERO
                    it[createdAt] = now
                }
                CustomersTable.selectAll()
                    .where { CustomersTable.id eq id }
                    .first()
                    .toCustomerDto(emptyList())
            }
            trace.step("Customer created", mapOf("customerId" to customer.id, "phone" to customer.phone, "name" to (customer.name ?: "null")))
            trace.step("Create customer completed")
            call.respond(HttpStatusCode.Created, customer)
        }

        // PUT /api/v1/customers/{id}
        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update customer started")
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateCustomerDto>()
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerUUID = UUID.fromString(id)
            trace.step("Request parsed", mapOf("customerId" to id, "name" to (request.name ?: "null"), "phone" to (request.phone ?: "null")))

            val updated = transaction {
                CustomersTable.update({
                    (CustomersTable.id eq customerUUID) and
                    (CustomersTable.vendorId eq vendorUUID)
                }) { stmt ->
                    request.name?.let { stmt[name] = it }
                    request.phone?.let { stmt[phone] = it }
                    request.notes?.let { stmt[notes] = it }
                    stmt[updatedAt] = Clock.System.now()
                }
                val row = CustomersTable.selectAll()
                    .where { CustomersTable.id eq customerUUID }
                    .firstOrNull() ?: throw NoSuchElementException("Customer not found")
                val addresses = CustomerAddressesTable.selectAll()
                    .where { CustomerAddressesTable.customerId eq customerUUID }
                    .map { it.toAddressDto() }
                row.toCustomerDto(addresses)
            }
            trace.step("Customer updated", mapOf("customerId" to updated.id, "addressCount" to updated.addresses.size.toString()))
            trace.step("Update customer completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        // DELETE /api/v1/customers/{id}
        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete customer started")
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val vendorUUID = UUID.fromString(principal.vendorId)
            trace.step("Customer ID parsed", mapOf("customerId" to id))

            transaction {
                // Addresses will cascade-delete via FK
                val deleted = CustomersTable.deleteWhere {
                    (CustomersTable.id eq UUID.fromString(id)) and
                    (vendorId eq vendorUUID)
                }
                if (deleted == 0) throw NoSuchElementException("Customer not found")
            }
            trace.step("Customer deleted", mapOf("customerId" to id))
            trace.step("Delete customer completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // ─── Customer Addresses ──────────────────────────────────

        // GET /api/v1/customers/{customerId}/addresses
        get("/{customerId}/addresses") {
            val trace = call.routeTrace()
            trace.step("List customer addresses started")
            val principal = currentUser()
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerUUID = UUID.fromString(customerId)
            trace.step("Customer ID parsed", mapOf("customerId" to customerId))

            val addresses = transaction {
                // Verify customer belongs to vendor
                val exists = CustomersTable.selectAll()
                    .where {
                        (CustomersTable.id eq customerUUID) and
                        (CustomersTable.vendorId eq vendorUUID)
                    }.count() > 0
                if (!exists) throw NoSuchElementException("Customer not found")

                CustomerAddressesTable.selectAll()
                    .where { CustomerAddressesTable.customerId eq customerUUID }
                    .orderBy(CustomerAddressesTable.isDefault, SortOrder.DESC)
                    .map { it.toAddressDto() }
            }
            trace.step("Addresses fetched", mapOf("customerId" to customerId, "count" to addresses.size.toString()))
            trace.step("List customer addresses completed")
            call.respond(HttpStatusCode.OK, addresses)
        }

        // POST /api/v1/customers/{customerId}/addresses
        post("/{customerId}/addresses") {
            val trace = call.routeTrace()
            trace.step("Create customer address started")
            val principal = currentUser()
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val request = call.receive<CreateCustomerAddressDto>()
            require(request.address.isNotBlank()) { "Address is required" }
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerUUID = UUID.fromString(customerId)
            trace.step("Request parsed", mapOf("customerId" to customerId, "label" to (request.label ?: "null"), "isDefault" to request.is_default.toString()))

            val address = transaction {
                // Verify customer belongs to vendor
                val exists = CustomersTable.selectAll()
                    .where {
                        (CustomersTable.id eq customerUUID) and
                        (CustomersTable.vendorId eq vendorUUID)
                    }.count() > 0
                if (!exists) throw NoSuchElementException("Customer not found")

                // If new address is default, un-default all others
                if (request.is_default) {
                    CustomerAddressesTable.update({
                        CustomerAddressesTable.customerId eq customerUUID
                    }) {
                        it[isDefault] = false
                    }
                }

                val now = Clock.System.now()
                val id = CustomerAddressesTable.insertAndGetId {
                    it[CustomerAddressesTable.customerId] = customerUUID
                    it[label] = request.label
                    it[address] = request.address
                    it[geoLat] = request.geo_lat
                    it[geoLng] = request.geo_lng
                    it[deliveryZoneId] = request.delivery_zone_id
                    it[isDefault] = request.is_default
                    it[createdAt] = now
                }
                CustomerAddressesTable.selectAll()
                    .where { CustomerAddressesTable.id eq id }
                    .first()
                    .toAddressDto()
            }
            trace.step("Address created", mapOf("addressId" to address.id, "customerId" to customerId))
            trace.step("Create customer address completed")
            call.respond(HttpStatusCode.Created, address)
        }

        // DELETE /api/v1/customers/{customerId}/addresses/{addressId}
        delete("/{customerId}/addresses/{addressId}") {
            val trace = call.routeTrace()
            trace.step("Delete customer address started")
            val principal = currentUser()
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val addressId = call.parameters["addressId"]
                ?: throw IllegalArgumentException("Address ID required")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerUUID = UUID.fromString(customerId)
            trace.step("Parameters parsed", mapOf("customerId" to customerId, "addressId" to addressId))

            transaction {
                // Verify customer belongs to vendor
                val exists = CustomersTable.selectAll()
                    .where {
                        (CustomersTable.id eq customerUUID) and
                        (CustomersTable.vendorId eq vendorUUID)
                    }.count() > 0
                if (!exists) throw NoSuchElementException("Customer not found")

                val deleted = CustomerAddressesTable.deleteWhere {
                    (CustomerAddressesTable.id eq UUID.fromString(addressId)) and
                    (CustomerAddressesTable.customerId eq customerUUID)
                }
                if (deleted == 0) throw NoSuchElementException("Address not found")
            }
            trace.step("Address deleted", mapOf("customerId" to customerId, "addressId" to addressId))
            trace.step("Delete customer address completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // ─── Customer Points History ─────────────────────────────

        // GET /api/v1/customers/{customerId}/points?limit=50
        get("/{customerId}/points") {
            val trace = call.routeTrace()
            trace.step("Get customer points history started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "LOYALTY")
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerUUID = UUID.fromString(customerId)
            trace.step("Parameters parsed", mapOf("customerId" to customerId, "limit" to limit.toString()))

            val transactions = transaction {
                // Verify customer belongs to vendor
                val exists = CustomersTable.selectAll()
                    .where {
                        (CustomersTable.id eq customerUUID) and
                        (CustomersTable.vendorId eq vendorUUID)
                    }.count() > 0
                if (!exists) throw NoSuchElementException("Customer not found")

                PointsTransactionsTable.selectAll()
                    .where {
                        (PointsTransactionsTable.customerId eq customerUUID) and
                        (PointsTransactionsTable.vendorId eq vendorUUID)
                    }
                    .orderBy(PointsTransactionsTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        PointsTransactionDto(
                            id = row[PointsTransactionsTable.id].toString(),
                            customer_id = row[PointsTransactionsTable.customerId].toString(),
                            vendor_id = row[PointsTransactionsTable.vendorId].toString(),
                            order_id = row[PointsTransactionsTable.orderId]?.toString(),
                            type = row[PointsTransactionsTable.type],
                            points = row[PointsTransactionsTable.points],
                            description = row[PointsTransactionsTable.description],
                            created_at = row[PointsTransactionsTable.createdAt].toEpochMilliseconds(),
                        )
                    }
            }
            trace.step("Points history fetched", mapOf("customerId" to customerId, "count" to transactions.size.toString()))
            trace.step("Get customer points history completed")
            call.respond(HttpStatusCode.OK, transactions)
        }

        // ─── Customer Discount Orders ────────────────────────────

        // GET /api/v1/customers/{customerId}/discount-orders?limit=20
        get("/{customerId}/discount-orders") {
            val trace = call.routeTrace()
            trace.step("Get customer discount orders started")
            val principal = currentUser()
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerUUID = UUID.fromString(customerId)
            trace.step("Parameters parsed", mapOf("customerId" to customerId, "limit" to limit.toString()))

            val orders = transaction {
                // Verify customer belongs to vendor
                val exists = CustomersTable.selectAll()
                    .where {
                        (CustomersTable.id eq customerUUID) and
                        (CustomersTable.vendorId eq vendorUUID)
                    }.count() > 0
                if (!exists) throw NoSuchElementException("Customer not found")

                // Build user and table lookup maps
                val usersMap = UsersTable.selectAll()
                    .where { UsersTable.vendorId eq vendorUUID }
                    .associate { it[UsersTable.id].value to it[UsersTable.name] }

                val tablesMap = TablesTable.selectAll()
                    .where { TablesTable.vendorId eq vendorUUID }
                    .associate { it[TablesTable.id].value to it[TablesTable.number] }

                val orderRows = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.vendorId eq vendorUUID) and
                        (OrdersTable.customerId eq customerUUID) and
                        (OrdersTable.discount greater java.math.BigDecimal.ZERO)
                    }
                    .orderBy(OrdersTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .toList()

                orderRows.map { row ->
                    val orderId = row[OrdersTable.id].value
                    val items = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId eq orderId }
                        .map { itemRow ->
                            OrderItemDto(
                                id = itemRow[OrderItemsTable.id].toString(),
                                order_id = itemRow[OrderItemsTable.orderId].toString(),
                                item_id = itemRow[OrderItemsTable.itemId].toString(),
                                item_name_snapshot = itemRow[OrderItemsTable.itemNameSnapshot],
                                item_price_snapshot = itemRow[OrderItemsTable.itemPriceSnapshot].toDouble(),
                                quantity = itemRow[OrderItemsTable.quantity],
                                note = itemRow[OrderItemsTable.note],
                            )
                        }
                    OrderDto(
                        id = row[OrdersTable.id].toString(),
                        vendor_id = row[OrdersTable.vendorId].toString(),
                        channel = row[OrdersTable.channel],
                        status = row[OrdersTable.status],
                        table_id = row[OrdersTable.tableId]?.toString(),
                        table_number = row[OrdersTable.tableId]?.let { tablesMap[it.value] },
                        cashier_id = row[OrdersTable.cashierId].toString(),
                        cashier_name = usersMap[row[OrdersTable.cashierId].value],
                        delivery_user_id = row[OrdersTable.deliveryUserId]?.toString(),
                        delivery_user_name = row[OrdersTable.deliveryUserId]?.let { usersMap[it.value] },
                        customer_id = row[OrdersTable.customerId]?.toString(),
                        client_name = row[OrdersTable.clientName],
                        client_phone = row[OrdersTable.clientPhone],
                        client_address = row[OrdersTable.clientAddress],
                        geo_lat = row[OrdersTable.geoLat],
                        geo_lng = row[OrdersTable.geoLng],
                        payment_method = row[OrdersTable.paymentMethod],
                        subtotal = row[OrdersTable.subtotal].toDouble(),
                        delivery_fee = row[OrdersTable.deliveryFee].toDouble(),
                        discount = row[OrdersTable.discount].toDouble(),
                        discount_type = row[OrdersTable.discountType],
                        tax = row[OrdersTable.tax].toDouble(),
                        total = row[OrdersTable.total].toDouble(),
                        notes = row[OrdersTable.notes],
                        items = items,
                        discount_reason = row[OrdersTable.discountReason],
                        created_at = row[OrdersTable.createdAt].toEpochMilliseconds(),
                        updated_at = row[OrdersTable.updatedAt].toEpochMilliseconds(),
                    )
                }
            }
            trace.step("Discount orders fetched", mapOf("customerId" to customerId, "count" to orders.size.toString()))
            trace.step("Get customer discount orders completed")
            call.respond(HttpStatusCode.OK, orders)
        }

        // ─── Customer Order History ──────────────────────────────

        // GET /api/v1/customers/{customerId}/orders?limit=3
        get("/{customerId}/orders") {
            val trace = call.routeTrace()
            trace.step("Get customer order history started")
            val principal = currentUser()
            val customerId = call.parameters["customerId"]
                ?: throw IllegalArgumentException("Customer ID required")
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 3
            val vendorUUID = UUID.fromString(principal.vendorId)
            val customerUUID = UUID.fromString(customerId)
            trace.step("Parameters parsed", mapOf("customerId" to customerId, "limit" to limit.toString()))

            val result = transaction {
                // Verify customer belongs to vendor
                val exists = CustomersTable.selectAll()
                    .where {
                        (CustomersTable.id eq customerUUID) and
                        (CustomersTable.vendorId eq vendorUUID)
                    }.count() > 0
                if (!exists) throw NoSuchElementException("Customer not found")

                // Get orders by customer_id OR matching phone
                val customerPhone = CustomersTable.selectAll()
                    .where { CustomersTable.id eq customerUUID }
                    .first()[CustomersTable.phone]

                // Build user and table lookup maps
                val usersMap = UsersTable.selectAll()
                    .where { UsersTable.vendorId eq vendorUUID }
                    .associate { it[UsersTable.id].value to it[UsersTable.name] }

                val tablesMap = TablesTable.selectAll()
                    .where { TablesTable.vendorId eq vendorUUID }
                    .associate { it[TablesTable.id].value to it[TablesTable.number] }

                val orderRows = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.vendorId eq vendorUUID) and
                        (
                            (OrdersTable.customerId eq customerUUID) or
                            (OrdersTable.clientPhone eq customerPhone)
                        )
                    }
                    .orderBy(OrdersTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .toList()

                val orders = orderRows.map { row ->
                    val orderId = row[OrdersTable.id].value
                    val items = OrderItemsTable.selectAll()
                        .where { OrderItemsTable.orderId eq orderId }
                        .map { itemRow ->
                            OrderItemDto(
                                id = itemRow[OrderItemsTable.id].toString(),
                                order_id = itemRow[OrderItemsTable.orderId].toString(),
                                item_id = itemRow[OrderItemsTable.itemId].toString(),
                                item_name_snapshot = itemRow[OrderItemsTable.itemNameSnapshot],
                                item_price_snapshot = itemRow[OrderItemsTable.itemPriceSnapshot].toDouble(),
                                quantity = itemRow[OrderItemsTable.quantity],
                                note = itemRow[OrderItemsTable.note],
                            )
                        }
                    OrderDto(
                        id = row[OrdersTable.id].toString(),
                        vendor_id = row[OrdersTable.vendorId].toString(),
                        channel = row[OrdersTable.channel],
                        status = row[OrdersTable.status],
                        table_id = row[OrdersTable.tableId]?.toString(),
                        table_number = row[OrdersTable.tableId]?.let { tablesMap[it.value] },
                        cashier_id = row[OrdersTable.cashierId].toString(),
                        cashier_name = usersMap[row[OrdersTable.cashierId].value],
                        delivery_user_id = row[OrdersTable.deliveryUserId]?.toString(),
                        delivery_user_name = row[OrdersTable.deliveryUserId]?.let { usersMap[it.value] },
                        client_name = row[OrdersTable.clientName],
                        client_phone = row[OrdersTable.clientPhone],
                        client_address = row[OrdersTable.clientAddress],
                        geo_lat = row[OrdersTable.geoLat],
                        geo_lng = row[OrdersTable.geoLng],
                        payment_method = row[OrdersTable.paymentMethod],
                        subtotal = row[OrdersTable.subtotal].toDouble(),
                        delivery_fee = row[OrdersTable.deliveryFee].toDouble(),
                        tax = row[OrdersTable.tax].toDouble(),
                        total = row[OrdersTable.total].toDouble(),
                        notes = row[OrdersTable.notes],
                        items = items,
                        created_at = row[OrdersTable.createdAt].toEpochMilliseconds(),
                        updated_at = row[OrdersTable.updatedAt].toEpochMilliseconds(),
                    )
                }

                val totalCount = OrdersTable.selectAll()
                    .where {
                        (OrdersTable.vendorId eq vendorUUID) and
                        (
                            (OrdersTable.customerId eq customerUUID) or
                            (OrdersTable.clientPhone eq customerPhone)
                        )
                    }.count().toInt()

                CustomerOrderHistoryDto(orders = orders, total = totalCount)
            }
            trace.step("Order history fetched", mapOf("customerId" to customerId, "ordersReturned" to result.orders.size.toString(), "totalOrders" to result.total.toString()))
            trace.step("Get customer order history completed")
            call.respond(HttpStatusCode.OK, result)
        }
    }
}

// ─── Mappers ─────────────────────────────────────────────────────

private fun ResultRow.toCustomerDto(addresses: List<CustomerAddressDto> = emptyList()) = CustomerDto(
    id = this[CustomersTable.id].toString(),
    vendor_id = this[CustomersTable.vendorId].toString(),
    name = this[CustomersTable.name],
    phone = this[CustomersTable.phone],
    notes = this[CustomersTable.notes],
    order_count = this[CustomersTable.orderCount],
    total_spent = this[CustomersTable.totalSpent].toDouble(),
    points_balance = this[CustomersTable.pointsBalance],
    last_order_at = this[CustomersTable.lastOrderAt]?.toEpochMilliseconds(),
    addresses = addresses,
    created_at = this[CustomersTable.createdAt].toEpochMilliseconds(),
    updated_at = this[CustomersTable.updatedAt]?.toEpochMilliseconds(),
)

private fun ResultRow.toAddressDto() = CustomerAddressDto(
    id = this[CustomerAddressesTable.id].toString(),
    customer_id = this[CustomerAddressesTable.customerId].toString(),
    label = this[CustomerAddressesTable.label],
    address = this[CustomerAddressesTable.address],
    geo_lat = this[CustomerAddressesTable.geoLat],
    geo_lng = this[CustomerAddressesTable.geoLng],
    delivery_zone_id = this[CustomerAddressesTable.deliveryZoneId],
    delivery_fee = this[CustomerAddressesTable.deliveryFee]?.toDouble(),
    is_default = this[CustomerAddressesTable.isDefault],
    created_at = this[CustomerAddressesTable.createdAt].toEpochMilliseconds(),
)
