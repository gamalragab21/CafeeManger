package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.*

class LogsViewModel(private val apiClient: AdminApiClient) : ViewModel() {

    private val _stats = MutableStateFlow<LogStatsDto?>(null)
    val stats: StateFlow<LogStatsDto?> = _stats.asStateFlow()

    private val _topEndpoints = MutableStateFlow<List<EndpointStatDto>>(emptyList())
    val topEndpoints: StateFlow<List<EndpointStatDto>> = _topEndpoints.asStateFlow()

    private val _slowestEndpoints = MutableStateFlow<List<EndpointStatDto>>(emptyList())
    val slowestEndpoints: StateFlow<List<EndpointStatDto>> = _slowestEndpoints.asStateFlow()

    private val _errorEndpoints = MutableStateFlow<List<EndpointStatDto>>(emptyList())
    val errorEndpoints: StateFlow<List<EndpointStatDto>> = _errorEndpoints.asStateFlow()

    private val _timeline = MutableStateFlow<List<TimelinePointDto>>(emptyList())
    val timeline: StateFlow<List<TimelinePointDto>> = _timeline.asStateFlow()

    private val _logs = MutableStateFlow<PaginatedLogsDto?>(null)
    val logs: StateFlow<PaginatedLogsDto?> = _logs.asStateFlow()

    private val _vendors = MutableStateFlow<List<LogVendorDto>>(emptyList())
    val vendors: StateFlow<List<LogVendorDto>> = _vendors.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // New: Resource & Action breakdown
    private val _resourceBreakdown = MutableStateFlow<List<ResourceStatDto>>(emptyList())
    val resourceBreakdown: StateFlow<List<ResourceStatDto>> = _resourceBreakdown.asStateFlow()

    private val _actionBreakdown = MutableStateFlow<List<ActionStatDto>>(emptyList())
    val actionBreakdown: StateFlow<List<ActionStatDto>> = _actionBreakdown.asStateFlow()

    // New: Live monitoring
    private val _monitoring = MutableStateFlow<LiveMonitoringDto?>(null)
    val monitoring: StateFlow<LiveMonitoringDto?> = _monitoring.asStateFlow()

    private val _monitoringEnabled = MutableStateFlow(false)
    val monitoringEnabled: StateFlow<Boolean> = _monitoringEnabled.asStateFlow()

    // Filter state
    private val _selectedVendorId = MutableStateFlow<String?>(null)
    val selectedVendorId: StateFlow<String?> = _selectedVendorId.asStateFlow()

    private val _selectedMethod = MutableStateFlow<String?>(null)
    val selectedMethod: StateFlow<String?> = _selectedMethod.asStateFlow()

    private val _selectedStatusGroup = MutableStateFlow<String?>(null)
    val selectedStatusGroup: StateFlow<String?> = _selectedStatusGroup.asStateFlow()

    private val _selectedResource = MutableStateFlow<String?>(null)
    val selectedResource: StateFlow<String?> = _selectedResource.asStateFlow()

    private val _pathSearch = MutableStateFlow("")
    val pathSearch: StateFlow<String> = _pathSearch.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    fun loadDashboard() {
        viewModelScope.launch {
            _isLoading.value = true
            val vendorId = _selectedVendorId.value
            val statsDeferred = async { apiClient.getLogStats(vendorId) }
            val topDeferred = async { apiClient.getTopEndpoints(5, vendorId) }
            val slowDeferred = async { apiClient.getSlowestEndpoints(5, vendorId) }
            val errDeferred = async { apiClient.getErrorEndpoints(5, vendorId) }
            val timelineDeferred = async { apiClient.getRequestTimeline(24, vendorId) }
            val vendorsDeferred = async { apiClient.getLogVendors() }
            val resourceDeferred = async { apiClient.getResourceBreakdown(vendorId) }
            val actionDeferred = async { apiClient.getActionBreakdown(vendorId = vendorId) }
            val monitorDeferred = async { apiClient.getLiveMonitoring(vendorId) }

            _stats.value = statsDeferred.await()
            _topEndpoints.value = topDeferred.await()
            _slowestEndpoints.value = slowDeferred.await()
            _errorEndpoints.value = errDeferred.await()
            _timeline.value = timelineDeferred.await()
            _vendors.value = vendorsDeferred.await()
            _resourceBreakdown.value = resourceDeferred.await()
            _actionBreakdown.value = actionDeferred.await()
            _monitoring.value = monitorDeferred.await()
            _isLoading.value = false
        }
    }

    fun loadLogs(page: Int = 1) {
        viewModelScope.launch {
            _currentPage.value = page
            _logs.value = apiClient.getLogs(
                vendorId = _selectedVendorId.value,
                method = _selectedMethod.value,
                path = _pathSearch.value.ifBlank { null },
                statusGroup = _selectedStatusGroup.value,
                resource = _selectedResource.value,
                page = page,
                pageSize = 30
            )
        }
    }

    fun toggleMonitoring() {
        _monitoringEnabled.value = !_monitoringEnabled.value
        if (_monitoringEnabled.value) {
            startMonitoringLoop()
        }
    }

    private fun startMonitoringLoop() {
        viewModelScope.launch {
            while (isActive && _monitoringEnabled.value) {
                _monitoring.value = apiClient.getLiveMonitoring(_selectedVendorId.value)
                delay(15_000) // Refresh every 15 seconds
            }
        }
    }

    fun setVendorFilter(vendorId: String?) {
        _selectedVendorId.value = vendorId
    }

    fun setMethodFilter(method: String?) {
        _selectedMethod.value = method
    }

    fun setStatusGroupFilter(statusGroup: String?) {
        _selectedStatusGroup.value = statusGroup
    }

    fun setResourceFilter(resource: String?) {
        _selectedResource.value = resource
    }

    fun setPathSearch(path: String) {
        _pathSearch.value = path
    }

    fun applyFilters() {
        _currentPage.value = 1
        loadDashboard()
        loadLogs(1)
    }

    fun cleanupLogs(days: Int) {
        viewModelScope.launch {
            val success = apiClient.cleanupLogs(days)
            if (success) {
                _message.value = "Old logs cleaned up"
                loadDashboard()
                loadLogs(1)
            } else {
                _message.value = "Failed to cleanup logs"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
