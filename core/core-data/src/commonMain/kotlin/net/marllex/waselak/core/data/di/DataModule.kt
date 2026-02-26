package net.marllex.waselak.core.data.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import net.marllex.waselak.core.data.offline.ConnectivityChecker
import net.marllex.waselak.core.data.offline.OfflineModeManager
import net.marllex.waselak.core.data.repository.*
import net.marllex.waselak.core.data.sync.SyncScheduler
import net.marllex.waselak.core.data.sync.SyncService
import net.marllex.waselak.core.domain.repository.*
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single<VendorRepository> { VendorRepositoryImpl(get(), get(), get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get(), get(), get()) }
    single<ItemRepository> { ItemRepositoryImpl(get(), get(), get()) }
    single<TableRepository> { TableRepositoryImpl(get(), get(), get()) }
    single<OrderRepository> { OrderRepositoryImpl(get(), get(), get(), get(), get()) }
    single<UserManagementRepository> { UserManagementRepositoryImpl(get(), get(), get()) }
    single<AnalyticsRepository> { AnalyticsRepositoryImpl(get()) }
    single<TaxPlaceRepository> { TaxPlaceRepositoryImpl(get()) }
    single<StockRepository> { StockRepositoryImpl(get(), get(), get()) }
    single<RecipeRepository> { RecipeRepositoryImpl(get(), get(), get()) }
    single<WorkerRepository> { WorkerRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<CustomerRepository> { CustomerRepositoryImpl(get(), get(), get()) }

    // Offline infrastructure
    single {
        val baseUrl = get<String>(qualifier = named("baseUrl"))
        val pingClient = HttpClient(get<HttpClientEngineFactory<*>>())
        ConnectivityChecker(baseUrl, pingClient)
    }
    single { OfflineModeManager(get(), get(), get()) }
    single { SyncService(get(), get(), get()) }
    single { SyncScheduler(get(), get(), get()) }
}
