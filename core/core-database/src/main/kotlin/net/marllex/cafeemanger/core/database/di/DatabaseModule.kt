package net.marllex.cafeemanger.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.marllex.cafeemanger.core.database.CafeeMangerDatabase
import net.marllex.cafeemanger.core.database.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add new columns to workers table for PIN and QR code support
            db.execSQL("ALTER TABLE workers ADD COLUMN has_pin INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE workers ADD COLUMN qr_code_version INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE workers ADD COLUMN pin_updated_at INTEGER")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CafeeMangerDatabase = Room.databaseBuilder(
        context,
        CafeeMangerDatabase::class.java,
        "cafeemanger.db"
    )
        .addMigrations(MIGRATION_10_11)
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideVendorDao(db: CafeeMangerDatabase): VendorDao = db.vendorDao()

    @Provides
    fun provideUserDao(db: CafeeMangerDatabase): UserDao = db.userDao()

    @Provides
    fun provideCategoryDao(db: CafeeMangerDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideItemDao(db: CafeeMangerDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideTableDao(db: CafeeMangerDatabase): TableDao = db.tableDao()

    @Provides
    fun provideOrderDao(db: CafeeMangerDatabase): OrderDao = db.orderDao()

    @Provides
    fun provideStockDao(db: CafeeMangerDatabase): StockDao = db.stockDao()

    @Provides
    fun provideWorkerDao(db: CafeeMangerDatabase): WorkerDao = db.workerDao()
}
