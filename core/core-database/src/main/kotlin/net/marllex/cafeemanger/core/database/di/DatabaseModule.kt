package net.marllex.cafeemanger.core.database.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CafeeMangerDatabase = Room.databaseBuilder(
        context,
        CafeeMangerDatabase::class.java,
        "cafeemanger.db"
    )
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
}
