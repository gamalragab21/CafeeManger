package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.RecipeDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.IngredientInput
import net.marllex.waselak.core.domain.repository.RecipeRepository
import net.marllex.waselak.core.model.Recipe
import net.marllex.waselak.core.model.RecipeAvailability
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateRecipeIngredientRequest
import net.marllex.waselak.core.network.dto.CreateRecipeRequest
import net.marllex.waselak.core.network.dto.UpdateRecipeRequest
import net.marllex.waselak.core.network.mapper.toDomain

class RecipeRepositoryImpl(
    private val api: WaselakApiClient,
    private val recipeDao: RecipeDao,
    private val authRepository: AuthRepository,
) : RecipeRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getAllRecipes(): Flow<List<Recipe>> =
        recipeDao.getAllRecipes(vendorId).map { list ->
            list.map { recipeDb ->
                val ingredients = recipeDao.getIngredientsByRecipeIdSync(recipeDb.id)
                recipeDb.toDomain(ingredients.map { it.toDomain() })
            }
        }

    override fun getRecipeById(id: String): Flow<Recipe?> =
        recipeDao.getRecipeById(id).map { recipeDb ->
            recipeDb?.let {
                val ingredients = recipeDao.getIngredientsByRecipeIdSync(it.id)
                it.toDomain(ingredients.map { ing -> ing.toDomain() })
            }
        }

    override fun getRecipeByItemId(itemId: String): Flow<Recipe?> =
        recipeDao.getRecipeByItemId(vendorId, itemId).map { recipeDb ->
            recipeDb?.let {
                val ingredients = recipeDao.getIngredientsByRecipeIdSync(it.id)
                it.toDomain(ingredients.map { ing -> ing.toDomain() })
            }
        }

    override suspend fun refreshRecipes(): Result<List<Recipe>> = runCatching {
        val response = api.getRecipes()
        val recipes = response.map { it.toDomain() }
        recipeDao.deleteAllRecipes(vendorId)
        recipeDao.insertAllRecipes(recipes)
        recipes
    }

    override suspend fun createRecipe(
        itemId: String,
        name: String,
        description: String?,
        yieldQuantity: Double,
        yieldUnit: String,
        ingredients: List<IngredientInput>,
    ): Result<Recipe> = runCatching {
        val response = api.createRecipe(
            CreateRecipeRequest(
                itemId = itemId,
                name = name,
                description = description,
                yieldQuantity = yieldQuantity,
                yieldUnit = yieldUnit,
                ingredients = ingredients.map {
                    CreateRecipeIngredientRequest(
                        stockId = it.stockId,
                        quantity = it.quantity,
                        unit = it.unit,
                        displayOrder = it.displayOrder,
                    )
                }
            )
        )
        val recipe = response.toDomain()
        recipeDao.insertRecipe(recipe)
        recipe
    }

    override suspend fun updateRecipe(
        id: String,
        name: String?,
        description: String?,
        yieldQuantity: Double?,
        yieldUnit: String?,
        active: Boolean?,
        ingredients: List<IngredientInput>?,
    ): Result<Recipe> = runCatching {
        val response = api.updateRecipe(
            id,
            UpdateRecipeRequest(
                name = name,
                description = description,
                yieldQuantity = yieldQuantity,
                yieldUnit = yieldUnit,
                active = active,
                ingredients = ingredients?.map {
                    CreateRecipeIngredientRequest(
                        stockId = it.stockId,
                        quantity = it.quantity,
                        unit = it.unit,
                        displayOrder = it.displayOrder,
                    )
                }
            )
        )
        val recipe = response.toDomain()
        recipeDao.insertRecipe(recipe)
        recipe
    }

    override suspend fun deleteRecipe(id: String): Result<Unit> = runCatching {
        api.deleteRecipe(id)
        recipeDao.deleteRecipe(id)
    }

    override suspend fun checkAvailability(itemId: String, servings: Double): Result<RecipeAvailability> = runCatching {
        api.checkRecipeAvailability(itemId, servings).toDomain()
    }
}
