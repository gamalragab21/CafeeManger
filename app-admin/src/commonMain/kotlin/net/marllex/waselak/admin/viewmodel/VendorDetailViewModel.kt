package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.network.UpdateVendorRequest
import net.marllex.waselak.admin.network.VendorDetailDto

class VendorDetailViewModel(private val apiClient: AdminApiClient) : ViewModel() {
    private val _detail = MutableStateFlow<VendorDetailDto?>(null)
    val detail: StateFlow<VendorDetailDto?> = _detail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun loadVendorDetail(vendorId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = apiClient.getVendorDetail(vendorId)
                if (result != null) {
                    _detail.value = result
                } else {
                    _error.value = "Failed to load vendor details"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateVendor(vendorId: String, request: UpdateVendorRequest) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val success = apiClient.updateVendor(vendorId, request)
                if (success) {
                    _message.value = "Vendor updated successfully"
                    // Reload detail to reflect changes
                    loadVendorDetail(vendorId)
                } else {
                    _message.value = "Failed to update vendor"
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun retry(vendorId: String) {
        loadVendorDetail(vendorId)
    }
}
