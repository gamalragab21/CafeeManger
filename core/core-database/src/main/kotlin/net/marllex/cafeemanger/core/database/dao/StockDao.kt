package net.marllex.cafeemanger.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.database.entity.StockEntity
import net.marllex.cafeemanger.core.database.entity.StockTransactionEntity

@Dao
interface StockDao {

    // ─── Stock Queries ──────────────────────────────────────────────
    @Query("SELECT * FROM stock WHERE vendor_id = :vendorId ORDER BY item_name ASC")
    fun getAllStock(vendorId: String): Flow<List<StockEntity>>

    @Query("SELECT * FROM stock WHERE vendor_id = :vendorId AND quantity <= min_quantity AND quantity > 0 ORDER BY quantity ASC")
    fun getLowStock(vendorId: String): Flow<List<StockEntity>>

    @Query("SELECT * FROM stock WHERE vendor_id = :vendorId AND quantity <= 0 ORDER BY item_name ASC")
    fun getOutOfStock(vendorId: String): Flow<List<StockEntity>>

    @Query("SELECT * FROM stock WHERE id = :id")
    fun getStockById(id: String): Flow<StockEntity?>

    @Query("SELECT * FROM stock WHERE id = :id LIMIT 1")
    suspend fun getStockByIdSync(id: String): StockEntity?

    @Query("SELECT * FROM stock WHERE vendor_id = :vendorId AND item_id = :itemId")
    fun getStockByItemId(vendorId: String, itemId: String): Flow<StockEntity?>

    @Query("SELECT * FROM stock WHERE vendor_id = :vendorId AND item_id = :itemId LIMIT 1")
    suspend fun getStockByItemIdSync(vendorId: String, itemId: String): StockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStock(stock: StockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStock(stocks: List<StockEntity>)

    @Update
    suspend fun updateStock(stock: StockEntity)

    @Query("UPDATE stock SET quantity = :quantity, last_updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStockQuantity(id: String, quantity: Int, updatedAt: Long)

    @Query("DELETE FROM stock WHERE id = :id")
    suspend fun deleteStock(id: String)

    @Query("DELETE FROM stock WHERE vendor_id = :vendorId")
    suspend fun deleteAllStock(vendorId: String)

    // ─── Stock Transaction Queries ──────────────────────────────────
    @Query("SELECT * FROM stock_transactions WHERE stock_id = :stockId ORDER BY created_at DESC")
    fun getTransactionsByStockId(stockId: String): Flow<List<StockTransactionEntity>>

    @Query("SELECT * FROM stock_transactions WHERE stock_id = :stockId ORDER BY created_at DESC LIMIT :limit")
    fun getRecentTransactions(stockId: String, limit: Int = 20): Flow<List<StockTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: StockTransactionEntity)

    @Query("DELETE FROM stock_transactions WHERE stock_id = :stockId")
    suspend fun deleteTransactionsByStockId(stockId: String)
}
