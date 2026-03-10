package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.network.CreateVendorRequest
import net.marllex.waselak.admin.network.PlanDto
import net.marllex.waselak.admin.network.VendorDto

class VendorsViewModel(private val apiClient: AdminApiClient) : ViewModel() {
    private val _vendors = MutableStateFlow<List<VendorDto>>(emptyList())
    val vendors: StateFlow<List<VendorDto>> = _vendors.asStateFlow()

    private val _plans = MutableStateFlow<List<PlanDto>>(emptyList())
    val plans: StateFlow<List<PlanDto>> = _plans.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun loadVendors() {
        viewModelScope.launch {
            _isLoading.value = true
            _vendors.value = apiClient.getVendors()
            _plans.value = apiClient.getPlans()
            _isLoading.value = false
        }
    }

    fun suspendVendor(id: String, suspended: Boolean, reason: String?) {
        viewModelScope.launch {
            val success = apiClient.suspendVendor(id, suspended, reason)
            if (success) {
                _message.value = if (suspended) "Vendor suspended" else "Vendor activated"
                loadVendors()
            } else {
                _message.value = "Failed to update vendor"
            }
        }
    }

    fun deleteVendor(id: String) {
        viewModelScope.launch {
            val success = apiClient.deleteVendor(id)
            if (success) {
                _message.value = "Vendor deleted"
                loadVendors()
            } else {
                _message.value = "Failed to delete vendor"
            }
        }
    }

    fun changeVendorPlan(vendorId: String, plan: String) {
        viewModelScope.launch {
            val success = apiClient.changeVendorPlan(vendorId, plan, null)
            if (success) {
                _message.value = "Plan changed to $plan"
                loadVendors()
            } else {
                _message.value = "Failed to change plan"
            }
        }
    }

    fun createVendor(request: CreateVendorRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = apiClient.createVendor(request)
            if (result != null) {
                _message.value = "Vendor '${result.vendor_name}' created successfully"
                loadVendors()
            } else {
                _message.value = "Failed to create vendor"
            }
            _isLoading.value = false
        }
    }
}
