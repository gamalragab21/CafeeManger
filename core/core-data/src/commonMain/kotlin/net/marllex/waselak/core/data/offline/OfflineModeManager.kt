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
    private val connectivityChecker: ConnectivityChecker,
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

    /**
     * Effective "is the app online" signal.
     *
     * We are *online* if EITHER:
     *   • the OS reports a network capability AND has not been refuted
     *     by the backend probe yet, OR
     *   • the backend was reachable on the last probe.
     *
     * We are *offline* only when BOTH signals agree the link is dead.
     * This forgiving union fixes the prior false-offline behaviour
     * where a single failed probe (or a network that blocks the
     * `8.8.8.8` desktop probe) would flash the "no internet" dialog
     * even though the app could happily reach its backend.
     *
     * Recovery is fast: either signal flipping to true brings us back
     * online immediately.
     */
    val isOnline: StateFlow<Boolean> = combine(
        networkMonitor.isOnline,
        connectivityChecker.isOnline,
    ) { osOnline, backendReachable ->
        osOnline || backendReachable
    }.stateIn(scope, SharingStarted.Eagerly, true)

    /** Whether offline mode is enabled by the manager. */
    val offlineModeEnabled: StateFlow<Boolean> get() = _offlineModeEnabled

    /**
     * True when: vendor has enabled offline mode AND device has no network
     * AND user has explicitly confirmed they want to work offline.
     * Used by OrderRepository to decide: save locally vs call API.
     */
    val isOfflineActive: StateFlow<Boolean> = combine(
        _offlineModeEnabled,
        isOnline,
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
        isOnline,
        _userAcceptedOffline,
    ) { enabled, online, accepted ->
        enabled && !online && !accepted
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val pendingCount: StateFlow<Long> = pendingSyncDao.getPendingCount()
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    /**
     * Probe the backend right now. Useful for "Retry" buttons on the
     * offline dialog so the user doesn't have to wait the 15-s polling
     * cycle after fixing their network.
     */
    suspend fun recheckNow(): Boolean = connectivityChecker.checkNow()

    init {
        scope.launch {
            vendorRepository.getMyVendor().collect { vendor ->
                _offlineModeEnabled.value = vendor?.enableOfflineMode ?: false
            }
        }
        // Auto-reset user acceptance when connectivity is restored
        scope.launch {
            isOnline.collect { online ->
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
