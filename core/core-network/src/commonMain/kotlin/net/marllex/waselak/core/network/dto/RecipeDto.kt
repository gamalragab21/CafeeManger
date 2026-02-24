package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String,
    val name: String,
    val description: String? = null,
    @SerialName("yield_quantity") val yieldQuantity: Double = 1.0,
    @SerialName("yield_unit") val yieldUnit: String = "PIECE",
    val status: String = "ACTIVE",
    val ingredients: List<RecipeIngredientResponse> = emptyList(),
    @SerialName("total_cost") val totalCost: Double = 0.0,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class RecipeIngredientResponse(
    @SerialName("stock_id") val stockId: String,
    @SerialName("stock_item_name") val stockItemName: String,
    val quantity: Double,
    val unit: String,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("available_quantity") val availableQuantity: Double = 0.0,
)

@Serializable
data class CreateRecipeRequest(
    @SerialName("item_id") val itemId: String,
    val name: String,
    val description: String? = null,
    @SerialName("yield_quantity") val yieldQuantity: Double = 1.0,
    @SerialName("yield_unit") val yieldUnit: String = "PIECE",
    val ingredients: List<CreateRecipeIngredientRequest>,
)

@Serializable
data class CreateRecipeIngredientRequest(
    @SerialName("stock_id") val stockId: String,
    val quantity: Double,
    val unit: String,
    @SerialName("display_order") val displayOrder: Int = 0,
)

@Serializable
data class UpdateRecipeRequest(
    val name: String? = null,
    val description: String? = null,
    @SerialName("yield_quantity") val yieldQuantity: Double? = null,
    @SerialName("yield_unit") val yieldUnit: String? = null,
    val status: String? = null,
    val ingredients: List<CreateRecipeIngredientRequest>? = null,
)

@Serializable
data class RecipeAvailabilityResponse(
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("item_name") val itemName: String,
    val available: Boolean,
    @SerialName("max_servings") val maxServings: Double,
    @SerialName("requested_servings") val requestedServings: Double,
    @SerialName("insufficient_ingredients") val insufficientIngredients: List<InsufficientIngredientResponse> = emptyList(),
)

@Serializable
data class InsufficientIngredientResponse(
    @SerialName("stock_id") val stockId: String,
    @SerialName("stock_item_name") val stockItemName: String,
    val required: Double,
    val available: Double,
    val unit: String,
)
