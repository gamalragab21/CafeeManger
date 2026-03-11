package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.network.AnalyticsOverviewDto
import net.marllex.waselak.admin.network.PlatformAnalyticsDto

class AnalyticsViewModel(private val apiClient: AdminApiClient) : ViewModel() {
    private val _analytics = MutableStateFlow<AnalyticsOverviewDto?>(null)
    val analytics: StateFlow<AnalyticsOverviewDto?> = _analytics.asStateFlow()

    private val _platformAnalytics = MutableStateFlow<PlatformAnalyticsDto?>(null)
    val platformAnalytics: StateFlow<PlatformAnalyticsDto?> = _platformAnalytics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
        when (index) {
            0 -> if (_analytics.value == null) loadAnalytics()
            1 -> if (_platformAnalytics.value == null) loadPlatformAnalytics()
        }
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            _isLoading.value = true
            _analytics.value = apiClient.getAnalyticsOverview()
            _isLoading.value = false
        }
    }

    fun loadPlatformAnalytics() {
        viewModelScope.launch {
            _isLoading.value = true
            _platformAnalytics.value = apiClient.getPlatformAnalytics()
            _isLoading.value = false
        }
    }
}
