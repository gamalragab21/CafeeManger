package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Recipe(
    val id: String,
    val vendorId: String,
    val itemId: String,
    val itemName: String,
    val name: String,
    val description: String? = null,
    val yieldQuantity: Double = 1.0,
    val yieldUnit: String = "PIECE",
    val status: String = "ACTIVE", // DRAFT, ACTIVE, ARCHIVED
    val ingredients: List<RecipeIngredient> = emptyList(),
    val totalCost: Double = 0.0,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
) {
    val isActive: Boolean get() = status == "ACTIVE"
    val isDraft: Boolean get() = status == "DRAFT"
    val isArchived: Boolean get() = status == "ARCHIVED"
}

@Serializable
data class RecipeIngredient(
    val stockId: String,
    val stockItemName: String,
    val quantity: Double,
    val unit: String,
    val displayOrder: Int = 0,
    val availableQuantity: Double = 0.0,
)

@Serializable
data class RecipeAvailability(
    val recipeId: String,
    val itemName: String,
    val available: Boolean,
    val maxServings: Double,
    val requestedServings: Double,
    val insufficientIngredients: List<InsufficientIngredient> = emptyList(),
)

@Serializable
data class InsufficientIngredient(
    val stockId: String,
    val stockItemName: String,
    val required: Double,
    val available: Double,
    val unit: String,
)
