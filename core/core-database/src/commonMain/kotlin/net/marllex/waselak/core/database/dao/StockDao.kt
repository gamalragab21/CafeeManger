package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Stock
import net.marllex.waselak.core.database.Stock_transactions

class StockDao(private val db: WaselakDatabase) {
    private val stockQueries get() = db.stockQueries
    private val transactionQueries get() = db.stockTransactionQueries

    // ─── Stock Queries ──────────────────────────────────────────────
    fun getAllStock(vendorId: String): Flow<List<Stock>> =
        stockQueries.getAllStock(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getLowStock(vendorId: String): Flow<List<Stock>> =
        stockQueries.getLowStock(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getOutOfStock(vendorId: String): Flow<List<Stock>> =
        stockQueries.getOutOfStock(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getStockById(id: String): Flow<Stock?> =
        stockQueries.getStockById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun getStockByIdSync(id: String): Stock? =
        stockQueries.getStockById(id).executeAsOneOrNull()

    fun getStockByItemId(vendorId: String, itemId: String): Flow<Stock?> =
        stockQueries.getStockByItemId(vendorId, itemId).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun getStockByItemIdSync(vendorId: String, itemId: String): Stock? =
        stockQueries.getStockByItemId(vendorId, itemId).executeAsOneOrNull()

    suspend fun insertStock(stock: Stock) {
        stockQueries.insertStock(
            id = stock.id,
            vendor_id = stock.vendor_id,
            item_id = stock.item_id,
            item_name = stock.item_name,
            quantity = stock.quantity,
            min_quantity = stock.min_quantity,
            cost_price = stock.cost_price,
            unit = stock.unit,
            base_unit = stock.base_unit,
            conversion_rate = stock.conversion_rate,
            is_menu_item = stock.is_menu_item,
            alert_enabled = stock.alert_enabled,
            last_updated_at = stock.last_updated_at
        )
    }

    suspend fun insertAllStock(stocks: List<Stock>) {
        db.transaction {
            stocks.forEach { stock ->
                stockQueries.insertStock(
                    id = stock.id,
                    vendor_id = stock.vendor_id,
                    item_id = stock.item_id,
                    item_name = stock.item_name,
                    quantity = stock.quantity,
                    min_quantity = stock.min_quantity,
                    cost_price = stock.cost_price,
                    unit = stock.unit,
                    base_unit = stock.base_unit,
                    conversion_rate = stock.conversion_rate,
                    is_menu_item = stock.is_menu_item,
                    alert_enabled = stock.alert_enabled,
                    last_updated_at = stock.last_updated_at
                )
            }
        }
    }

    suspend fun updateStockQuantity(id: String, quantity: Double, updatedAt: Long) {
        stockQueries.updateStockQuantity(quantity, updatedAt, id)
    }

    suspend fun deleteStock(id: String) {
        stockQueries.deleteStock(id)
    }

    suspend fun deleteAllStock(vendorId: String) {
        stockQueries.deleteAllStock(vendorId)
    }

    // ─── Stock Transaction Queries ──────────────────────────────────
    fun getTransactionsByStockId(stockId: String): Flow<List<Stock_transactions>> =
        transactionQueries.getTransactionsByStockId(stockId).asFlow().mapToList(Dispatchers.Default)

    fun getRecentTransactions(stockId: String, limit: Long = 20): Flow<List<Stock_transactions>> =
        transactionQueries.getRecentTransactions(stockId, limit).asFlow().mapToList(Dispatchers.Default)

    suspend fun insertTransaction(transaction: Stock_transactions) {
        transactionQueries.insertTransaction(
            id = transaction.id,
            stock_id = transaction.stock_id,
            item_name = transaction.item_name,
            type = transaction.type,
            quantity = transaction.quantity,
            previous_quantity = transaction.previous_quantity,
            order_id = transaction.order_id,
            recipe_id = transaction.recipe_id,
            note = transaction.note,
            created_at = transaction.created_at
        )
    }

    suspend fun deleteTransactionsByStockId(stockId: String) {
        transactionQueries.deleteTransactionsByStockId(stockId)
    }
}
