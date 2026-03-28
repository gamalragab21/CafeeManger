package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.network.AdminSendNotificationRequest
import net.marllex.waselak.admin.network.VendorDto
import net.marllex.waselak.core.common.crash.CrashReporter

class AdminNotificationsViewModel(
    private val apiClient: AdminApiClient,
) : ViewModel() {

    data class UiState(
        val vendors: List<VendorDto> = emptyList(),
        val selectedVendorIds: Set<String> = emptySet(),
        val allVendors: Boolean = true,
        val type: String = "ADMIN_ANNOUNCEMENT",     // ADMIN_ANNOUNCEMENT or SYSTEM_UPDATE
        val title: String = "",
        val body: String = "",
        val actionUrl: String = "",                    // Download link for SYSTEM_UPDATE
        val platform: String? = null,                  // null=All, ANDROID, DESKTOP, IOS
        val priority: String = "NORMAL",               // NORMAL, HIGH, URGENT
        val isSending: Boolean = false,
        val isLoadingVendors: Boolean = true,
        val successMessage: String? = null,
        val error: String? = null,
        val showConfirmDialog: Boolean = false,
    ) {
        val canSend: Boolean
            get() = title.isNotBlank() && body.isNotBlank() && !isSending &&
                (allVendors || selectedVendorIds.isNotEmpty())

        val targetCount: String
            get() = if (allVendors) "all vendors" else "${selectedVendorIds.size} vendor(s)"
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadVendors()
    }

    private fun loadVendors() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVendors = true) }
            val vendors = apiClient.getVendors()
            _uiState.update { it.copy(vendors = vendors, isLoadingVendors = false) }
        }
    }

    fun onTypeChange(type: String) {
        _uiState.update {
            it.copy(
                type = type,
                // Clear SYSTEM_UPDATE fields if switching away
                actionUrl = if (type == "SYSTEM_UPDATE") it.actionUrl else "",
                platform = if (type == "SYSTEM_UPDATE") it.platform else null,
            )
        }
    }

    fun onTitleChange(v: String) { _uiState.update { it.copy(title = v) } }
    fun onBodyChange(v: String) { _uiState.update { it.copy(body = v) } }
    fun onActionUrlChange(v: String) { _uiState.update { it.copy(actionUrl = v) } }
    fun onPlatformChange(v: String?) { _uiState.update { it.copy(platform = v) } }
    fun onPriorityChange(v: String) { _uiState.update { it.copy(priority = v) } }

    fun toggleAllVendors(all: Boolean) {
        _uiState.update { it.copy(allVendors = all, selectedVendorIds = if (all) emptySet() else it.selectedVendorIds) }
    }

    fun toggleVendor(vendorId: String) {
        _uiState.update { state ->
            val newIds = state.selectedVendorIds.toMutableSet()
            if (vendorId in newIds) newIds.remove(vendorId) else newIds.add(vendorId)
            state.copy(selectedVendorIds = newIds, allVendors = false)
        }
    }

    fun showConfirm() { _uiState.update { it.copy(showConfirmDialog = true) } }
    fun dismissConfirm() { _uiState.update { it.copy(showConfirmDialog = false) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun send() {
        val s = _uiState.value
        if (!s.canSend) return
        _uiState.update { it.copy(showConfirmDialog = false, isSending = true, error = null) }

        viewModelScope.launch {
            val request = AdminSendNotificationRequest(
                vendor_ids = if (s.allVendors) null else s.selectedVendorIds.toList(),
                type = s.type,
                title = s.title,
                body = s.body,
                action_url = s.actionUrl.ifBlank { null },
                platform = s.platform,
                priority = s.priority,
            )
            val success = apiClient.sendNotification(request)
            if (success) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        successMessage = "sent",
                        // Reset form
                        title = "",
                        body = "",
                        actionUrl = "",
                        type = "ADMIN_ANNOUNCEMENT",
                        platform = null,
                        priority = "NORMAL",
                        allVendors = true,
                        selectedVendorIds = emptySet(),
                    )
                }
            } else {
                _uiState.update { it.copy(isSending = false, error = "Failed to send notification") }
            }
        }
    }
}
