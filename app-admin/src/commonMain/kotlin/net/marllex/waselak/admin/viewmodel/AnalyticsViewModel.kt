package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.network.AnalyticsOverviewDto

class AnalyticsViewModel(private val apiClient: AdminApiClient) : ViewModel() {
    private val _analytics = MutableStateFlow<AnalyticsOverviewDto?>(null)
    val analytics: StateFlow<AnalyticsOverviewDto?> = _analytics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAnalytics() {
        viewModelScope.launch {
            _isLoading.value = true
            _analytics.value = apiClient.getAnalyticsOverview()
            _isLoading.value = false
        }
    }
}
