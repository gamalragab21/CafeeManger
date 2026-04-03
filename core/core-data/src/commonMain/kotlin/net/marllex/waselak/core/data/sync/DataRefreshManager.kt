package net.marllex.waselak.core.data.sync

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.CategoryRepository
import net.marllex.waselak.core.domain.repository.CustomerRepository
import net.marllex.waselak.core.domain.repository.ItemRepository
import net.marllex.waselak.core.domain.repository.OfferRepository
import net.marllex.waselak.core.domain.repository.StockRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.common.logging.AppLogger

/**
 * Refreshes ALL cached data from the server after a sync completes.
 * This ensures local data is up-to-date with any changes made while offline
 * (e.g., manager changed menu items, admin changed feature flags, etc.)
 *
 * Each refresh is independent — if one fails, the others still complete.
 */
class DataRefreshManager(
    private val vendorRepository: VendorRepository,
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val customerRepository: CustomerRepository,
    private val stockRepository: StockRepository,
    private val offerRepository: OfferRepository,
    private val workerRepository: WorkerRepository,
) {
    companion object {
        private const val TAG = "DataRefresh"
    }

    suspend fun refreshAll() {
        AppLogger.i(TAG, "Starting full data refresh after sync...")
        var successCount = 0
        var failCount = 0

        coroutineScope {
            // Vendor settings first (feature flags, tax, stock mode, etc.)
            launch {
                vendorRepository.refreshVendor()
                    .onSuccess { AppLogger.d(TAG, "Vendor refreshed"); successCount++ }
                    .onFailure { AppLogger.e(TAG, "Vendor refresh failed", it); failCount++ }
            }
            // Menu data
            launch {
                categoryRepository.refreshCategories()
                    .onSuccess { AppLogger.d(TAG, "Categories refreshed: ${it.size}"); successCount++ }
                    .onFailure { AppLogger.e(TAG, "Categories refresh failed", it); failCount++ }
            }
            launch {
                itemRepository.refreshItems()
                    .onSuccess { AppLogger.d(TAG, "Items refreshed: ${it.size}"); successCount++ }
                    .onFailure { AppLogger.e(TAG, "Items refresh failed", it); failCount++ }
            }
            // Customers
            launch {
                customerRepository.refreshCustomers()
                    .onSuccess { AppLogger.d(TAG, "Customers refreshed: ${it.size}"); successCount++ }
                    .onFailure { AppLogger.e(TAG, "Customers refresh failed", it); failCount++ }
            }
            // Stock
            launch {
                stockRepository.refreshStock()
                    .onSuccess { AppLogger.d(TAG, "Stock refreshed: ${it.size}"); successCount++ }
                    .onFailure { AppLogger.e(TAG, "Stock refresh failed", it); failCount++ }
            }
            // Offers
            launch {
                offerRepository.refreshOffers()
                    .onSuccess { AppLogger.d(TAG, "Offers refreshed: ${it.size}"); successCount++ }
                    .onFailure { AppLogger.e(TAG, "Offers refresh failed", it); failCount++ }
            }
            // Workers
            launch {
                workerRepository.refreshWorkers()
                    .onSuccess { AppLogger.d(TAG, "Workers refreshed: ${it.size}"); successCount++ }
                    .onFailure { AppLogger.e(TAG, "Workers refresh failed", it); failCount++ }
            }
        }

        AppLogger.i(TAG, "Data refresh complete: $successCount succeeded, $failCount failed")
    }
}
