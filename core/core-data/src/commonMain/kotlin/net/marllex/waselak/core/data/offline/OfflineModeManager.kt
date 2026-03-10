package net.marllex.waselak.core.data.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * Whether the user has explicitly accepted to work offline in this session.
     * Resets automatically when connectivity is restored.
     */
    private val _userAcceptedOffline = MutableStateFlow(false)
    val userAcceptedOffline: StateFlow<Boolean> = _userAcceptedOffline.asStateFlow()

    /** Whether the device has network connectivity (device-level check). */
    val isOnline: StateFlow<Boolean> get() = networkMonitor.isOnline

    /** Whether offline mode is enabled by the manager. */
    val offlineModeEnabled: StateFlow<Boolean> get() = _offlineModeEnabled

    /**
     * True when: vendor has enabled offline mode AND device has no network
     * AND user has explicitly confirmed they want to work offline.
     * Used by OrderRepository to decide: save locally vs call API.
     */
    val isOfflineActive: StateFlow<Boolean> = combine(
        _offlineModeEnabled,
        networkMonitor.isOnline,
        _userAcceptedOffline,
    ) { enabled, online, accepted ->
        enabled && !online && accepted
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * True when device is offline and offline mode is enabled by the vendor,
     * but user has NOT yet confirmed. Used by UI to show confirmation dialog.
     */
    val needsOfflineConfirmation: StateFlow<Boolean> = combine(
        _offlineModeEnabled,
        networkMonitor.isOnline,
        _userAcceptedOffline,
    ) { enabled, online, accepted ->
        enabled && !online && !accepted
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val pendingCount: StateFlow<Long> = pendingSyncDao.getPendingCount()
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    init {
        scope.launch {
            vendorRepository.getMyVendor().collect { vendor ->
                _offlineModeEnabled.value = vendor?.enableOfflineMode ?: false
            }
        }
        // Auto-reset user acceptance when connectivity is restored
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    _userAcceptedOffline.value = false
                }
            }
        }
    }

    /** Called when user explicitly confirms they want to work offline. */
    fun confirmOfflineMode() {
        _userAcceptedOffline.value = true
    }

    /** Called when user declines offline mode. */
    fun declineOfflineMode() {
        _userAcceptedOffline.value = false
    }
}
