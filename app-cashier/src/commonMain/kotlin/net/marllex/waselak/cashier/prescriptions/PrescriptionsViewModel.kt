package net.marllex.waselak.cashier.prescriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.PrescriptionRepository
import net.marllex.waselak.core.model.Prescription
import net.marllex.waselak.core.network.dto.CreatePrescriptionRequest
import net.marllex.waselak.core.network.dto.DispensePrescriptionRequest
import net.marllex.waselak.core.network.dto.DispenseItemRequest
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class PrescriptionsViewModel(
    private val prescriptionRepository: PrescriptionRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Prescriptions" }


    data class UiState(
        val prescriptions: List<Prescription> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val statusFilter: String? = null,
        val selectedPrescription: Prescription? = null,
        // Create dialog
        val showCreateDialog: Boolean = false,
        val patientName: String = "",
        val patientPhone: String = "",
        val doctorName: String = "",
        val diagnosis: String = "",
        val notes: String = "",
        // Dispense dialog
        val showDispenseDialog: Boolean = false,
        val dispensePrescription: Prescription? = null,
        val isSaving: Boolean = false,
    ) {
        val filteredPrescriptions: List<Prescription>
            get() = if (statusFilter != null) prescriptions.filter { it.status == statusFilter } else prescriptions
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        CrashReporter.addBreadcrumb("load() called", "PrescriptionsViewModel")
        AppLogger.d(TAG, "load called")
        CrashReporter.addBreadcrumb("Loading prescriptions", "PrescriptionsViewModel")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = _uiState.value.prescriptions.isEmpty(), error = null) }
            prescriptionRepository.getPrescriptions(status = _uiState.value.statusFilter)
                .onSuccess { list ->
                    CrashReporter.addBreadcrumb("Prescriptions loaded: ${list.size} items", "PrescriptionsViewModel")
                    _uiState.update { it.copy(prescriptions = list, isLoading = false) } }
                .onFailure { e ->
                    CrashReporter.addBreadcrumb("Prescriptions load failed: ${e.message}", "PrescriptionsViewModel")
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun onStatusFilterChange(status: String?) {
        _uiState.update { it.copy(statusFilter = status) }
        load()
    }

    fun selectPrescription(prescription: Prescription?) {
        AppLogger.d(TAG, "selectPrescription called")
        _uiState.update { it.copy(selectedPrescription = prescription) }
    }

    // Create prescription
    fun showCreateDialog() {
        _uiState.update {
            it.copy(showCreateDialog = true, patientName = "", patientPhone = "", doctorName = "", diagnosis = "", notes = "")
        }
    }
    fun dismissCreateDialog() { _uiState.update { it.copy(showCreateDialog = false) } }
    fun onPatientNameChange(v: String) { _uiState.update { it.copy(patientName = v) } }
    fun onPatientPhoneChange(v: String) { _uiState.update { it.copy(patientPhone = v) } }
    fun onDoctorNameChange(v: String) { _uiState.update { it.copy(doctorName = v) } }
    fun onDiagnosisChange(v: String) { _uiState.update { it.copy(diagnosis = v) } }
    fun onNotesChange(v: String) { _uiState.update { it.copy(notes = v) } }

    fun createPrescription() {
        AppLogger.d(TAG, "createPrescription called")
        CrashReporter.addBreadcrumb("Creating prescription", "PrescriptionsViewModel")
        val state = _uiState.value
        if (state.patientName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val request = CreatePrescriptionRequest(
                patientName = state.patientName,
                patientPhone = state.patientPhone.ifBlank { null },
                doctorName = state.doctorName.ifBlank { null },
                diagnosis = state.diagnosis.ifBlank { null },
                notes = state.notes.ifBlank { null },
                items = emptyList(),
            )
            prescriptionRepository.createPrescription(request)
                .onSuccess { _uiState.update { it.copy(isSaving = false, showCreateDialog = false) }; load() }
                .onFailure { e ->
                    CrashReporter.addBreadcrumb("Create prescription failed: ${e.message}", "PrescriptionsViewModel")
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    // Dispense
    fun showDispenseDialog(prescription: Prescription) {
        _uiState.update { it.copy(showDispenseDialog = true, dispensePrescription = prescription) }
    }
    fun dismissDispenseDialog() { _uiState.update { it.copy(showDispenseDialog = false, dispensePrescription = null) } }

    fun dispensePrescription() {
        AppLogger.d(TAG, "dispensePrescription called")
        CrashReporter.addBreadcrumb("Dispensing prescription", "PrescriptionsViewModel")
        val prescription = _uiState.value.dispensePrescription ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val items = prescription.items.filter { it.isPending }.map {
                DispenseItemRequest(prescriptionItemId = it.id, dispensedQuantity = it.quantity)
            }
            prescriptionRepository.dispensePrescription(
                prescription.id,
                DispensePrescriptionRequest(items = items),
            )
                .onSuccess { _uiState.update { it.copy(isSaving = false, showDispenseDialog = false, dispensePrescription = null) }; load() }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun cancelPrescription(id: String) {
        AppLogger.d(TAG, "cancelPrescription called")
        viewModelScope.launch {
            prescriptionRepository.cancelPrescription(id)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
