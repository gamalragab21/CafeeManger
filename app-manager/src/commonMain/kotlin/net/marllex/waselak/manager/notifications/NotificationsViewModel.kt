package net.marllex.waselak.manager.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.NotificationRepository
import net.marllex.waselak.core.model.AppNotification
import net.marllex.waselak.core.model.NotificationCount
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Notifications" }


    data class UiState(
        val notifications: List<AppNotification> = emptyList(),
        val count: NotificationCount = NotificationCount(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val showUnreadOnly: Boolean = false,
    ) {
        val displayedNotifications: List<AppNotification>
            get() = if (showUnreadOnly) notifications.filter { it.isUnread } else notifications
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init { load() }

    fun startPolling() {
        AppLogger.d(TAG, "startPolling called")
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(15_000) // refresh every 15 seconds
                silentLoad()
            }
        }
    }

    fun stopPolling() {
        AppLogger.d(TAG, "stopPolling called")
        refreshJob?.cancel()
        refreshJob = null
    }

    fun load() {
        CrashReporter.addBreadcrumb("load() called", "NotificationsViewModel")
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            notificationRepository.getNotifications()
                .onSuccess { list -> _uiState.update { it.copy(notifications = list, isLoading = false) } }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
        viewModelScope.launch {
            notificationRepository.getCount()
                .onSuccess { c -> _uiState.update { it.copy(count = c) } }
        }
    }

    /** Silent refresh without loading indicator — used by auto-polling */
    private fun silentLoad() {
        viewModelScope.launch {
            notificationRepository.getNotifications()
                .onSuccess { list -> _uiState.update { it.copy(notifications = list, error = null) } }
        }
        viewModelScope.launch {
            notificationRepository.getCount()
                .onSuccess { c -> _uiState.update { it.copy(count = c) } }
        }
    }

    fun toggleUnreadFilter() {
        AppLogger.d(TAG, "toggleUnreadFilter called"); _uiState.update { it.copy(showUnreadOnly = !it.showUnreadOnly) } }

    fun markRead(id: String) {
        AppLogger.d(TAG, "markRead called")
        viewModelScope.launch {
            notificationRepository.markRead(id)
                .onSuccess { load() }
        }
    }

    fun markAllRead() {
        AppLogger.d(TAG, "markAllRead called")
        viewModelScope.launch {
            notificationRepository.markAllRead()
                .onSuccess { load() }
        }
    }

    fun delete(id: String) {
        AppLogger.d(TAG, "delete called")
        viewModelScope.launch {
            notificationRepository.deleteNotification(id)
                .onSuccess { load() }
        }
    }
}
