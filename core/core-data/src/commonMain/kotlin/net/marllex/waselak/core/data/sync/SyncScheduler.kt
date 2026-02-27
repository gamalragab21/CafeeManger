package net.marllex.waselak.core.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.network.connectivity.NetworkMonitor
import net.marllex.waselak.core.database.dao.PendingSyncDao

class SyncScheduler(
    private val syncService: SyncService,
    private val networkMonitor: NetworkMonitor,
    private val pendingSyncDao: PendingSyncDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    init {
        // Trigger 1: Auto-sync when connectivity is restored
        scope.launch {
            var wasOffline = false
            networkMonitor.isOnline.collect { online ->
                if (online && wasOffline) {
                    triggerSync("connectivity_restored")
                }
                wasOffline = !online
            }
        }

        // Trigger 2: Midnight sync
        scope.launch {
            while (true) {
                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val minutesUntilMidnight = (24 * 60) - (now.hour * 60 + now.minute)
                delay(minutesUntilMidnight.toLong() * 60_000L)
                triggerSync("midnight")
            }
        }
    }

    suspend fun triggerManualSync(): Int {
        return triggerSync("manual")
    }

    private suspend fun triggerSync(reason: String): Int {
        return try {
            val count = syncService.syncAll()
            _lastSyncResult.value = "Synced $count items ($reason)"
            count
        } catch (e: Exception) {
            _lastSyncResult.value = "Sync failed: ${e.message}"
            0
        }
    }
}
