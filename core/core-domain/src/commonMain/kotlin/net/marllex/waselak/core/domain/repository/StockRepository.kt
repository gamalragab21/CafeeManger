package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Stock
import net.marllex.waselak.core.model.StockAlert
import net.marllex.waselak.core.model.StockSummary
import net.marllex.waselak.core.model.StockTransaction

interface StockRepository {
    fun getAllStock(): Flow<List<Stock>>
    fun getLowStock(): Flow<List<Stock>>
    fun getOutOfStock(): Flow<List<Stock>>
    fun getStockById(id: String): Flow<Stock?>
    fun getStockByItemId(itemId: String): Flow<Stock?>
    fun getTransactions(stockId: String): Flow<List<StockTransaction>>
    suspend fun refreshStock(): Result<List<Stock>>

    // Add stock item - supports both menu-linked and independent items
    suspend fun addStockItem(
        itemId: String? = null,
        itemName: String,
        quantity: Double,
        minQuantity: Double,
        costPrice: Double,
        unit: String,
        baseUnit: String = "PIECE",
        conversionRate: Double = 1.0,
        alertEnabled: Boolean = true,
    ): Result<Stock>

    suspend fun updateStockItem(
        id: String,
        itemName: String? = null,
        quantity: Double? = null,
        minQuantity: Double? = null,
        costPrice: Double? = null,
        unit: String? = null,
        baseUnit: String? = null,
        alertEnabled: Boolean? = null,
    ): Result<Stock>

    suspend fun addQuantity(stockId: String, quantity: Double, note: String? = null): Result<Stock>
    suspend fun deductQuantity(stockId: String, quantity: Double, orderId: String? = null, note: String? = null): Result<Stock>
    suspend fun deductByItemId(itemId: String, quantity: Double, orderId: String? = null): Result<Unit>
    suspend fun deleteStockItem(id: String): Result<Unit>

    // Stock Analytics
    suspend fun getAllTransactions(
        stockId: String? = null,
        type: String? = null,
        from: Long? = null,
        to: Long? = null,
        limit: Int = 100
    ): Result<List<StockTransaction>>

    suspend fun getStockAlerts(): Result<List<StockAlert>>
    suspend fun getAnalyticsSummary(): Result<StockSummary>
}
