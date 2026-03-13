package net.marllex.waselak.cashier.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.NotificationRepository
import net.marllex.waselak.core.model.AppNotification
import net.marllex.waselak.core.model.NotificationCount

class CashierNotificationsViewModel(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    data class UiState(
        val notifications: List<AppNotification> = emptyList(),
        val count: NotificationCount = NotificationCount(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            notificationRepository.getNotifications()
                .onSuccess { list -> _uiState.update { it.copy(notifications = list, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
        viewModelScope.launch {
            notificationRepository.getCount()
                .onSuccess { c -> _uiState.update { it.copy(count = c) } }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            notificationRepository.markRead(id).onSuccess { load() }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            notificationRepository.markAllRead().onSuccess { load() }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(id).onSuccess { load() }
        }
    }
}
