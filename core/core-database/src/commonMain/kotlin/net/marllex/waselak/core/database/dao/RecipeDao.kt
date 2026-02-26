package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Recipes
import net.marllex.waselak.core.database.Recipe_ingredients
import net.marllex.waselak.core.model.Recipe
import net.marllex.waselak.core.model.RecipeIngredient

class RecipeDao(private val db: WaselakDatabase) {
    private val recipeQueries get() = db.recipeQueries

    fun getAllRecipes(vendorId: String): Flow<List<Recipes>> =
        recipeQueries.getAllRecipes(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getRecipeById(id: String): Flow<Recipes?> =
        recipeQueries.getRecipeById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun getRecipeByIdSync(id: String): Recipes? =
        recipeQueries.getRecipeById(id).executeAsOneOrNull()

    fun getRecipeByItemId(vendorId: String, itemId: String): Flow<Recipes?> =
        recipeQueries.getRecipeByItemId(vendorId, itemId).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun getRecipeByItemIdSync(vendorId: String, itemId: String): Recipes? =
        recipeQueries.getRecipeByItemId(vendorId, itemId).executeAsOneOrNull()

    fun getIngredientsByRecipeId(recipeId: String): Flow<List<Recipe_ingredients>> =
        recipeQueries.getIngredientsByRecipeId(recipeId).asFlow().mapToList(Dispatchers.Default)

    suspend fun getIngredientsByRecipeIdSync(recipeId: String): List<Recipe_ingredients> =
        recipeQueries.getIngredientsByRecipeId(recipeId).executeAsList()

    suspend fun insertRecipe(recipe: Recipe) {
        db.transaction {
            recipeQueries.insertRecipe(
                id = recipe.id,
                vendor_id = recipe.vendorId,
                item_id = recipe.itemId,
                item_name = recipe.itemName,
                name = recipe.name,
                description = recipe.description,
                yield_quantity = recipe.yieldQuantity,
                yield_unit = recipe.yieldUnit,
                status = recipe.status,
                total_cost = recipe.totalCost,
                created_at = recipe.createdAt,
                updated_at = recipe.updatedAt,
            )

            // Replace all ingredients
            recipeQueries.deleteIngredientsByRecipeId(recipe.id)
            recipe.ingredients.forEachIndexed { index, ingredient ->
                recipeQueries.insertIngredient(
                    recipe_id = recipe.id,
                    stock_id = ingredient.stockId,
                    stock_item_name = ingredient.stockItemName,
                    quantity = ingredient.quantity,
                    unit = ingredient.unit,
                    fixed_quantity = ingredient.fixedQuantity,
                    display_order = ingredient.displayOrder,
                    available_quantity = ingredient.availableQuantity,
                )
            }
        }
    }

    suspend fun insertAllRecipes(recipes: List<Recipe>) {
        db.transaction {
            recipes.forEach { recipe ->
                recipeQueries.insertRecipe(
                    id = recipe.id,
                    vendor_id = recipe.vendorId,
                    item_id = recipe.itemId,
                    item_name = recipe.itemName,
                    name = recipe.name,
                    description = recipe.description,
                    yield_quantity = recipe.yieldQuantity,
                    yield_unit = recipe.yieldUnit,
                    status = recipe.status,
                    total_cost = recipe.totalCost,
                    created_at = recipe.createdAt,
                    updated_at = recipe.updatedAt,
                )

                recipeQueries.deleteIngredientsByRecipeId(recipe.id)
                recipe.ingredients.forEach { ingredient ->
                    recipeQueries.insertIngredient(
                        recipe_id = recipe.id,
                        stock_id = ingredient.stockId,
                        stock_item_name = ingredient.stockItemName,
                        quantity = ingredient.quantity,
                        unit = ingredient.unit,
                        fixed_quantity = ingredient.fixedQuantity,
                        display_order = ingredient.displayOrder,
                        available_quantity = ingredient.availableQuantity,
                    )
                }
            }
        }
    }

    suspend fun deleteRecipe(id: String) {
        db.transaction {
            recipeQueries.deleteIngredientsByRecipeId(id)
            recipeQueries.deleteRecipe(id)
        }
    }

    suspend fun deleteAllRecipes(vendorId: String) {
        recipeQueries.deleteAllRecipes(vendorId)
    }
}
