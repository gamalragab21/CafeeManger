package net.marllex.waselak.feature.manager.staff

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.model.Worker
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import net.marllex.waselak.core.common.crash.CrashReporter

class WorkerQrCodeViewModel constructor(
    private val workerRepository: WorkerRepository,
    private val vendorRepository: VendorRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val worker: Worker? = null,
        val vendor: Vendor? = null,
        val qrCodeImage: ImageBitmap? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val successMessage: String? = null,
        val showRegenerateDialog: Boolean = false,
    )

    private val workerId: String = checkNotNull(savedStateHandle["workerId"])

    // Keep raw bytes for saving to file
    private var qrCodeBytes: ByteArray? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadWorkerAndQrCode()
    }

    private fun loadWorkerAndQrCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val vendor = vendorRepository.refreshVendor().getOrNull()

                workerRepository.refreshWorkers().onSuccess { workers ->
                    val worker = workers.find { it.id == workerId }
                    if (worker == null) {
                        _uiState.update { it.copy(isLoading = false, error = "Worker not found") }
                        return@launch
                    }

                    workerRepository.getWorkerQrCode(workerId).onSuccess { bytes ->
                        qrCodeBytes = bytes
                        val imageBitmap = withContext(Dispatchers.IO) {
                            val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
                            bufferedImage?.toComposeImageBitmap()
                        }
                        _uiState.update {
                            it.copy(
                                worker = worker,
                                vendor = vendor,
                                qrCodeImage = imageBitmap,
                                isLoading = false,
                            )
                        }
                    }.onFailure { e ->
                    CrashReporter.captureException(e)
                        _uiState.update {
                            it.copy(
                                worker = worker,
                                vendor = vendor,
                                isLoading = false,
                                error = "Failed to load QR code: ${e.message}",
                            )
                        }
                    }
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load worker: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun downloadQrCode() {
        viewModelScope.launch {
            val worker = _uiState.value.worker
            val bytes = qrCodeBytes

            if (worker == null || bytes == null) {
                _uiState.update { it.copy(error = "No QR code to download") }
                return@launch
            }

            try {
                withContext(Dispatchers.IO) {
                    val defaultName = "QR_${worker.fullName.replace(" ", "_")}_${worker.workerId}.png"
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Save QR Code"
                        selectedFile = File(defaultName)
                        fileFilter = FileNameExtensionFilter("PNG Images", "png")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        var file = chooser.selectedFile
                        if (!file.name.endsWith(".png")) {
                            file = File(file.absolutePath + ".png")
                        }
                        file.writeBytes(bytes)
                        _uiState.update { it.copy(successMessage = "QR code saved to ${file.name}") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save QR code: ${e.message}") }
            }
        }
    }

    fun showRegenerateDialog() {
        _uiState.update { it.copy(showRegenerateDialog = true) }
    }

    fun dismissRegenerateDialog() {
        _uiState.update { it.copy(showRegenerateDialog = false) }
    }

    fun regenerateQrCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showRegenerateDialog = false) }

            workerRepository.regenerateWorkerQrCode(workerId).onSuccess {
                _uiState.update { it.copy(successMessage = "QR code regenerated successfully") }
                loadWorkerAndQrCode()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to regenerate QR code: ${e.message}")
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
