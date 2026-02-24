package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.StockDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.StockRepository
import net.marllex.waselak.core.model.Stock
import net.marllex.waselak.core.model.StockAlert
import net.marllex.waselak.core.model.StockSummary
import net.marllex.waselak.core.model.StockTransaction
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.AdjustQuantityRequest
import net.marllex.waselak.core.network.dto.CreateStockRequest
import net.marllex.waselak.core.network.dto.UpdateStockRequest
import net.marllex.waselak.core.network.mapper.toDomain

class StockRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val stockDao: StockDao,
    private val authRepository: AuthRepository,
) : StockRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    // ─── Read operations (from local DB, synced from API) ───────

    override fun getAllStock(): Flow<List<Stock>> =
        stockDao.getAllStock(vendorId).map { list -> list.map { it.toDomain() } }

    override fun getLowStock(): Flow<List<Stock>> =
        stockDao.getLowStock(vendorId).map { list -> list.map { it.toDomain() } }

    override fun getOutOfStock(): Flow<List<Stock>> =
        stockDao.getOutOfStock(vendorId).map { list -> list.map { it.toDomain() } }

    override fun getStockById(id: String): Flow<Stock?> =
        stockDao.getStockById(id).map { it?.toDomain() }

    override fun getStockByItemId(itemId: String): Flow<Stock?> =
        stockDao.getStockByItemId(vendorId, itemId).map { it?.toDomain() }

    override fun getTransactions(stockId: String): Flow<List<StockTransaction>> =
        stockDao.getTransactionsByStockId(stockId).map { list -> list.map { it.toDomain() } }

    // ─── Sync from API ──────────────────────────────────────────

    override suspend fun refreshStock(): Result<List<Stock>> = runCatching {
        val response = api.getStock()
        val stocks = response.map { it.toDomain() }
        stockDao.deleteAllStock(vendorId)
        stockDao.insertAllStock(stocks.map { it.toDbEntity() })
        stocks
    }

    // ─── Write operations (API first, then sync local) ──────────

    override suspend fun addStockItem(
        itemId: String?,
        itemName: String,
        quantity: Double,
        minQuantity: Double,
        costPrice: Double,
        unit: String,
        baseUnit: String,
        conversionRate: Double,
        alertEnabled: Boolean,
    ): Result<Stock> = runCatching {
        val response = api.createStock(
            CreateStockRequest(
                itemId = itemId,
                itemName = if (itemId == null) itemName else null,
                quantity = quantity,
                minQuantity = minQuantity,
                costPrice = costPrice,
                unit = unit,
                baseUnit = baseUnit,
                conversionRate = conversionRate,
                alertEnabled = alertEnabled,
            )
        )
        val stock = response.toDomain()
        stockDao.insertStock(stock.toDbEntity())
        stock
    }

    override suspend fun updateStockItem(
        id: String,
        itemName: String?,
        quantity: Double?,
        minQuantity: Double?,
        costPrice: Double?,
        unit: String?,
        baseUnit: String?,
        alertEnabled: Boolean?,
    ): Result<Stock> = runCatching {
        val response = api.updateStock(
            id,
            UpdateStockRequest(
                itemName = itemName,
                quantity = quantity,
                minQuantity = minQuantity,
                costPrice = costPrice,
                unit = unit,
                baseUnit = baseUnit,
                alertEnabled = alertEnabled,
            )
        )
        val stock = response.toDomain()
        stockDao.insertStock(stock.toDbEntity())
        stock
    }

    override suspend fun addQuantity(stockId: String, quantity: Double, note: String?): Result<Stock> = runCatching {
        val response = api.addStockQuantity(stockId, AdjustQuantityRequest(quantity, note))
        val stock = response.toDomain()
        stockDao.insertStock(stock.toDbEntity())
        stock
    }

    override suspend fun deductQuantity(
        stockId: String, quantity: Double, orderId: String?, note: String?
    ): Result<Stock> = runCatching {
        val response = api.deductStockQuantity(stockId, AdjustQuantityRequest(quantity, note))
        val stock = response.toDomain()
        stockDao.insertStock(stock.toDbEntity())
        stock
    }

    override suspend fun deductByItemId(
        itemId: String, quantity: Double, orderId: String?
    ): Result<Unit> = runCatching {
        refreshStock()
    }

    override suspend fun deleteStockItem(id: String): Result<Unit> = runCatching {
        api.deleteStock(id)
        stockDao.deleteTransactionsByStockId(id)
        stockDao.deleteStock(id)
    }

    // ─── Stock Analytics ────────────────────────────────────────

    override suspend fun getAllTransactions(
        stockId: String?,
        type: String?,
        from: Long?,
        to: Long?,
        limit: Int
    ): Result<List<StockTransaction>> = runCatching {
        api.getStockTransactions(stockId, type, from, to, limit).map { it.toDomain() }
    }

    override suspend fun getStockAlerts(): Result<List<StockAlert>> = runCatching {
        api.getStockAlerts().map { it.toDomain() }
    }

    override suspend fun getAnalyticsSummary(): Result<StockSummary> = runCatching {
        api.getStockAnalyticsSummary().toDomain()
    }
}
