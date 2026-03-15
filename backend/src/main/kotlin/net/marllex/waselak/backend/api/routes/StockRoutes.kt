package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.ItemsTable
import net.marllex.waselak.backend.data.database.StockBatchesTable
import net.marllex.waselak.backend.data.database.StockTable
import net.marllex.waselak.backend.data.database.StockTransactionsTable
import net.marllex.waselak.backend.domain.model.StockUnit
import net.marllex.waselak.backend.domain.service.PlanService
import net.marllex.waselak.backend.plugins.routeTrace
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import org.koin.java.KoinJavaComponent

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class StockDto(
    val id: String,
    val vendor_id: String,
    val item_id: String?, // Nullable for independent stock items
    val item_name: String,
    val quantity: Double,
    val min_quantity: Double,
    val cost_price: Double,
    val unit: String,
    val base_unit: String = "PIECE",
    val conversion_rate: Double = 1.0,
    val is_menu_item: Boolean = true,
    val alert_enabled: Boolean = true,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class CreateStockDto(
    val item_id: String? = null, // Optional - null for independent stock items
    val item_name: String? = null, // Required if item_id is null
    val quantity: Double,
    val min_quantity: Double = 5.0,
    val cost_price: Double = 0.0,
    val unit: String = "PIECE",
    val base_unit: String = "PIECE",
    val conversion_rate: Double = 1.0,
    val alert_enabled: Boolean = true,
)

@Serializable
data class UpdateStockDto(
    val item_name: String? = null,
    val quantity: Double? = null,
    val min_quantity: Double? = null,
    val cost_price: Double? = null,
    val unit: String? = null,
    val base_unit: String? = null,
    val conversion_rate: Double? = null,
    val alert_enabled: Boolean? = null,
)

@Serializable
data class AdjustQuantityDto(
    val quantity: Double,
    val note: String? = null,
)

@Serializable
data class TransferStockDto(
    val target_stock_id: String,
    val quantity: Double,
    val note: String? = null,
)

@Serializable
data class PurchaseStockDto(
    val quantity: Double,
    val unit: String, // Purchase unit (e.g., KILOGRAM) — will be converted to base unit
    val cost_price: Double? = null, // Update cost price if provided
    val note: String? = null,
)

@Serializable
data class StockTransactionDto(
    val id: String,
    val stock_id: String,
    val item_name: String? = null,
    val type: String,
    val quantity: Double,
    val previous_quantity: Double,
    val order_id: String? = null,
    val recipe_id: String? = null,
    val note: String? = null,
    val created_at: Long? = null,
)

@Serializable
data class StockAlertDto(
    val id: String,
    val item_name: String,
    val quantity: Double,
    val min_quantity: Double,
    val unit: String,
    val is_out_of_stock: Boolean,
    val is_menu_item: Boolean,
)

@Serializable
data class StockAnalyticsSummaryDto(
    val total_items: Int,
    val total_value: Double,
    val low_stock_count: Int,
    val out_of_stock_count: Int,
    val healthy_count: Int,
    val menu_items_count: Int,
    val independent_items_count: Int,
    val recipe_items_count: Int = 0,
    val total_transactions_today: Int,
    val total_added_today: Double,
    val total_deducted_today: Double,
)

// ─── Batch DTOs ─────────────────────────────────────────────────

@Serializable
data class StockBatchDto(
    val id: String,
    val stock_id: String,
    val vendor_id: String,
    val batch_number: String? = null,
    val quantity: Double,
    val initial_quantity: Double,
    val cost_price: Double,
    val expiry_date: String? = null,       // ISO date string (yyyy-MM-dd)
    val received_at: Long? = null,
    val status: String = "ACTIVE",
    val created_at: Long? = null,
    val item_name: String? = null,         // Denormalized for convenience
)

@Serializable
data class CreateBatchDto(
    val stock_id: String,
    val batch_number: String? = null,
    val quantity: Double,
    val cost_price: Double = 0.0,
    val expiry_date: String? = null,       // ISO date string (yyyy-MM-dd)
)

@Serializable
data class UpdateBatchDto(
    val batch_number: String? = null,
    val expiry_date: String? = null,
    val cost_price: Double? = null,
)

@Serializable
data class ExpiryAlertDto(
    val id: String,
    val stock_id: String,
    val item_name: String,
    val batch_number: String?,
    val quantity: Double,
    val expiry_date: String,               // ISO date string
    val days_until_expiry: Int,
    val status: String,                    // EXPIRING_SOON, EXPIRED
    val unit: String,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.stockRoutes() {
    val planService by KoinJavaComponent.inject<PlanService>(clazz = PlanService::class.java)

    route("/api/v1/stock") {

        // GET all stock for this vendor
        get {
            val trace = call.routeTrace()
            trace.step("List stock started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val stocks = transaction {
                StockTable.selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .orderBy(StockTable.itemName)
                    .map { it.toStockDto() }
            }
            trace.step("Stock items fetched", mapOf("count" to stocks.size.toString()))
            trace.step("List stock completed")
            call.respond(HttpStatusCode.OK, stocks)
        }

        // GET single stock item
        get("/{id}") {
            val trace = call.routeTrace()
            trace.step("Get stock item started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Stock ID parsed", mapOf("stockId" to id))

            val stock = transaction {
                StockTable.selectAll()
                    .where {
                        (StockTable.id eq UUID.fromString(id)) and
                        (StockTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toStockDto()
                    ?: throw NoSuchElementException("Stock item not found")
            }
            trace.step("Stock item found", mapOf("itemName" to stock.item_name, "quantity" to stock.quantity.toString(), "unit" to stock.unit))
            trace.step("Get stock item completed")
            call.respond(HttpStatusCode.OK, stock)
        }

        // GET stock by item ID
        get("/by-item/{itemId}") {
            val trace = call.routeTrace()
            trace.step("Get stock by item ID started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val itemId = call.parameters["itemId"] ?: throw IllegalArgumentException("Item ID required")
            trace.step("Item ID parsed", mapOf("itemId" to itemId))

            val stock = transaction {
                StockTable.selectAll()
                    .where {
                        (StockTable.itemId eq UUID.fromString(itemId)) and
                        (StockTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull()?.toStockDto()
            }
            if (stock != null) {
                trace.step("Stock found for item", mapOf("stockId" to stock.id, "itemName" to stock.item_name, "quantity" to stock.quantity.toString()))
                trace.step("Get stock by item ID completed")
                call.respond(HttpStatusCode.OK, stock)
            } else {
                trace.step("Stock not found for item", mapOf("itemId" to itemId))
                trace.step("Get stock by item ID completed")
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Stock not found for this item"))
            }
        }

        // CREATE stock item (MANAGER only)
        // Supports both menu-linked items (item_id provided) and independent items (item_name provided)
        post {
            val trace = call.routeTrace()
            trace.step("Create stock item started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val request = call.receive<CreateStockDto>()
            trace.step("Request parsed", mapOf(
                "itemId" to (request.item_id ?: "null"),
                "itemName" to (request.item_name ?: "null"),
                "quantity" to request.quantity.toString(),
                "unit" to request.unit,
                "minQuantity" to request.min_quantity.toString()
            ))
            require(request.quantity >= 0) { "Quantity must be non-negative" }

            // Validate: either item_id or item_name must be provided
            require(request.item_id != null || !request.item_name.isNullOrBlank()) {
                "Either item_id (for menu items) or item_name (for independent items) must be provided"
            }

            val isMenuLinkedReq = request.item_id != null
            trace.step("Stock mode determined", mapOf("isMenuLinked" to isMenuLinkedReq.toString()))

            val stock = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                // Smart baseUnit inference: derive from the selected unit's category base
                val parsedUnit = StockUnit.fromString(request.unit)
                val inferredBaseUnit = parsedUnit?.resolvedBaseUnit?.name ?: request.base_unit
                val inferredConversionRate = parsedUnit?.toBaseRate ?: request.conversion_rate

                val isMenuLinked = request.item_id != null
                val itemUUID = request.item_id?.let { UUID.fromString(it) }
                val finalItemName: String

                if (isMenuLinked && itemUUID != null) {
                    // Menu-linked stock item
                    val item = ItemsTable.selectAll()
                        .where {
                            (ItemsTable.id eq itemUUID) and
                            (ItemsTable.vendorId eq vendorUUID)
                        }.firstOrNull() ?: throw NoSuchElementException("Item not found")

                    // Check if stock already exists for this item
                    val existing = StockTable.selectAll()
                        .where {
                            (StockTable.itemId eq itemUUID) and
                            (StockTable.vendorId eq vendorUUID)
                        }.firstOrNull()
                    if (existing != null) throw IllegalStateException("Stock already exists for this item")

                    finalItemName = item[ItemsTable.name]

                    // Auto-set stockBehavior to DIRECT if not already set
                    if (item[ItemsTable.stockBehavior] == "NONE") {
                        ItemsTable.update({ ItemsTable.id eq itemUUID }) {
                            it[stockBehavior] = "DIRECT"
                            it[updatedAt] = now
                        }
                    }
                } else {
                    // Independent stock item (not linked to menu)
                    finalItemName = request.item_name!!

                    // Check for duplicate name in independent items
                    val existing = StockTable.selectAll()
                        .where {
                            (StockTable.vendorId eq vendorUUID) and
                            (StockTable.itemName eq finalItemName) and
                            (StockTable.itemId.isNull())
                        }.firstOrNull()
                    if (existing != null) throw IllegalStateException("Independent stock item with this name already exists")
                }

                val stockId = StockTable.insertAndGetId {
                    it[vendorId] = vendorUUID
                    it[StockTable.itemId] = itemUUID
                    it[itemName] = finalItemName
                    it[quantity] = BigDecimal.valueOf(request.quantity)
                    it[minQuantity] = BigDecimal.valueOf(request.min_quantity)
                    it[costPrice] = BigDecimal.valueOf(request.cost_price)
                    it[unit] = request.unit
                    it[baseUnit] = inferredBaseUnit
                    it[conversionRate] = BigDecimal.valueOf(inferredConversionRate)
                    it[isMenuItem] = isMenuLinked
                    it[alertEnabled] = request.alert_enabled
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                // Record initial transaction
                StockTransactionsTable.insertAndGetId {
                    it[StockTransactionsTable.stockId] = stockId
                    it[type] = "ADD"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(request.quantity)
                    it[previousQuantity] = BigDecimal.ZERO
                    it[note] = "Initial stock entry"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockId }.first().toStockDto()
            }
            trace.step("Stock item created", mapOf("stockId" to stock.id, "itemName" to stock.item_name, "quantity" to stock.quantity.toString(), "unit" to stock.unit))
            trace.step("Create stock item completed")
            call.respond(HttpStatusCode.Created, stock)
        }

        // UPDATE stock item (MANAGER only)
        put("/{id}") {
            val trace = call.routeTrace()
            trace.step("Update stock item started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Stock ID parsed", mapOf("stockId" to id))
            val request = call.receive<UpdateStockDto>()
            trace.step("Request parsed", mapOf(
                "quantity" to (request.quantity?.toString() ?: "null"),
                "minQuantity" to (request.min_quantity?.toString() ?: "null"),
                "unit" to (request.unit ?: "null"),
                "alertEnabled" to (request.alert_enabled?.toString() ?: "null")
            ))

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val now = Clock.System.now()
                val oldQty = current[StockTable.quantity]
                val isMenuItem = current[StockTable.isMenuItem]

                // Only allow item_name update for independent (non-menu) items
                if (request.item_name != null && isMenuItem) {
                    throw IllegalStateException("Cannot rename menu-linked stock items")
                }

                // Smart baseUnit inference: when unit changes, auto-update baseUnit
                val effectiveBaseUnit = request.unit?.let { newUnit ->
                    StockUnit.fromString(newUnit)?.resolvedBaseUnit?.name
                } ?: request.base_unit
                val effectiveConversionRate = request.unit?.let { newUnit ->
                    StockUnit.fromString(newUnit)?.toBaseRate
                } ?: request.conversion_rate

                StockTable.update({
                    (StockTable.id eq stockUUID) and
                    (StockTable.vendorId eq vendorUUID)
                }) { stmt ->
                    request.item_name?.let { stmt[itemName] = it }
                    request.quantity?.let { stmt[quantity] = BigDecimal.valueOf(it) }
                    request.min_quantity?.let { stmt[minQuantity] = BigDecimal.valueOf(it) }
                    request.cost_price?.let { stmt[costPrice] = BigDecimal.valueOf(it) }
                    request.unit?.let { stmt[unit] = it }
                    effectiveBaseUnit?.let { stmt[baseUnit] = it }
                    effectiveConversionRate?.let { stmt[conversionRate] = BigDecimal.valueOf(it) }
                    request.alert_enabled?.let { stmt[alertEnabled] = it }
                    stmt[updatedAt] = now
                }

                // Log quantity change
                val newQtyDecimal = request.quantity?.let { BigDecimal.valueOf(it) }
                if (newQtyDecimal != null && newQtyDecimal.compareTo(oldQty) != 0) {
                    StockTransactionsTable.insertAndGetId {
                        it[stockId] = stockUUID
                        it[type] = "ADJUST"
                        it[quantity] = newQtyDecimal
                        it[previousQuantity] = oldQty
                        it[note] = "Manual adjustment"
                        it[createdAt] = now
                    }
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            trace.step("Stock item updated", mapOf("stockId" to updated.id, "itemName" to updated.item_name, "quantity" to updated.quantity.toString()))
            trace.step("Update stock item completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        // ADD quantity (MANAGER only)
        patch("/{id}/add") {
            val trace = call.routeTrace()
            trace.step("Add stock quantity started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Stock ID parsed", mapOf("stockId" to id))
            val request = call.receive<AdjustQuantityDto>()
            trace.step("Request parsed", mapOf("addQuantity" to request.quantity.toString(), "note" to (request.note ?: "null")))
            require(request.quantity > 0) { "Quantity must be positive" }

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = current[StockTable.quantity]
                val newQty = oldQty + BigDecimal.valueOf(request.quantity)
                val now = Clock.System.now()

                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[quantity] = newQty
                    it[updatedAt] = now
                }

                StockTransactionsTable.insertAndGetId {
                    it[stockId] = stockUUID
                    it[type] = "ADD"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(request.quantity)
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Stock added"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            trace.step("Stock quantity added", mapOf("stockId" to updated.id, "previousQuantity" to (updated.quantity - request.quantity).toString(), "newQuantity" to updated.quantity.toString()))
            trace.step("Add stock quantity completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        // DEDUCT quantity (MANAGER/CASHIER)
        patch("/{id}/deduct") {
            val trace = call.routeTrace()
            trace.step("Deduct stock quantity started")
            val principal = requireRole("MANAGER", "CASHIER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Stock ID parsed", mapOf("stockId" to id))
            val request = call.receive<AdjustQuantityDto>()
            trace.step("Request parsed", mapOf("deductQuantity" to request.quantity.toString(), "note" to (request.note ?: "null")))
            require(request.quantity > 0) { "Quantity must be positive" }

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = current[StockTable.quantity]
                val newQty = (oldQty - BigDecimal.valueOf(request.quantity)).coerceAtLeast(BigDecimal.ZERO)
                val now = Clock.System.now()

                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[quantity] = newQty
                    it[updatedAt] = now
                }

                StockTransactionsTable.insertAndGetId {
                    it[stockId] = stockUUID
                    it[type] = "DEDUCT"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(request.quantity)
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Stock deducted"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            trace.step("Stock quantity deducted", mapOf("stockId" to updated.id, "newQuantity" to updated.quantity.toString()))
            trace.step("Deduct stock quantity completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        // WASTE — record stock wastage (MANAGER only)
        patch("/{id}/waste") {
            val trace = call.routeTrace()
            trace.step("Record stock waste started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Stock ID parsed", mapOf("stockId" to id))
            val request = call.receive<AdjustQuantityDto>()
            trace.step("Request parsed", mapOf("wasteQuantity" to request.quantity.toString(), "note" to (request.note ?: "null")))
            require(request.quantity > 0) { "Quantity must be positive" }

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = current[StockTable.quantity]
                val newQty = (oldQty - BigDecimal.valueOf(request.quantity)).coerceAtLeast(BigDecimal.ZERO)
                val now = Clock.System.now()

                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[quantity] = newQty
                    it[updatedAt] = now
                }

                StockTransactionsTable.insertAndGetId {
                    it[stockId] = stockUUID
                    it[type] = "WASTE"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(request.quantity)
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Stock waste recorded"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            trace.step("Stock waste recorded", mapOf("stockId" to updated.id, "wastedQuantity" to request.quantity.toString(), "newQuantity" to updated.quantity.toString()))
            trace.step("Record stock waste completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        // TRANSFER — move stock between stock items (MANAGER only)
        post("/{id}/transfer") {
            val trace = call.routeTrace()
            trace.step("Transfer stock started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Source stock ID required")
            val request = call.receive<TransferStockDto>()
            trace.step("Request parsed", mapOf("sourceStockId" to id, "targetStockId" to request.target_stock_id, "transferQuantity" to request.quantity.toString()))
            require(request.quantity > 0) { "Quantity must be positive" }

            val result = transaction {
                val sourceUUID = UUID.fromString(id)
                val targetUUID = UUID.fromString(request.target_stock_id)
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                val source = StockTable.selectAll()
                    .where {
                        (StockTable.id eq sourceUUID) and (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Source stock item not found")

                val target = StockTable.selectAll()
                    .where {
                        (StockTable.id eq targetUUID) and (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Target stock item not found")

                val transferQty = BigDecimal.valueOf(request.quantity)
                val sourceOldQty = source[StockTable.quantity]
                val targetOldQty = target[StockTable.quantity]
                val sourceNewQty = (sourceOldQty - transferQty).coerceAtLeast(BigDecimal.ZERO)
                val targetNewQty = targetOldQty + transferQty

                // Deduct from source
                StockTable.update({ StockTable.id eq sourceUUID }) {
                    it[quantity] = sourceNewQty
                    it[updatedAt] = now
                }
                StockTransactionsTable.insertAndGetId {
                    it[stockId] = sourceUUID
                    it[type] = "TRANSFER"
                    it[StockTransactionsTable.quantity] = transferQty
                    it[previousQuantity] = sourceOldQty
                    it[note] = request.note ?: "Transfer to ${target[StockTable.itemName]}"
                    it[createdAt] = now
                }

                // Add to target
                StockTable.update({ StockTable.id eq targetUUID }) {
                    it[quantity] = targetNewQty
                    it[updatedAt] = now
                }
                StockTransactionsTable.insertAndGetId {
                    it[stockId] = targetUUID
                    it[type] = "TRANSFER"
                    it[StockTransactionsTable.quantity] = transferQty
                    it[previousQuantity] = targetOldQty
                    it[note] = request.note ?: "Transfer from ${source[StockTable.itemName]}"
                    it[createdAt] = now
                }

                mapOf(
                    "source" to StockTable.selectAll().where { StockTable.id eq sourceUUID }.first().toStockDto(),
                    "target" to StockTable.selectAll().where { StockTable.id eq targetUUID }.first().toStockDto(),
                )
            }
            trace.step("Stock transferred", mapOf(
                "sourceStockId" to id,
                "targetStockId" to request.target_stock_id,
                "quantity" to request.quantity.toString()
            ))
            trace.step("Transfer stock completed")
            call.respond(HttpStatusCode.OK, result)
        }

        // PURCHASE — add stock with unit conversion (MANAGER only)
        post("/{id}/purchase") {
            val trace = call.routeTrace()
            trace.step("Purchase stock started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Stock ID parsed", mapOf("stockId" to id))
            val request = call.receive<PurchaseStockDto>()
            trace.step("Request parsed", mapOf(
                "purchaseQuantity" to request.quantity.toString(),
                "purchaseUnit" to request.unit,
                "costPrice" to (request.cost_price?.toString() ?: "null")
            ))
            require(request.quantity > 0) { "Quantity must be positive" }

            val updated = transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val current = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = current[StockTable.quantity]
                val stockDisplayUnit = current[StockTable.unit]
                val now = Clock.System.now()

                // Convert purchase quantity to stock's display unit
                val convertedQty = convertUnits(request.quantity, request.unit, stockDisplayUnit)
                val newQty = oldQty + BigDecimal.valueOf(convertedQty)

                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[quantity] = newQty
                    it[updatedAt] = now
                    // Update cost price if provided
                    request.cost_price?.let { cp -> it[costPrice] = BigDecimal.valueOf(cp) }
                }

                StockTransactionsTable.insertAndGetId {
                    it[stockId] = stockUUID
                    it[type] = "PURCHASE"
                    it[StockTransactionsTable.quantity] = BigDecimal.valueOf(convertedQty)
                    it[previousQuantity] = oldQty
                    it[note] = request.note ?: "Purchase: ${request.quantity} ${request.unit} → ${"%.3f".format(convertedQty)} $stockDisplayUnit"
                    it[createdAt] = now
                }

                StockTable.selectAll().where { StockTable.id eq stockUUID }.first().toStockDto()
            }
            trace.step("Stock purchased", mapOf("stockId" to updated.id, "newQuantity" to updated.quantity.toString(), "unit" to updated.unit))
            trace.step("Purchase stock completed")
            call.respond(HttpStatusCode.OK, updated)
        }

        // GET transactions for a stock item
        get("/{id}/transactions") {
            val trace = call.routeTrace()
            trace.step("Get stock transactions started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Stock ID parsed", mapOf("stockId" to id))

            val transactions = transaction {
                // Verify stock belongs to vendor
                StockTable.selectAll()
                    .where {
                        (StockTable.id eq UUID.fromString(id)) and
                        (StockTable.vendorId eq UUID.fromString(principal.vendorId))
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                StockTransactionsTable.selectAll()
                    .where { StockTransactionsTable.stockId eq UUID.fromString(id) }
                    .orderBy(StockTransactionsTable.createdAt, SortOrder.DESC)
                    .map { it.toStockTransactionDto() }
            }
            trace.step("Stock transactions fetched", mapOf("stockId" to id, "count" to transactions.size.toString()))
            trace.step("Get stock transactions completed")
            call.respond(HttpStatusCode.OK, transactions)
        }

        // DELETE stock item (MANAGER only)
        delete("/{id}") {
            val trace = call.routeTrace()
            trace.step("Delete stock item started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            trace.step("Stock ID parsed", mapOf("stockId" to id))

            transaction {
                val stockUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)

                // Delete transactions first
                val stock = StockTable.selectAll()
                    .where {
                        (StockTable.id eq stockUUID) and
                        (StockTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                StockTransactionsTable.deleteWhere { StockTransactionsTable.stockId eq stockUUID }
                StockTable.deleteWhere {
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }
            }
            trace.step("Stock item deleted", mapOf("stockId" to id))
            trace.step("Delete stock item completed")
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // ═══════════════════════════════════════════════════════════════
        // Stock Analytics Endpoints
        // ═══════════════════════════════════════════════════════════════

        // GET all transactions (with filters)
        get("/analytics/transactions") {
            val trace = call.routeTrace()
            trace.step("Get analytics transactions started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val stockId = call.parameters["stock_id"]?.let { UUID.fromString(it) }
            val type = call.parameters["type"] // ADD, DEDUCT, ADJUST, PURCHASE, SALE_DIRECT, SALE_RECIPE, RETURN, WASTE, TRANSFER
            val from = call.parameters["from"]?.toLongOrNull()
            val to = call.parameters["to"]?.toLongOrNull()
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 100
            trace.step("Query filters", mapOf(
                "stockId" to (stockId?.toString() ?: "null"),
                "type" to (type ?: "null"),
                "from" to (from?.toString() ?: "null"),
                "to" to (to?.toString() ?: "null"),
                "limit" to limit.toString()
            ))

            val transactions = transaction {
                // Get all stock IDs for this vendor
                val vendorStockIds = StockTable
                    .selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .map { it[StockTable.id] }

                if (vendorStockIds.isEmpty()) {
                    return@transaction emptyList<StockTransactionDto>()
                }

                // Create stock name lookup
                val stockNameMap = StockTable
                    .selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .associate { it[StockTable.id] to it[StockTable.itemName] }

                var query = StockTransactionsTable
                    .selectAll()
                    .where { StockTransactionsTable.stockId inList vendorStockIds }

                stockId?.let { sid ->
                    query = query.andWhere { StockTransactionsTable.stockId eq sid }
                }

                type?.let { t ->
                    query = query.andWhere { StockTransactionsTable.type eq t }
                }

                from?.let { ts ->
                    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(ts)
                    query = query.andWhere { StockTransactionsTable.createdAt greaterEq instant }
                }

                to?.let { ts ->
                    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(ts)
                    query = query.andWhere { StockTransactionsTable.createdAt lessEq instant }
                }

                query
                    .orderBy(StockTransactionsTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        StockTransactionDto(
                            id = row[StockTransactionsTable.id].toString(),
                            stock_id = row[StockTransactionsTable.stockId].toString(),
                            item_name = stockNameMap[row[StockTransactionsTable.stockId]],
                            type = row[StockTransactionsTable.type],
                            quantity = row[StockTransactionsTable.quantity].toDouble(),
                            previous_quantity = row[StockTransactionsTable.previousQuantity].toDouble(),
                            order_id = row[StockTransactionsTable.orderId]?.toString(),
                            recipe_id = row[StockTransactionsTable.recipeId]?.toString(),
                            note = row[StockTransactionsTable.note],
                            created_at = row[StockTransactionsTable.createdAt].toEpochMilliseconds(),
                        )
                    }
            }
            trace.step("Analytics transactions fetched", mapOf("count" to transactions.size.toString()))
            trace.step("Get analytics transactions completed")
            call.respond(HttpStatusCode.OK, transactions)
        }

        // GET low stock alerts
        get("/analytics/alerts") {
            val trace = call.routeTrace()
            trace.step("Get stock alerts started")
            val principal = currentUser()
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val alerts = transaction {
                StockTable
                    .selectAll()
                    .where {
                        (StockTable.vendorId eq vendorUUID) and
                        (StockTable.alertEnabled eq true) and
                        (StockTable.quantity lessEq StockTable.minQuantity)
                    }
                    .orderBy(StockTable.quantity, SortOrder.ASC)
                    .map { row ->
                        StockAlertDto(
                            id = row[StockTable.id].toString(),
                            item_name = row[StockTable.itemName],
                            quantity = row[StockTable.quantity].toDouble(),
                            min_quantity = row[StockTable.minQuantity].toDouble(),
                            unit = row[StockTable.unit],
                            is_out_of_stock = row[StockTable.quantity] <= BigDecimal.ZERO,
                            is_menu_item = row[StockTable.isMenuItem],
                        )
                    }
            }
            val outOfStockCount = alerts.count { it.is_out_of_stock }
            trace.step("Stock alerts fetched", mapOf("totalAlerts" to alerts.size.toString(), "outOfStock" to outOfStockCount.toString()))
            trace.step("Get stock alerts completed")
            call.respond(HttpStatusCode.OK, alerts)
        }

        // GET stock analytics summary
        get("/analytics/summary") {
            val trace = call.routeTrace()
            trace.step("Get stock analytics summary started")
            val principal = requireRole("MANAGER")
            trace.step("User authenticated", mapOf("userId" to principal.userId, "vendorId" to principal.vendorId))
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val vendorUUID = UUID.fromString(principal.vendorId)

            val summary = transaction {
                val stocks = StockTable
                    .selectAll()
                    .where { StockTable.vendorId eq vendorUUID }
                    .toList()

                val totalItems = stocks.size
                val totalValue = stocks.sumOf { it[StockTable.quantity].toDouble() * it[StockTable.costPrice].toDouble() }
                val lowStockCount = stocks.count {
                    val qty = it[StockTable.quantity]
                    val minQty = it[StockTable.minQuantity]
                    qty > BigDecimal.ZERO && qty <= minQty
                }
                val outOfStockCount = stocks.count { it[StockTable.quantity] <= BigDecimal.ZERO }
                val healthyCount = totalItems - lowStockCount - outOfStockCount
                val menuItemsCount = stocks.count { it[StockTable.isMenuItem] }
                val independentItemsCount = stocks.count { !it[StockTable.isMenuItem] }

                // Count recipe items (items with stockBehavior = RECIPE)
                val recipeItemsCount = ItemsTable.selectAll()
                    .where {
                        (ItemsTable.vendorId eq vendorUUID) and
                        (ItemsTable.stockBehavior eq "RECIPE")
                    }.count().toInt()

                // Get today's transactions
                val todayStart = kotlinx.datetime.Clock.System.now().let {
                    val str = it.toString().substring(0, 10)
                    kotlinx.datetime.Instant.parse("${str}T00:00:00Z")
                }

                val stockIds = stocks.map { it[StockTable.id] }
                val todayTransactions = if (stockIds.isEmpty()) emptyList() else {
                    StockTransactionsTable
                        .selectAll()
                        .where {
                            (StockTransactionsTable.stockId inList stockIds) and
                            (StockTransactionsTable.createdAt greaterEq todayStart)
                        }.toList()
                }

                val addTypes = setOf("ADD", "PURCHASE")
                val deductTypes = setOf("DEDUCT", "SALE_DIRECT", "SALE_RECIPE")

                val totalTransactionsToday = todayTransactions.size
                val totalAddedToday = todayTransactions.filter { it[StockTransactionsTable.type] in addTypes }
                    .sumOf { it[StockTransactionsTable.quantity].toDouble() }
                val totalDeductedToday = todayTransactions.filter { it[StockTransactionsTable.type] in deductTypes }
                    .sumOf { it[StockTransactionsTable.quantity].toDouble() }

                StockAnalyticsSummaryDto(
                    total_items = totalItems,
                    total_value = totalValue,
                    low_stock_count = lowStockCount,
                    out_of_stock_count = outOfStockCount,
                    healthy_count = healthyCount,
                    menu_items_count = menuItemsCount,
                    independent_items_count = independentItemsCount,
                    recipe_items_count = recipeItemsCount,
                    total_transactions_today = totalTransactionsToday,
                    total_added_today = totalAddedToday,
                    total_deducted_today = totalDeductedToday,
                )
            }
            trace.step("Stock analytics summary fetched", mapOf(
                "totalItems" to summary.total_items.toString(),
                "lowStockCount" to summary.low_stock_count.toString(),
                "outOfStockCount" to summary.out_of_stock_count.toString(),
                "transactionsToday" to summary.total_transactions_today.toString()
            ))
            trace.step("Get stock analytics summary completed")
            call.respond(HttpStatusCode.OK, summary)
        }

        // ═══════════════════════════════════════════════════════════════
        // Batch / Expiry Tracking Endpoints
        // ═══════════════════════════════════════════════════════════════

        // GET all batches for a stock item
        get("/{id}/batches") {
            val trace = call.routeTrace()
            trace.step("List batches started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val stockId = call.parameters["id"] ?: throw IllegalArgumentException("Stock ID required")
            val includeEmpty = call.parameters["include_empty"]?.toBoolean() ?: false

            val batches = transaction {
                val stockUUID = UUID.fromString(stockId)
                val vendorUUID = UUID.fromString(principal.vendorId)

                // Verify stock belongs to vendor
                StockTable.selectAll().where {
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                var query = StockBatchesTable.selectAll().where {
                    (StockBatchesTable.stockId eq stockUUID) and
                    (StockBatchesTable.vendorId eq vendorUUID)
                }
                if (!includeEmpty) {
                    query = query.andWhere { StockBatchesTable.quantity greater BigDecimal.ZERO }
                }

                query.orderBy(StockBatchesTable.receivedAt, SortOrder.ASC)
                    .map { it.toBatchDto() }
            }
            trace.step("Batches listed", mapOf("count" to batches.size.toString()))
            call.respond(HttpStatusCode.OK, batches)
        }

        // CREATE a new batch (receiving stock)
        post("/{id}/batches") {
            val trace = call.routeTrace()
            trace.step("Create batch started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val stockId = call.parameters["id"] ?: throw IllegalArgumentException("Stock ID required")
            val request = call.receive<CreateBatchDto>()
            require(request.quantity > 0) { "Quantity must be positive" }

            val batch = transaction {
                val stockUUID = UUID.fromString(stockId)
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                // Verify stock belongs to vendor
                val stock = StockTable.selectAll().where {
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Stock item not found")

                val oldQty = stock[StockTable.quantity]
                val addQty = BigDecimal.valueOf(request.quantity)

                // Parse expiry date
                val expiryDate = request.expiry_date?.let {
                    kotlinx.datetime.LocalDate.parse(it)
                }

                // Create batch
                val batchId = StockBatchesTable.insertAndGetId {
                    it[StockBatchesTable.stockId] = stockUUID
                    it[StockBatchesTable.vendorId] = vendorUUID
                    it[batchNumber] = request.batch_number
                    it[quantity] = addQty
                    it[initialQuantity] = addQty
                    it[costPrice] = BigDecimal.valueOf(request.cost_price)
                    it[StockBatchesTable.expiryDate] = expiryDate
                    it[receivedAt] = now
                    it[status] = "ACTIVE"
                    it[createdAt] = now
                }

                // Also add to main stock quantity
                StockTable.update({
                    (StockTable.id eq stockUUID) and (StockTable.vendorId eq vendorUUID)
                }) {
                    it[StockTable.quantity] = oldQty + addQty
                    it[updatedAt] = now
                    // Update cost price to batch cost price if provided
                    if (request.cost_price > 0) {
                        it[StockTable.costPrice] = BigDecimal.valueOf(request.cost_price)
                    }
                }

                // Record transaction
                StockTransactionsTable.insertAndGetId {
                    it[StockTransactionsTable.stockId] = stockUUID
                    it[StockTransactionsTable.batchId] = batchId
                    it[type] = "ADD"
                    it[StockTransactionsTable.quantity] = addQty
                    it[previousQuantity] = oldQty
                    it[note] = "Batch received: ${request.batch_number ?: "no lot#"}" +
                        (expiryDate?.let { d -> " | Expires: $d" } ?: "")
                    it[StockTransactionsTable.createdAt] = now
                }

                StockBatchesTable.selectAll().where {
                    StockBatchesTable.id eq batchId
                }.first().toBatchDto()
            }
            trace.step("Batch created", mapOf("batchId" to batch.id, "quantity" to batch.quantity.toString()))
            call.respond(HttpStatusCode.Created, batch)
        }

        // UPDATE batch metadata (lot number, expiry date, cost)
        put("/batches/{batchId}") {
            val trace = call.routeTrace()
            trace.step("Update batch started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val batchId = call.parameters["batchId"] ?: throw IllegalArgumentException("Batch ID required")
            val request = call.receive<UpdateBatchDto>()

            val updated = transaction {
                val batchUUID = UUID.fromString(batchId)
                val vendorUUID = UUID.fromString(principal.vendorId)

                val batch = StockBatchesTable.selectAll().where {
                    (StockBatchesTable.id eq batchUUID) and (StockBatchesTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Batch not found")

                StockBatchesTable.update({
                    (StockBatchesTable.id eq batchUUID) and (StockBatchesTable.vendorId eq vendorUUID)
                }) {
                    request.batch_number?.let { bn -> it[batchNumber] = bn }
                    request.expiry_date?.let { ed ->
                        it[expiryDate] = kotlinx.datetime.LocalDate.parse(ed)
                    }
                    request.cost_price?.let { cp -> it[costPrice] = BigDecimal.valueOf(cp) }
                }

                StockBatchesTable.selectAll().where {
                    StockBatchesTable.id eq batchUUID
                }.first().toBatchDto()
            }
            trace.step("Batch updated", mapOf("batchId" to updated.id))
            call.respond(HttpStatusCode.OK, updated)
        }

        // DELETE a batch (dispose / write-off)
        delete("/batches/{batchId}") {
            val trace = call.routeTrace()
            trace.step("Delete batch started")
            val principal = requireRole("MANAGER")
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val batchId = call.parameters["batchId"] ?: throw IllegalArgumentException("Batch ID required")

            transaction {
                val batchUUID = UUID.fromString(batchId)
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                val batch = StockBatchesTable.selectAll().where {
                    (StockBatchesTable.id eq batchUUID) and (StockBatchesTable.vendorId eq vendorUUID)
                }.firstOrNull() ?: throw NoSuchElementException("Batch not found")

                val remainingQty = batch[StockBatchesTable.quantity]
                val stockUUID = batch[StockBatchesTable.stockId]

                // Deduct remaining quantity from main stock
                if (remainingQty > BigDecimal.ZERO) {
                    val stock = StockTable.selectAll().where {
                        StockTable.id eq stockUUID
                    }.first()
                    val oldQty = stock[StockTable.quantity]
                    val newQty = (oldQty - remainingQty).coerceAtLeast(BigDecimal.ZERO)

                    StockTable.update({ StockTable.id eq stockUUID }) {
                        it[quantity] = newQty
                        it[updatedAt] = now
                    }

                    StockTransactionsTable.insertAndGetId {
                        it[stockId] = stockUUID
                        it[StockTransactionsTable.batchId] = batchUUID
                        it[type] = "WASTE"
                        it[StockTransactionsTable.quantity] = remainingQty
                        it[previousQuantity] = oldQty
                        it[note] = "Batch disposed: ${batch[StockBatchesTable.batchNumber] ?: batchId}"
                        it[createdAt] = now
                    }
                }

                // Mark batch as disposed
                StockBatchesTable.update({
                    StockBatchesTable.id eq batchUUID
                }) {
                    it[quantity] = BigDecimal.ZERO
                    it[status] = "DISPOSED"
                }
            }
            trace.step("Batch deleted/disposed", mapOf("batchId" to batchId))
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // GET expiry alerts — batches expiring soon or already expired
        get("/analytics/expiry-alerts") {
            val trace = call.routeTrace()
            trace.step("Get expiry alerts started")
            val principal = currentUser()
            planService.checkFeature(UUID.fromString(principal.vendorId), "STOCK")
            val vendorUUID = UUID.fromString(principal.vendorId)
            val daysThreshold = call.parameters["days"]?.toIntOrNull() ?: 30

            val alerts = transaction {
                val today = Clock.System.now().let {
                    val str = it.toString().substring(0, 10)
                    LocalDate.parse(str)
                }
                val thresholdDate = today.plus(DatePeriod(days = daysThreshold))

                // Get active batches with expiry dates within threshold or already expired
                val batches = StockBatchesTable.selectAll().where {
                    (StockBatchesTable.vendorId eq vendorUUID) and
                    (StockBatchesTable.expiryDate.isNotNull()) and
                    (StockBatchesTable.quantity greater BigDecimal.ZERO) and
                    (StockBatchesTable.status eq "ACTIVE") and
                    (StockBatchesTable.expiryDate lessEq thresholdDate)
                }.orderBy(StockBatchesTable.expiryDate, SortOrder.ASC).toList()

                // Build stock name lookup
                val stockIds = batches.map { it[StockBatchesTable.stockId] }.distinct()
                val stockInfo = if (stockIds.isNotEmpty()) {
                    StockTable.selectAll().where { StockTable.id inList stockIds }
                        .associate { it[StockTable.id] to Pair(it[StockTable.itemName], it[StockTable.unit]) }
                } else emptyMap()

                batches.map { row ->
                    val expiryDate = row[StockBatchesTable.expiryDate]!!
                    val daysUntilExpiry = today.daysUntil(expiryDate)
                    val stockId = row[StockBatchesTable.stockId]
                    val (itemName, unit) = stockInfo[stockId] ?: Pair("Unknown", "PIECE")

                    ExpiryAlertDto(
                        id = row[StockBatchesTable.id].toString(),
                        stock_id = stockId.toString(),
                        item_name = itemName,
                        batch_number = row[StockBatchesTable.batchNumber],
                        quantity = row[StockBatchesTable.quantity].toDouble(),
                        expiry_date = expiryDate.toString(),
                        days_until_expiry = daysUntilExpiry,
                        status = if (daysUntilExpiry < 0) "EXPIRED" else "EXPIRING_SOON",
                        unit = unit,
                    )
                }
            }
            trace.step("Expiry alerts fetched", mapOf(
                "count" to alerts.size.toString(),
                "expired" to alerts.count { it.status == "EXPIRED" }.toString()
            ))
            call.respond(HttpStatusCode.OK, alerts)
        }
    }
}

// ─── FIFO Batch Deduction Helper ────────────────────────────────

/**
 * Deducts [amount] from the oldest active batches for [stockUUID] (FIFO).
 * Returns the list of batch IDs affected. If no batches exist, returns empty list
 * and the caller should use the legacy deduction path.
 */
internal fun fifoDeductBatches(
    stockUUID: UUID,
    vendorUUID: UUID,
    amount: BigDecimal,
    transactionType: String = "BATCH_DEDUCT",
    orderId: UUID? = null,
    recipeId: UUID? = null,
    note: String? = null,
): List<UUID> {
    val now = Clock.System.now()
    val batches = StockBatchesTable.selectAll().where {
        (StockBatchesTable.stockId eq stockUUID) and
        (StockBatchesTable.vendorId eq vendorUUID) and
        (StockBatchesTable.quantity greater BigDecimal.ZERO) and
        (StockBatchesTable.status eq "ACTIVE")
    }.orderBy(StockBatchesTable.receivedAt, SortOrder.ASC).toList()

    if (batches.isEmpty()) return emptyList()

    var remaining = amount
    val affectedBatchIds = mutableListOf<UUID>()

    for (batch in batches) {
        if (remaining <= BigDecimal.ZERO) break

        val batchId = batch[StockBatchesTable.id]
        val batchQty = batch[StockBatchesTable.quantity]
        val deductFromBatch = remaining.min(batchQty)
        val newBatchQty = batchQty - deductFromBatch

        StockBatchesTable.update({ StockBatchesTable.id eq batchId }) {
            it[quantity] = newBatchQty
            if (newBatchQty <= BigDecimal.ZERO) {
                it[status] = "DEPLETED"
            }
        }

        // Record batch-level transaction
        StockTransactionsTable.insertAndGetId {
            it[StockTransactionsTable.stockId] = stockUUID
            it[StockTransactionsTable.batchId] = batchId
            it[type] = transactionType
            it[StockTransactionsTable.quantity] = deductFromBatch
            it[previousQuantity] = batchQty
            it[StockTransactionsTable.orderId] = orderId
            it[StockTransactionsTable.recipeId] = recipeId
            it[StockTransactionsTable.note] = note ?: "FIFO deduction from batch ${batch[StockBatchesTable.batchNumber] ?: batchId}"
            it[createdAt] = now
        }

        affectedBatchIds.add(batchId.value)
        remaining -= deductFromBatch
    }

    return affectedBatchIds
}

/**
 * Restores [amount] to the most recently depleted batches for [stockUUID] (reverse FIFO).
 * Used when orders are cancelled/refunded.
 */
internal fun fifoRestoreBatches(
    stockUUID: UUID,
    vendorUUID: UUID,
    amount: BigDecimal,
    orderId: UUID? = null,
    note: String? = null,
): List<UUID> {
    val now = Clock.System.now()
    // Restore to most recently depleted batches first (reverse order)
    val batches = StockBatchesTable.selectAll().where {
        (StockBatchesTable.stockId eq stockUUID) and
        (StockBatchesTable.vendorId eq vendorUUID) and
        (StockBatchesTable.status inList listOf("ACTIVE", "DEPLETED"))
    }.orderBy(StockBatchesTable.receivedAt, SortOrder.DESC).toList()

    if (batches.isEmpty()) return emptyList()

    var remaining = amount
    val affectedBatchIds = mutableListOf<UUID>()

    for (batch in batches) {
        if (remaining <= BigDecimal.ZERO) break

        val batchId = batch[StockBatchesTable.id]
        val batchQty = batch[StockBatchesTable.quantity]
        val initialQty = batch[StockBatchesTable.initialQuantity]
        val spaceInBatch = initialQty - batchQty
        if (spaceInBatch <= BigDecimal.ZERO) continue

        val restoreAmount = remaining.min(spaceInBatch)
        val newBatchQty = batchQty + restoreAmount

        StockBatchesTable.update({ StockBatchesTable.id eq batchId }) {
            it[quantity] = newBatchQty
            it[status] = "ACTIVE"
        }

        StockTransactionsTable.insertAndGetId {
            it[StockTransactionsTable.stockId] = stockUUID
            it[StockTransactionsTable.batchId] = batchId
            it[type] = "RETURN"
            it[StockTransactionsTable.quantity] = restoreAmount
            it[previousQuantity] = batchQty
            it[StockTransactionsTable.orderId] = orderId
            it[StockTransactionsTable.note] = note ?: "FIFO restoration to batch"
            it[createdAt] = now
        }

        affectedBatchIds.add(batchId.value)
        remaining -= restoreAmount
    }

    return affectedBatchIds
}

// ─── Mappers ────────────────────────────────────────────────────

private fun ResultRow.toStockDto() = StockDto(
    id = this[StockTable.id].toString(),
    vendor_id = this[StockTable.vendorId].toString(),
    item_id = this[StockTable.itemId]?.toString(),
    item_name = this[StockTable.itemName],
    quantity = this[StockTable.quantity].toDouble(),
    min_quantity = this[StockTable.minQuantity].toDouble(),
    cost_price = this[StockTable.costPrice].toDouble(),
    unit = this[StockTable.unit],
    base_unit = this[StockTable.baseUnit],
    conversion_rate = this[StockTable.conversionRate].toDouble(),
    is_menu_item = this[StockTable.isMenuItem],
    alert_enabled = this[StockTable.alertEnabled],
    created_at = this[StockTable.createdAt].toEpochMilliseconds(),
    updated_at = this[StockTable.updatedAt].toEpochMilliseconds(),
)

private fun ResultRow.toStockTransactionDto() = StockTransactionDto(
    id = this[StockTransactionsTable.id].toString(),
    stock_id = this[StockTransactionsTable.stockId].toString(),
    type = this[StockTransactionsTable.type],
    quantity = this[StockTransactionsTable.quantity].toDouble(),
    previous_quantity = this[StockTransactionsTable.previousQuantity].toDouble(),
    order_id = this[StockTransactionsTable.orderId]?.toString(),
    recipe_id = this[StockTransactionsTable.recipeId]?.toString(),
    note = this[StockTransactionsTable.note],
    created_at = this[StockTransactionsTable.createdAt].toEpochMilliseconds(),
)

private fun ResultRow.toBatchDto(itemName: String? = null) = StockBatchDto(
    id = this[StockBatchesTable.id].toString(),
    stock_id = this[StockBatchesTable.stockId].toString(),
    vendor_id = this[StockBatchesTable.vendorId].toString(),
    batch_number = this[StockBatchesTable.batchNumber],
    quantity = this[StockBatchesTable.quantity].toDouble(),
    initial_quantity = this[StockBatchesTable.initialQuantity].toDouble(),
    cost_price = this[StockBatchesTable.costPrice].toDouble(),
    expiry_date = this[StockBatchesTable.expiryDate]?.toString(),
    received_at = this[StockBatchesTable.receivedAt].toEpochMilliseconds(),
    status = this[StockBatchesTable.status],
    created_at = this[StockBatchesTable.createdAt].toEpochMilliseconds(),
    item_name = itemName,
)
