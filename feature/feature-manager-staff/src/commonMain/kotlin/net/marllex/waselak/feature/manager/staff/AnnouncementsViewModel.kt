package net.marllex.waselak.feature.manager.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.model.Announcement
import net.marllex.waselak.core.model.AnnouncementPriority
import net.marllex.waselak.core.model.AnnouncementTarget
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateAnnouncementRequest
import net.marllex.waselak.core.common.logging.AppLogger

class AnnouncementsViewModel constructor(
    private val api: WaselakApiClient,
) : ViewModel() {
    private companion object { private const val TAG = "Announcements" }


    data class UiState(
        val announcements: List<Announcement> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val showCreateDialog: Boolean = false,
        val isSending: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadAnnouncements()
    }

    fun loadAnnouncements() {
        AppLogger.d(TAG, "loadAnnouncements called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.getAnnouncements()
                val announcements = response.map { dto ->
                    Announcement(
                        id = dto.id,
                        vendorId = dto.vendorId,
                        senderId = dto.senderId,
                        senderName = dto.senderName,
                        targetType = try { AnnouncementTarget.valueOf(dto.targetType) } catch (_: Exception) { AnnouncementTarget.ALL },
                        targetUserId = dto.targetUserId,
                        title = dto.title,
                        message = dto.message,
                        priority = try { AnnouncementPriority.valueOf(dto.priority) } catch (_: Exception) { AnnouncementPriority.NORMAL },
                        read = dto.read,
                        createdAt = dto.createdAt,
                    )
                }
                _uiState.update { it.copy(announcements = announcements, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun hideCreateDialog() {
        AppLogger.d(TAG, "hideCreateDialog called")
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun createAnnouncement(
        title: String,
        message: String,
        targetType: String,
        priority: String,
    ) {
        AppLogger.d(TAG, "createAnnouncement called")
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            try {
                api.createAnnouncement(
                    CreateAnnouncementRequest(
                        targetType = targetType,
                        title = title,
                        message = message,
                        priority = priority,
                    )
                )
                _uiState.update { it.copy(showCreateDialog = false, isSending = false) }
                loadAnnouncements()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = e.message) }
            }
        }
    }

    fun markAsRead(announcementId: String) {
        AppLogger.d(TAG, "markAsRead called")
        viewModelScope.launch {
            try {
                api.markAnnouncementRead(announcementId)
                _uiState.update { state ->
                    state.copy(
                        announcements = state.announcements.map {
                            if (it.id == announcementId) it.copy(read = true) else it
                        }
                    )
                }
            } catch (_: Exception) { }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        AppLogger.d(TAG, "deleteAnnouncement called")
        viewModelScope.launch {
            try {
                api.deleteAnnouncement(announcementId)
                _uiState.update { state ->
                    state.copy(
                        announcements = state.announcements.filter { it.id != announcementId }
                    )
                }
            } catch (_: Exception) { }
        }
    }
}
