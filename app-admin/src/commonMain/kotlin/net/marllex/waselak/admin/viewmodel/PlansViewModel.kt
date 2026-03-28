package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.network.PlanDto
import net.marllex.waselak.admin.network.PlanUpdateDto
import net.marllex.waselak.admin.util.UiMessage
import waselak.app_admin.generated.resources.Res
import waselak.app_admin.generated.resources.*
import net.marllex.waselak.core.common.crash.CrashReporter

class PlansViewModel(private val apiClient: AdminApiClient) : ViewModel() {
    private val _plans = MutableStateFlow<List<PlanDto>>(emptyList())
    val plans: StateFlow<List<PlanDto>> = _plans.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun loadPlans() {
        viewModelScope.launch {
            _isLoading.value = true
            _plans.value = apiClient.getPlans()
            _isLoading.value = false
        }
    }

    fun updatePlan(planName: String, update: PlanUpdateDto) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = apiClient.updatePlan(planName, update)
            if (result != null) {
                _message.value = UiMessage.Resource(Res.string.plan_updated_format, listOf(planName), isSuccess = true)
                loadPlans()
            } else {
                _message.value = UiMessage.Resource(Res.string.plan_update_failed)
            }
            _isLoading.value = false
        }
    }
}
