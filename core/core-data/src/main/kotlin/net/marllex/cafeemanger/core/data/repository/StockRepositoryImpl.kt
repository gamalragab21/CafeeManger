package net.marllex.cafeemanger.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.cafeemanger.core.database.dao.StockDao
import net.marllex.cafeemanger.core.database.mapper.toDomain
import net.marllex.cafeemanger.core.database.mapper.toEntity
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.domain.repository.StockRepository
import net.marllex.cafeemanger.core.model.Stock
import net.marllex.cafeemanger.core.model.StockTransaction
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.AdjustQuantityRequest
import net.marllex.cafeemanger.core.network.dto.CreateStockRequest
import net.marllex.cafeemanger.core.network.dto.UpdateStockRequest
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class StockRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
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
        stockDao.insertAllStock(stocks.map { it.toEntity() })
        stocks
    }

    // ─── Write operations (API first, then sync local) ──────────

    override suspend fun addStockItem(
        itemId: String,
        itemName: String,
        quantity: Int,
        minQuantity: Int,
        costPrice: Double,
        unit: String,
    ): Result<Stock> = runCatching {
        val response = api.createStock(
            CreateStockRequest(
                itemId = itemId,
                quantity = quantity,
                minQuantity = minQuantity,
                costPrice = costPrice,
                unit = unit,
            )
        )
        val stock = response.toDomain()
        stockDao.insertStock(stock.toEntity())
        stock
    }

    override suspend fun updateStockItem(
        id: String,
        quantity: Int?,
        minQuantity: Int?,
        costPrice: Double?,
        unit: String?,
    ): Result<Stock> = runCatching {
        val response = api.updateStock(
            id,
            UpdateStockRequest(
                quantity = quantity,
                minQuantity = minQuantity,
                costPrice = costPrice,
                unit = unit,
            )
        )
        val stock = response.toDomain()
        stockDao.insertStock(stock.toEntity())
        stock
    }

    override suspend fun addQuantity(stockId: String, quantity: Int, note: String?): Result<Stock> = runCatching {
        val response = api.addStockQuantity(stockId, AdjustQuantityRequest(quantity, note))
        val stock = response.toDomain()
        stockDao.insertStock(stock.toEntity())
        stock
    }

    override suspend fun deductQuantity(
        stockId: String, quantity: Int, orderId: String?, note: String?
    ): Result<Stock> = runCatching {
        val response = api.deductStockQuantity(stockId, AdjustQuantityRequest(quantity, note))
        val stock = response.toDomain()
        stockDao.insertStock(stock.toEntity())
        stock
    }

    override suspend fun deductByItemId(
        itemId: String, quantity: Int, orderId: String?
    ): Result<Unit> = runCatching {
        // This is handled server-side during order creation now
        // Just refresh stock to get the latest quantities
        refreshStock()
    }

    override suspend fun deleteStockItem(id: String): Result<Unit> = runCatching {
        api.deleteStock(id)
        stockDao.deleteTransactionsByStockId(id)
        stockDao.deleteStock(id)
    }
}
