package net.marllex.waselak.core.data.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.marllex.waselak.core.database.dao.PendingSyncDao
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.network.connectivity.NetworkMonitor

class OfflineModeManager(
    private val networkMonitor: NetworkMonitor,
    private val vendorRepository: VendorRepository,
    private val pendingSyncDao: PendingSyncDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _offlineModeEnabled = MutableStateFlow(false)

    /** Whether the device has network connectivity (device-level check). */
    val isOnline: StateFlow<Boolean> get() = networkMonitor.isOnline

    /** Whether offline mode is enabled by the manager. */
    val offlineModeEnabled: StateFlow<Boolean> get() = _offlineModeEnabled

    /**
     * True when the vendor has enabled offline mode AND the device has no network.
     * Used by OrderRepository to decide: save locally vs call API.
     */
    val isOfflineActive: StateFlow<Boolean> = combine(
        _offlineModeEnabled,
        networkMonitor.isOnline,
    ) { enabled, online ->
        enabled && !online
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val pendingCount: StateFlow<Long> = pendingSyncDao.getPendingCount()
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    init {
        scope.launch {
            vendorRepository.getMyVendor().collect { vendor ->
                _offlineModeEnabled.value = vendor?.enableOfflineMode ?: false
            }
        }
    }
}
