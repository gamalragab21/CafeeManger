package net.marllex.cafeemanger.feature.manager.analytics

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.AnalyticsRepository
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    data class UiState(
        val fromDate: Long? = null,
        val toDate: Long? = null,
        val selectedFormat: String = "pdf", // pdf or excel
        val previewData: PreviewData? = null,
        val exportedFilePath: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    data class PreviewData(
        val totalOrders: Int,
        val totalRevenue: Double,
        val totalDeliveryFees: Double,
        val ordersByChannel: Map<String, Int>,
        val ordersByPaymentMethod: Map<String, Int>
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Set default date range to last 30 days
        val calendar = Calendar.getInstance()
        val toDate = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val fromDate = calendar.timeInMillis

        _uiState.update {
            it.copy(
                fromDate = fromDate,
                toDate = toDate
            )
        }
    }

    fun setFromDate(date: Long) {
        _uiState.update { it.copy(fromDate = date, previewData = null, exportedFilePath = null) }
    }

    fun setToDate(date: Long) {
        _uiState.update { it.copy(toDate = date, previewData = null, exportedFilePath = null) }
    }

    fun setFormat(format: String) {
        _uiState.update { it.copy(selectedFormat = format, exportedFilePath = null) }
    }

    fun loadPreview() {
        val fromDate = _uiState.value.fromDate ?: return
        val toDate = _uiState.value.toDate ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            analyticsRepository.getExportPreview(fromDate, toDate)
                .onSuccess { body ->
                    val summary = body["summary"] as? Map<*, *>

                    val previewData = PreviewData(
                        totalOrders = (summary?.get("total_orders") as? Number)?.toInt() ?: 0,
                        totalRevenue = (summary?.get("total_revenue") as? Number)?.toDouble() ?: 0.0,
                        totalDeliveryFees = (summary?.get("total_delivery_fees") as? Number)?.toDouble() ?: 0.0,
                        ordersByChannel = (summary?.get("orders_by_channel") as? Map<String, Number>)
                            ?.mapValues { it.value.toInt() } ?: emptyMap(),
                        ordersByPaymentMethod = (summary?.get("orders_by_payment_method") as? Map<String, Number>)
                            ?.mapValues { it.value.toInt() } ?: emptyMap()
                    )

                    _uiState.update {
                        it.copy(
                            previewData = previewData,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Error loading preview: ${e.message}"
                        )
                    }
                }
        }
    }

    suspend fun exportData(context: Context) {
        val fromDate = _uiState.value.fromDate ?: return
        val toDate = _uiState.value.toDate ?: return
        val format = _uiState.value.selectedFormat

        _uiState.update { it.copy(isLoading = true, error = null, exportedFilePath = null) }

        val result = when (format) {
            "pdf" -> analyticsRepository.exportOrdersPDF(fromDate, toDate)
            "excel" -> analyticsRepository.exportOrdersExcel(fromDate, toDate)
            else -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Unknown format: $format"
                    )
                }
                return
            }
        }

        result
            .onSuccess { bytes ->
                val file = saveFile(context, bytes, format, fromDate, toDate)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        exportedFilePath = file.absolutePath
                    )
                }
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error exporting data: ${e.message}"
                    )
                }
            }
    }

    private fun saveFile(
        context: Context,
        bytes: ByteArray,
        format: String,
        fromDate: Long,
        toDate: Long
    ): File {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fromDateStr = dateFormat.format(Date(fromDate))
        val toDateStr = dateFormat.format(Date(toDate))

        val extension = if (format == "pdf") "pdf" else "xlsx"
        val fileName = "sales_report_${fromDateStr}_to_${toDateStr}.$extension"

        // Save to Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        FileOutputStream(file).use { outputStream ->
            outputStream.write(bytes)
        }

        return file
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
