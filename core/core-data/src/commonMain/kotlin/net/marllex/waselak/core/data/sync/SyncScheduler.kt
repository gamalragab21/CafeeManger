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
    private val attendanceSyncManager: AttendanceSyncManager,
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

        // Trigger 3: Startup — sync pending items if already online
        scope.launch {
            delay(5_000) // let app fully initialize
            if (networkMonitor.isOnline.value) {
                triggerSync("startup")
            }
        }

        // Trigger 4: Periodic retry every 3 minutes
        scope.launch {
            while (true) {
                delay(3 * 60_000L)
                if (networkMonitor.isOnline.value) {
                    triggerSync("periodic")
                }
            }
        }
    }

    suspend fun triggerManualSync(): Int {
        return triggerSync("manual")
    }

    private suspend fun triggerSync(reason: String): Int {
        return try {
            val count = syncService.syncAll()
            // Also sync pending attendance records
            try {
                attendanceSyncManager.syncPendingRecords()
            } catch (_: Exception) {
                // Non-critical: attendance sync failure shouldn't block
            }
            _lastSyncResult.value = "Synced $count items ($reason)"
            count
        } catch (e: Exception) {
            _lastSyncResult.value = "Sync failed: ${e.message}"
            0
        }
    }
}
