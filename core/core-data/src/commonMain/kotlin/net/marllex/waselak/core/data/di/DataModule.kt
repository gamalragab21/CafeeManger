package net.marllex.waselak.core.data.di

import net.marllex.waselak.core.data.offline.ConnectivityChecker
import net.marllex.waselak.core.data.offline.OfflineModeManager
import net.marllex.waselak.core.data.repository.*
import net.marllex.waselak.core.data.sync.AttendanceSyncManager
import net.marllex.waselak.core.data.sync.DataRefreshManager
import net.marllex.waselak.core.data.sync.SyncScheduler
import net.marllex.waselak.core.data.sync.SyncService
import net.marllex.waselak.core.domain.repository.*
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single<VendorRepository> { VendorRepositoryImpl(get(), get(), get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get(), get(), get()) }
    single<ItemRepository> { ItemRepositoryImpl(get(), get(), get(), get()) }
    single<TableRepository> { TableRepositoryImpl(get(), get(), get()) }
    single<ReservationRepository> { ReservationRepositoryImpl(get(), get(), get()) }
    single<OrderRepository> { OrderRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<UserManagementRepository> { UserManagementRepositoryImpl(get(), get(), get()) }
    single<AnalyticsRepository> { AnalyticsRepositoryImpl(get()) }
    single<TaxPlaceRepository> { TaxPlaceRepositoryImpl(get()) }
    single<StockRepository> { StockRepositoryImpl(get(), get(), get()) }
    single<RecipeRepository> { RecipeRepositoryImpl(get(), get(), get()) }
    single<WorkerRepository> { WorkerRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<CustomerRepository> { CustomerRepositoryImpl(get(), get(), get()) }
    single<OfferRepository> { OfferRepositoryImpl(get(), get(), get()) }
    single<KdsRepository> { KdsRepositoryImpl(get()) }
    single<CashDrawerRepository> { CashDrawerRepositoryImpl(get()) }
    single<SplitPaymentRepository> { SplitPaymentRepositoryImpl(get()) }
    single<PrescriptionRepository> { PrescriptionRepositoryImpl(get()) }
    single<DrugInteractionRepository> { DrugInteractionRepositoryImpl(get()) }
    single<CustomerCreditRepository> { CustomerCreditRepositoryImpl(get()) }
    single<InstallmentRepository> { InstallmentRepositoryImpl(get()) }
    single<ScheduledOrderRepository> { ScheduledOrderRepositoryImpl(get()) }
    single<SupplierRepository> { SupplierRepositoryImpl(get()) }
    single<ReturnRepository> { ReturnRepositoryImpl(get()) }
    single<NotificationRepository> { NotificationRepositoryImpl(get()) }
    single(createdAtStart = true) { AttendanceSyncManager(get(), get(), get(), get()) }
    single { SyncService(get(), get(), get()) }
    single { DataRefreshManager(get(), get(), get(), get(), get(), get(), get()) }
    single(createdAtStart = true) { SyncScheduler(get(), get(), get(), get(), get()) }
    single { ConnectivityChecker(get(named("baseUrl")), get()) }
    single { OfflineModeManager(get(), get(), get()) }
}
