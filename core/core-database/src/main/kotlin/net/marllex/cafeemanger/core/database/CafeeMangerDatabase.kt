package net.marllex.cafeemanger.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import net.marllex.cafeemanger.core.database.dao.*
import net.marllex.cafeemanger.core.database.entity.*

@Database(
    entities = [
        VendorEntity::class,
        UserEntity::class,
        CategoryEntity::class,
        ItemEntity::class,
        TableEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        StockEntity::class,
        StockTransactionEntity::class,
        WorkerEntity::class,
        WorkerRoleEntity::class,
        AttendanceEntity::class,
        SalaryPaymentEntity::class,
    ],
    version = 8, // Added isMenuItem, alertEnabled to StockEntity, itemName to StockTransactionEntity
    exportSchema = false
)
abstract class CafeeMangerDatabase : RoomDatabase() {
    abstract fun vendorDao(): VendorDao
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun tableDao(): TableDao
    abstract fun orderDao(): OrderDao
    abstract fun stockDao(): StockDao
    abstract fun workerDao(): WorkerDao
}
