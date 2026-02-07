package net.marllex.cafeemanger.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.model.Stock
import net.marllex.cafeemanger.core.model.StockSummary
import net.marllex.cafeemanger.core.model.StockTransaction

interface StockRepository {
    fun getAllStock(): Flow<List<Stock>>
    fun getLowStock(): Flow<List<Stock>>
    fun getOutOfStock(): Flow<List<Stock>>
    fun getStockById(id: String): Flow<Stock?>
    fun getStockByItemId(itemId: String): Flow<Stock?>
    fun getTransactions(stockId: String): Flow<List<StockTransaction>>
    suspend fun refreshStock(): Result<List<Stock>>

    suspend fun addStockItem(
        itemId: String,
        itemName: String,
        quantity: Int,
        minQuantity: Int,
        costPrice: Double,
        unit: String,
    ): Result<Stock>

    suspend fun updateStockItem(
        id: String,
        quantity: Int? = null,
        minQuantity: Int? = null,
        costPrice: Double? = null,
        unit: String? = null,
    ): Result<Stock>

    suspend fun addQuantity(stockId: String, quantity: Int, note: String? = null): Result<Stock>
    suspend fun deductQuantity(stockId: String, quantity: Int, orderId: String? = null, note: String? = null): Result<Stock>
    suspend fun deductByItemId(itemId: String, quantity: Int, orderId: String? = null): Result<Unit>
    suspend fun deleteStockItem(id: String): Result<Unit>
}
