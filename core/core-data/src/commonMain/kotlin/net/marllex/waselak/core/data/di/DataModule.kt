package net.marllex.waselak.core.data.di

import net.marllex.waselak.core.data.repository.*
import net.marllex.waselak.core.domain.repository.*
import org.koin.dsl.module

val dataModule = module {
    single<VendorRepository> { VendorRepositoryImpl(get(), get(), get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get(), get(), get()) }
    single<ItemRepository> { ItemRepositoryImpl(get(), get(), get()) }
    single<TableRepository> { TableRepositoryImpl(get(), get(), get()) }
    single<OrderRepository> { OrderRepositoryImpl(get(), get(), get()) }
    single<UserManagementRepository> { UserManagementRepositoryImpl(get(), get(), get()) }
    single<AnalyticsRepository> { AnalyticsRepositoryImpl(get()) }
    single<TaxPlaceRepository> { TaxPlaceRepositoryImpl(get()) }
    single<StockRepository> { StockRepositoryImpl(get(), get(), get()) }
    single<RecipeRepository> { RecipeRepositoryImpl(get(), get(), get()) }
    single<WorkerRepository> { WorkerRepositoryImpl(get(), get(), get(), get()) }
    single<CustomerRepository> { CustomerRepositoryImpl(get(), get(), get()) }
}
