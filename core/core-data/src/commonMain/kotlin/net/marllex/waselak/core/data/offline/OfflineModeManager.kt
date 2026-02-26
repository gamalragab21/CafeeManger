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

class OfflineModeManager(
    private val connectivityChecker: ConnectivityChecker,
    private val vendorRepository: VendorRepository,
    private val pendingSyncDao: PendingSyncDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _offlineModeEnabled = MutableStateFlow(false)

    val isOnline: StateFlow<Boolean> get() = connectivityChecker.isOnline

    val isOfflineActive: StateFlow<Boolean> = combine(
        _offlineModeEnabled,
        connectivityChecker.isOnline,
    ) { enabled, online ->
        enabled && !online
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val pendingCount: StateFlow<Long> = pendingSyncDao.getPendingCount()
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    init {
        scope.launch {
            vendorRepository.getMyVendor().collect { vendor ->
                _offlineModeEnabled.value = vendor?.offlineModeEnabled ?: false
            }
        }
    }

    suspend fun checkConnectivity(): Boolean = connectivityChecker.checkNow()
}
