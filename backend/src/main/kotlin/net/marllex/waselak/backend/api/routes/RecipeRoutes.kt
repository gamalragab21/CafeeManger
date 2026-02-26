package net.marllex.waselak.backend.api.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.marllex.waselak.backend.api.middleware.currentUser
import net.marllex.waselak.backend.api.middleware.requireRole
import net.marllex.waselak.backend.data.database.*
import net.marllex.waselak.backend.domain.model.StockUnit
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

// ─── DTOs ───────────────────────────────────────────────────────

@Serializable
data class RecipeDto(
    val id: String,
    val vendor_id: String,
    val item_id: String,
    val item_name: String,
    val name: String,
    val description: String? = null,
    val yield_quantity: Double = 1.0,
    val yield_unit: String = "PIECE",
    val status: String = "ACTIVE", // DRAFT, ACTIVE, ARCHIVED
    val ingredients: List<RecipeIngredientDto> = emptyList(),
    val total_cost: Double = 0.0,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class RecipeIngredientDto(
    val id: String,
    val stock_id: String,
    val stock_item_name: String,
    val quantity: Double,
    val unit: String,
    val fixed_quantity: Boolean = false,
    val available_quantity: Double = 0.0,
    val display_order: Int = 0,
)

@Serializable
data class CreateRecipeDto(
    val item_id: String,
    val name: String? = null,
    val description: String? = null,
    val yield_quantity: Double = 1.0,
    val yield_unit: String = "PIECE",
    val ingredients: List<CreateRecipeIngredientDto>,
)

@Serializable
data class CreateRecipeIngredientDto(
    val stock_id: String,
    val quantity: Double,
    val unit: String,
    val fixed_quantity: Boolean = false,
    val display_order: Int = 0,
)

@Serializable
data class UpdateRecipeDto(
    val name: String? = null,
    val description: String? = null,
    val yield_quantity: Double? = null,
    val yield_unit: String? = null,
    val status: String? = null, // DRAFT, ACTIVE, ARCHIVED
    val ingredients: List<CreateRecipeIngredientDto>? = null, // Full replace if provided
)

@Serializable
data class RecipeAvailabilityDto(
    val available: Boolean,
    val max_quantity: Int,
    val insufficient_ingredients: List<InsufficientIngredientDto> = emptyList(),
)

@Serializable
data class InsufficientIngredientDto(
    val stock_item_name: String,
    val available: Double,
    val required: Double,
    val unit: String,
)

// ─── Routes ─────────────────────────────────────────────────────

fun Route.recipeRoutes() {
    route("/api/v1/recipes") {

        // GET all recipes for this vendor
        get {
            val principal = currentUser()
            val vendorUUID = UUID.fromString(principal.vendorId)

            val recipes = transaction {
                val recipeRows = RecipesTable.selectAll()
                    .where { RecipesTable.vendorId eq vendorUUID }
                    .orderBy(RecipesTable.name)
                    .toList()

                recipeRows.map { row ->
                    mapRecipeRow(row, vendorUUID)
                }
            }
            call.respond(HttpStatusCode.OK, recipes)
        }

        // GET single recipe
        get("/{id}") {
            val principal = currentUser()
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            val recipe = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val row = RecipesTable.selectAll()
                    .where {
                        (RecipesTable.id eq UUID.fromString(id)) and
                        (RecipesTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Recipe not found")

                mapRecipeRow(row, vendorUUID)
            }
            call.respond(HttpStatusCode.OK, recipe)
        }

        // GET recipe by item ID
        get("/by-item/{itemId}") {
            val principal = currentUser()
            val itemId = call.parameters["itemId"] ?: throw IllegalArgumentException("Item ID required")

            val recipe = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val row = RecipesTable.selectAll()
                    .where {
                        (RecipesTable.itemId eq UUID.fromString(itemId)) and
                        (RecipesTable.vendorId eq vendorUUID)
                    }.firstOrNull()

                row?.let { mapRecipeRow(it, vendorUUID) }
            }

            if (recipe != null) {
                call.respond(HttpStatusCode.OK, recipe)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Recipe not found for this item"))
            }
        }

        // CREATE recipe (MANAGER only)
        post {
            val principal = requireRole("MANAGER")
            val request = call.receive<CreateRecipeDto>()
            require(request.ingredients.isNotEmpty()) { "Recipe must have at least one ingredient" }
            require(request.yield_quantity > 0) { "Yield quantity must be positive" }

            val recipe = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val itemUUID = UUID.fromString(request.item_id)
                val now = Clock.System.now()

                // Verify item belongs to vendor
                val item = ItemsTable.selectAll()
                    .where {
                        (ItemsTable.id eq itemUUID) and
                        (ItemsTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Item not found")

                // Validate all ingredients
                request.ingredients.forEach { ingredient ->
                    val stockUUID = UUID.fromString(ingredient.stock_id)
                    val stockRow = StockTable.selectAll()
                        .where {
                            (StockTable.id eq stockUUID) and
                            (StockTable.vendorId eq vendorUUID)
                        }.firstOrNull() ?: throw NoSuchElementException("Stock item '${ingredient.stock_id}' not found")

                    // Validate unit compatibility
                    val ingredientUnit = StockUnit.fromString(ingredient.unit)
                    val stockBaseUnit = StockUnit.fromString(stockRow[StockTable.baseUnit])
                    if (ingredientUnit != null && stockBaseUnit != null) {
                        require(StockUnit.areCompatible(ingredientUnit, stockBaseUnit)) {
                            "Unit '${ingredient.unit}' is not compatible with stock base unit '${stockRow[StockTable.baseUnit]}' for '${stockRow[StockTable.itemName]}'"
                        }
                    }
                }

                val recipeName = request.name ?: item[ItemsTable.name]

                // Upsert: if recipe already exists for this item, update it instead of 409
                val existing = RecipesTable.selectAll()
                    .where {
                        (RecipesTable.itemId eq itemUUID) and
                        (RecipesTable.vendorId eq vendorUUID)
                    }.firstOrNull()

                val recipeId = if (existing != null) {
                    // UPDATE existing recipe
                    val existingId = existing[RecipesTable.id]
                    RecipesTable.update({
                        (RecipesTable.id eq existingId) and (RecipesTable.vendorId eq vendorUUID)
                    }) {
                        it[name] = recipeName
                        it[description] = request.description
                        it[yieldQuantity] = BigDecimal.valueOf(request.yield_quantity)
                        it[yieldUnit] = request.yield_unit
                        it[status] = "ACTIVE"
                        it[updatedAt] = now
                    }
                    // Replace ingredients
                    RecipeIngredientsTable.deleteWhere { RecipeIngredientsTable.recipeId eq existingId }
                    request.ingredients.forEach { ingredient ->
                        RecipeIngredientsTable.insertAndGetId {
                            it[RecipeIngredientsTable.recipeId] = existingId
                            it[stockId] = UUID.fromString(ingredient.stock_id)
                            it[quantity] = BigDecimal.valueOf(ingredient.quantity)
                            it[unit] = ingredient.unit
                            it[fixedQuantity] = ingredient.fixed_quantity
                            it[displayOrder] = ingredient.display_order
                            it[createdAt] = now
                        }
                    }
                    existingId
                } else {
                    // CREATE new recipe
                    val newId = RecipesTable.insertAndGetId {
                        it[vendorId] = vendorUUID
                        it[RecipesTable.itemId] = itemUUID
                        it[name] = recipeName
                        it[description] = request.description
                        it[yieldQuantity] = BigDecimal.valueOf(request.yield_quantity)
                        it[yieldUnit] = request.yield_unit
                        it[status] = "ACTIVE"
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    // Insert ingredients
                    request.ingredients.forEach { ingredient ->
                        RecipeIngredientsTable.insertAndGetId {
                            it[RecipeIngredientsTable.recipeId] = newId
                            it[stockId] = UUID.fromString(ingredient.stock_id)
                            it[quantity] = BigDecimal.valueOf(ingredient.quantity)
                            it[unit] = ingredient.unit
                            it[fixedQuantity] = ingredient.fixed_quantity
                            it[displayOrder] = ingredient.display_order
                            it[createdAt] = now
                        }
                    }
                    newId
                }

                // Auto-set item's stockBehavior to RECIPE
                ItemsTable.update({ ItemsTable.id eq itemUUID }) {
                    it[stockBehavior] = "RECIPE"
                    it[updatedAt] = now
                }

                // Return the full recipe
                val row = RecipesTable.selectAll()
                    .where { RecipesTable.id eq recipeId }.first()
                mapRecipeRow(row, vendorUUID)
            }
            call.respond(HttpStatusCode.Created, recipe)
        }

        // UPDATE recipe (MANAGER only)
        put("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")
            val request = call.receive<UpdateRecipeDto>()

            val updated = transaction {
                val recipeUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                // Verify recipe exists and belongs to vendor
                val current = RecipesTable.selectAll()
                    .where {
                        (RecipesTable.id eq recipeUUID) and
                        (RecipesTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Recipe not found")

                // Validate status if provided
                val validStatuses = listOf("DRAFT", "ACTIVE", "ARCHIVED")
                request.status?.let { newStatus ->
                    require(newStatus in validStatuses) {
                        "Invalid recipe status '$newStatus'. Must be one of: ${validStatuses.joinToString()}"
                    }
                    // Activation validation: when activating, ensure recipe has ingredients
                    if (newStatus == "ACTIVE" && current[RecipesTable.status] != "ACTIVE") {
                        val ingredientCount = RecipeIngredientsTable.selectAll()
                            .where { RecipeIngredientsTable.recipeId eq recipeUUID }
                            .count()
                        require(ingredientCount > 0) {
                            "Cannot activate recipe without ingredients"
                        }
                    }
                }

                // Update recipe fields
                RecipesTable.update({
                    (RecipesTable.id eq recipeUUID) and
                    (RecipesTable.vendorId eq vendorUUID)
                }) { stmt ->
                    request.name?.let { stmt[name] = it }
                    request.description?.let { stmt[description] = it }
                    request.yield_quantity?.let { stmt[yieldQuantity] = BigDecimal.valueOf(it) }
                    request.yield_unit?.let { stmt[yieldUnit] = it }
                    request.status?.let { stmt[status] = it }
                    stmt[updatedAt] = now
                }

                // Replace ingredients if provided
                if (request.ingredients != null) {
                    // Validate all ingredients
                    request.ingredients.forEach { ingredient ->
                        val stockUUID = UUID.fromString(ingredient.stock_id)
                        val stockRow = StockTable.selectAll()
                            .where {
                                (StockTable.id eq stockUUID) and
                                (StockTable.vendorId eq vendorUUID)
                            }.firstOrNull() ?: throw NoSuchElementException("Stock item '${ingredient.stock_id}' not found")

                        val ingredientUnit = StockUnit.fromString(ingredient.unit)
                        val stockBaseUnit = StockUnit.fromString(stockRow[StockTable.baseUnit])
                        if (ingredientUnit != null && stockBaseUnit != null) {
                            require(StockUnit.areCompatible(ingredientUnit, stockBaseUnit)) {
                                "Unit '${ingredient.unit}' is not compatible with stock base unit '${stockRow[StockTable.baseUnit]}'"
                            }
                        }
                    }

                    // Delete old ingredients
                    RecipeIngredientsTable.deleteWhere { RecipeIngredientsTable.recipeId eq recipeUUID }

                    // Insert new ingredients
                    request.ingredients.forEach { ingredient ->
                        RecipeIngredientsTable.insertAndGetId {
                            it[RecipeIngredientsTable.recipeId] = recipeUUID
                            it[stockId] = UUID.fromString(ingredient.stock_id)
                            it[quantity] = BigDecimal.valueOf(ingredient.quantity)
                            it[unit] = ingredient.unit
                            it[fixedQuantity] = ingredient.fixed_quantity
                            it[displayOrder] = ingredient.display_order
                            it[createdAt] = now
                        }
                    }
                }

                val row = RecipesTable.selectAll()
                    .where { RecipesTable.id eq recipeUUID }.first()
                mapRecipeRow(row, vendorUUID)
            }
            call.respond(HttpStatusCode.OK, updated)
        }

        // DELETE recipe (MANAGER only)
        delete("/{id}") {
            val principal = requireRole("MANAGER")
            val id = call.parameters["id"] ?: throw IllegalArgumentException("ID required")

            transaction {
                val recipeUUID = UUID.fromString(id)
                val vendorUUID = UUID.fromString(principal.vendorId)
                val now = Clock.System.now()

                val recipe = RecipesTable.selectAll()
                    .where {
                        (RecipesTable.id eq recipeUUID) and
                        (RecipesTable.vendorId eq vendorUUID)
                    }.firstOrNull() ?: throw NoSuchElementException("Recipe not found")

                val itemUUID = recipe[RecipesTable.itemId]

                // Delete ingredients (CASCADE should handle this, but be explicit)
                RecipeIngredientsTable.deleteWhere { RecipeIngredientsTable.recipeId eq recipeUUID }

                // Delete recipe
                RecipesTable.deleteWhere {
                    (RecipesTable.id eq recipeUUID) and (RecipesTable.vendorId eq vendorUUID)
                }

                // Reset item's stockBehavior to NONE
                ItemsTable.update({ ItemsTable.id eq itemUUID }) {
                    it[stockBehavior] = "NONE"
                    it[updatedAt] = now
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        // CHECK recipe availability for a given quantity
        get("/check-availability/{itemId}") {
            val principal = currentUser()
            val itemId = call.parameters["itemId"] ?: throw IllegalArgumentException("Item ID required")
            val requestedQty = call.parameters["quantity"]?.toIntOrNull() ?: 1

            val availability = transaction {
                val vendorUUID = UUID.fromString(principal.vendorId)
                val itemUUID = UUID.fromString(itemId)

                val recipe = RecipesTable.selectAll()
                    .where {
                        (RecipesTable.itemId eq itemUUID) and
                        (RecipesTable.vendorId eq vendorUUID) and
                        (RecipesTable.status eq "ACTIVE")
                    }.firstOrNull() ?: throw NoSuchElementException("No active recipe found for this item")

                val recipeId = recipe[RecipesTable.id]
                val yieldQty = recipe[RecipesTable.yieldQuantity].toDouble()

                val ingredients = RecipeIngredientsTable.selectAll()
                    .where { RecipeIngredientsTable.recipeId eq recipeId }
                    .toList()

                val multiplier = requestedQty.toDouble() / yieldQty
                val insufficient = mutableListOf<InsufficientIngredientDto>()
                var maxServings = Int.MAX_VALUE

                for (ingredient in ingredients) {
                    val stockRow = StockTable.selectAll()
                        .where { StockTable.id eq ingredient[RecipeIngredientsTable.stockId] }
                        .firstOrNull() ?: continue

                    val isFixed = ingredient[RecipeIngredientsTable.fixedQuantity]
                    val effectiveMultiplier = if (isFixed) 1.0 else multiplier
                    val requiredQty = ingredient[RecipeIngredientsTable.quantity].toDouble() * effectiveMultiplier
                    val ingredientUnit = ingredient[RecipeIngredientsTable.unit]
                    val stockUnit = stockRow[StockTable.unit]

                    // Convert required qty to stock's display unit
                    val convertedRequired = convertUnits(requiredQty, ingredientUnit, stockUnit)
                    val availableQty = stockRow[StockTable.quantity].toDouble()

                    if (availableQty < convertedRequired) {
                        insufficient.add(InsufficientIngredientDto(
                            stock_item_name = stockRow[StockTable.itemName],
                            available = availableQty,
                            required = convertedRequired,
                            unit = stockUnit,
                        ))
                    }

                    // Calculate max servings based on this ingredient
                    // Fixed ingredients don't scale with servings, so they don't limit max servings
                    val perServing = if (isFixed) 0.0 else ingredient[RecipeIngredientsTable.quantity].toDouble() / yieldQty
                    val convertedPerServing = convertUnits(perServing, ingredientUnit, stockUnit)
                    if (convertedPerServing > 0) {
                        val servingsFromThis = (availableQty / convertedPerServing).toInt()
                        maxServings = minOf(maxServings, servingsFromThis)
                    }
                }

                if (maxServings == Int.MAX_VALUE) maxServings = 0

                RecipeAvailabilityDto(
                    available = insufficient.isEmpty(),
                    max_quantity = maxServings,
                    insufficient_ingredients = insufficient,
                )
            }
            call.respond(HttpStatusCode.OK, availability)
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────

private fun mapRecipeRow(row: ResultRow, vendorUUID: UUID): RecipeDto {
    val recipeId = row[RecipesTable.id]
    val itemUUID = row[RecipesTable.itemId]

    // Get item name
    val itemName = ItemsTable.selectAll()
        .where { ItemsTable.id eq itemUUID }
        .firstOrNull()?.get(ItemsTable.name) ?: "Unknown Item"

    // Get ingredients
    val ingredientRows = RecipeIngredientsTable.selectAll()
        .where { RecipeIngredientsTable.recipeId eq recipeId }
        .orderBy(RecipeIngredientsTable.displayOrder)
        .toList()

    var totalCost = 0.0
    val ingredients = ingredientRows.map { ingRow ->
        val stockRow = StockTable.selectAll()
            .where { StockTable.id eq ingRow[RecipeIngredientsTable.stockId] }
            .firstOrNull()

        val stockName = stockRow?.get(StockTable.itemName) ?: "Unknown"
        val availableQty = stockRow?.get(StockTable.quantity)?.toDouble() ?: 0.0
        val costPerBaseUnit = stockRow?.let {
            val costPrice = it[StockTable.costPrice].toDouble()
            val convRate = it[StockTable.conversionRate].toDouble()
            if (convRate > 0) costPrice / convRate else 0.0
        } ?: 0.0

        // Calculate ingredient cost
        val ingredientUnit = ingRow[RecipeIngredientsTable.unit]
        val stockDisplayUnit = stockRow?.get(StockTable.unit) ?: ingredientUnit
        val convertedQty = convertUnits(
            ingRow[RecipeIngredientsTable.quantity].toDouble(),
            ingredientUnit,
            stockDisplayUnit
        )
        totalCost += convertedQty * costPerBaseUnit

        RecipeIngredientDto(
            id = ingRow[RecipeIngredientsTable.id].toString(),
            stock_id = ingRow[RecipeIngredientsTable.stockId].toString(),
            stock_item_name = stockName,
            quantity = ingRow[RecipeIngredientsTable.quantity].toDouble(),
            unit = ingredientUnit,
            fixed_quantity = ingRow[RecipeIngredientsTable.fixedQuantity],
            available_quantity = availableQty,
            display_order = ingRow[RecipeIngredientsTable.displayOrder],
        )
    }

    return RecipeDto(
        id = row[RecipesTable.id].toString(),
        vendor_id = row[RecipesTable.vendorId].toString(),
        item_id = itemUUID.toString(),
        item_name = itemName,
        name = row[RecipesTable.name],
        description = row[RecipesTable.description],
        yield_quantity = row[RecipesTable.yieldQuantity].toDouble(),
        yield_unit = row[RecipesTable.yieldUnit],
        status = row[RecipesTable.status],
        ingredients = ingredients,
        total_cost = totalCost,
        created_at = row[RecipesTable.createdAt].toEpochMilliseconds(),
        updated_at = row[RecipesTable.updatedAt].toEpochMilliseconds(),
    )
}

/** Convert a value between unit types. Falls back to 1:1 if units are unknown. */
internal fun convertUnits(value: Double, fromUnit: String, toUnit: String): Double {
    if (fromUnit == toUnit) return value
    val from = StockUnit.fromString(fromUnit) ?: return value
    val to = StockUnit.fromString(toUnit) ?: return value
    return try {
        StockUnit.convert(value, from, to)
    } catch (_: IllegalArgumentException) {
        value // Fallback: no conversion if incompatible
    }
}
