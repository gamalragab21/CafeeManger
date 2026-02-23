package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Recipe
import net.marllex.waselak.core.model.RecipeAvailability

interface RecipeRepository {
    fun getAllRecipes(): Flow<List<Recipe>>
    fun getRecipeById(id: String): Flow<Recipe?>
    fun getRecipeByItemId(itemId: String): Flow<Recipe?>
    suspend fun refreshRecipes(): Result<List<Recipe>>
    suspend fun createRecipe(
        itemId: String,
        name: String,
        description: String? = null,
        yieldQuantity: Double = 1.0,
        yieldUnit: String = "PIECE",
        ingredients: List<IngredientInput>,
    ): Result<Recipe>

    suspend fun updateRecipe(
        id: String,
        name: String? = null,
        description: String? = null,
        yieldQuantity: Double? = null,
        yieldUnit: String? = null,
        active: Boolean? = null,
        ingredients: List<IngredientInput>? = null,
    ): Result<Recipe>

    suspend fun deleteRecipe(id: String): Result<Unit>
    suspend fun checkAvailability(itemId: String, servings: Double = 1.0): Result<RecipeAvailability>
}

data class IngredientInput(
    val stockId: String,
    val quantity: Double,
    val unit: String,
    val displayOrder: Int = 0,
)
