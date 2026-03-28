package net.marllex.waselak.feature.manager.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.*
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import net.marllex.waselak.core.network.isPlanLimitExceeded
import net.marllex.waselak.core.ui.components.ShiftSummaryUiModel
import net.marllex.waselak.core.common.crash.CrashReporter

class StaffViewModel constructor(
    private val workerRepository: WorkerRepository,
    private val apiClient: WaselakApiClient,
) : ViewModel() {

    data class UiState(
        // Workers
        val workers: List<Worker> = emptyList(),
        val workerRoles: List<WorkerRole> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,

        // Today's attendance
        val todaySummary: List<AttendanceSummary> = emptyList(),
        val attendanceRecords: List<Attendance> = emptyList(),

        // Salary
        val salaryPayments: List<SalaryPayment> = emptyList(),

        // Overtime
        val overtimeEntries: List<Overtime> = emptyList(),
        val showAddOvertimeDialog: Boolean = false,
        val overtimeWorkerId: String? = null,
        val overtimeDate: String = "",
        val overtimeHours: String = "",
        val overtimeRatePerHour: String = "",
        val overtimeNote: String = "",
        // Edit Rate dialog (Manager)
        val showEditRateDialog: Boolean = false,
        val editRateOvertimeId: String? = null,
        val editRateValue: String = "",
        // Overtime batch pay
        val selectedOvertimeIds: Set<String> = emptySet(),

        // Add Worker Dialog - Multi-Step
        val showAddWorkerDialog: Boolean = false,
        val editingWorker: Worker? = null,
        val dialogStep: Int = 1, // 1 = Basic Info, 2 = System Access, 3 = Salary
        val dialogName: String = "",
        val dialogPhone: String = "",
        val dialogDescription: String = "",
        val dialogPhotoUrl: String? = null,
        val dialogRole: String = "",
        val dialogWorkerType: WorkerType = WorkerType.NORMAL, // NEW: Normal or Main Worker
        val dialogSalaryType: SalaryType = SalaryType.DAILY,
        val dialogSalaryAmount: String = "",
        val dialogIsLoginEnabled: Boolean = false,
        val dialogPassword: String = "",
        val dialogLoginRole: String = "CASHIER",
        val dialogEmail: String = "", // NEW: Email for main workers
        val dialogPin: String = "",
        val dialogPinConfirm: String = "",
        val showDialogPin: Boolean = false,
        val showDialogPinConfirm: Boolean = false,
        val isSaving: Boolean = false,
        val showPlanLimitDialog: Boolean = false,
        val planLimitMessage: String = "",
        val showFeatureNotAvailable: Boolean = false,
        val featureNotAvailableMessage: String = "",

        // Add Role Dialog
        val showAddRoleDialog: Boolean = false,
        val dialogRoleName: String = "",
        val dialogRoleDescription: String = "",

        // Delete Confirmation
        val showDeleteWorkerDialog: Boolean = false,
        val workerToDelete: Worker? = null,
        val showDeleteRoleDialog: Boolean = false,
        val roleToDelete: WorkerRole? = null,

        // Selected tab filter
        val selectedRoleFilter: String? = null,
        val salaryPaidFilter: Boolean? = null,

        // Salary detail view (two-level navigation)
        val selectedSalaryWorkerId: String? = null, // null = worker list, non-null = detail
        val selectedPaymentIds: Set<String> = emptySet(),
        val showBatchPayDialog: Boolean = false,
        val batchPayNote: String = "",

        // Shift Summary (per worker)
        val showShiftSummary: Boolean = false,
        val shiftSummaryData: ShiftSummaryUiModel? = null,
        val shiftSummaryLoading: Boolean = false,
        val shiftSummaryError: String? = null,
        val shiftSummaryWorkerName: String = "",

        // Attendance filters
        val attendanceWorkerFilter: String? = null,
        val attendancePeriod: AttendancePeriod = AttendancePeriod.TODAY,
        val attendanceFromDate: String = "",
        val attendanceToDate: String = "",
        val attendanceStatusFilter: String? = null, // "PRESENT", "ABSENT", or null for all
        val attendanceRoleFilter: String? = null, // "Cashier", "Delivery", or null for all

    )

    // Worker Type Enum
    enum class WorkerType {
        NORMAL,  // Regular worker, no system access
        MAIN     // System user with app access (Cashier/Delivery/Manager)
    }

    // Attendance Date Period Filter
    enum class AttendancePeriod {
        TODAY, WEEK, MONTH, CUSTOM
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Initialize attendance dates to today BEFORE loading data
        initAttendanceDates()
        loadData()
        observeWorkers()
        observeSalaryPayments()
    }

    private fun initAttendanceDates() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        _uiState.update {
            it.copy(
                attendancePeriod = AttendancePeriod.TODAY,
                attendanceFromDate = today,
                attendanceToDate = today,
            )
        }
    }

    private fun observeWorkers() {
        viewModelScope.launch {
            workerRepository.getWorkers().collect { workers ->
                _uiState.update { it.copy(workers = workers) }
            }
        }
    }

    private fun observeSalaryPayments() {
        viewModelScope.launch {
            workerRepository.getSalaryPayments().collect { payments ->
                _uiState.update { it.copy(salaryPayments = payments) }
            }
        }
    }

    fun loadData() {
        CrashReporter.addBreadcrumb("loadData() called", "StaffViewModel")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                AppLogger.d("Staff", "Loading staff data")
                workerRepository.refreshWorkers()
                workerRepository.refreshWorkerRoles().onSuccess { roles ->
                    _uiState.update { it.copy(workerRoles = roles) }
                }
            } catch (e: Exception) {
                if (e.isFeatureNotAvailableOrOffline()) {
                    AppLogger.d("Staff", "Workers feature not available (gated at UI level)")
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                return@launch
            }

            try {
                workerRepository.getTodayAttendance().onSuccess { summary ->
                    _uiState.update { it.copy(todaySummary = summary) }
                }
                applyAttendanceFilters()
            } catch (e: Exception) {
                if (e.isFeatureNotAvailableOrOffline()) {
                    AppLogger.d("Staff", "Attendance feature not available (gated at UI level)")
                }
            }

            try {
                workerRepository.refreshSalaryPayments()
            } catch (e: Exception) {
                if (e.isFeatureNotAvailableOrOffline()) {
                    AppLogger.d("Staff", "Salary feature not available (gated at UI level)")
                }
            }

            try {
                workerRepository.refreshOvertime()
            } catch (e: Exception) {
                if (e.isFeatureNotAvailableOrOffline()) {
                    AppLogger.d("Staff", "Overtime feature not available (gated at UI level)")
                }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshAttendance(workerId: String? = null, fromDate: String? = null, toDate: String? = null) {
        viewModelScope.launch {
            workerRepository.refreshAttendance(workerId = workerId, fromDate = fromDate, toDate = toDate)
                .onSuccess { records ->
                    _uiState.update { it.copy(attendanceRecords = records) }
                }
        }
    }

    fun refreshTodayAttendance() {
        viewModelScope.launch {
            workerRepository.getTodayAttendance().onSuccess { summary ->
                _uiState.update { it.copy(todaySummary = summary) }
            }
        }
    }

    // ─── Worker CRUD ─────────────────────────────────────────────

    fun showAddWorkerDialog(worker: Worker? = null) {
        AppLogger.i("Staff", "User action: open ${if (worker != null) "edit" else "add"} worker dialog${if (worker != null) " for id=${worker.id}" else ""}")
        val current = _uiState.value
        if (worker != null) {
            // Edit mode: always populate from the worker being edited
            _uiState.update {
                it.copy(
                    showAddWorkerDialog = true,
                    editingWorker = worker,
                    dialogStep = 1,
                    dialogName = worker.fullName,
                    dialogPhone = worker.phone ?: "",
                    dialogDescription = worker.description ?: "",
                    dialogPhotoUrl = worker.photoUrl,
                    dialogRole = worker.role,
                    dialogWorkerType = if (worker.isLoginEnabled) WorkerType.MAIN else WorkerType.NORMAL,
                    dialogSalaryType = worker.salaryType,
                    dialogSalaryAmount = worker.salaryAmount.let { a -> if (a > 0) a.toLong().let { l -> if (a == l.toDouble()) l.toString() else a.toString() } else "" },
                    dialogIsLoginEnabled = worker.isLoginEnabled,
                    dialogPassword = "",
                    dialogLoginRole = "CASHIER",
                    dialogEmail = "",
                    dialogPin = "",
                    dialogPinConfirm = "",
                    showDialogPin = false,
                    showDialogPinConfirm = false,
                )
            }
        } else if (current.dialogName.isNotBlank() && current.editingWorker == null) {
            // Reopen draft: data already exists from a previous dismiss, just show the sheet
            _uiState.update { it.copy(showAddWorkerDialog = true) }
        } else {
            // Fresh new worker: reset all fields
            _uiState.update {
                it.copy(
                    showAddWorkerDialog = true,
                    editingWorker = null,
                    dialogStep = 1,
                    dialogName = "",
                    dialogPhone = "",
                    dialogDescription = "",
                    dialogPhotoUrl = null,
                    dialogRole = "",
                    dialogWorkerType = WorkerType.NORMAL,
                    dialogSalaryType = SalaryType.DAILY,
                    dialogSalaryAmount = "",
                    dialogIsLoginEnabled = false,
                    dialogPassword = "",
                    dialogLoginRole = "CASHIER",
                    dialogEmail = "",
                    dialogPin = "",
                    dialogPinConfirm = "",
                    showDialogPin = false,
                    showDialogPinConfirm = false,
                )
            }
        }
    }

    fun dismissWorkerDialog() {
        // Only hide the sheet — preserve all form data so user can reopen
        _uiState.update { it.copy(showAddWorkerDialog = false) }
    }

    fun clearWorkerDialogData() {
        _uiState.update {
            it.copy(
                showAddWorkerDialog = false,
                editingWorker = null,
                dialogStep = 1,
                dialogName = "",
                dialogPhone = "",
                dialogDescription = "",
                dialogPhotoUrl = null,
                dialogRole = "",
                dialogWorkerType = WorkerType.NORMAL,
                dialogSalaryType = SalaryType.DAILY,
                dialogSalaryAmount = "",
                dialogIsLoginEnabled = false,
                dialogPassword = "",
                dialogLoginRole = "CASHIER",
                dialogEmail = "",
                dialogPin = "",
                dialogPinConfirm = "",
                showDialogPin = false,
                showDialogPinConfirm = false,
            )
        }
    }

    // NEW: Dialog Navigation
    fun nextDialogStep() {
        val currentStep = _uiState.value.dialogStep
        val workerType = _uiState.value.dialogWorkerType
        
        when (currentStep) {
            1 -> {
                // From Basic Info -> System Access (if Main Worker) or Salary (if Normal Worker)
                val nextStep = if (workerType == WorkerType.MAIN) 2 else 3
                _uiState.update { it.copy(dialogStep = nextStep) }
            }
            2 -> {
                // From System Access -> Salary
                _uiState.update { it.copy(dialogStep = 3) }
            }
        }
    }

    fun previousDialogStep() {
        val currentStep = _uiState.value.dialogStep
        val workerType = _uiState.value.dialogWorkerType
        
        when (currentStep) {
            2 -> {
                // From System Access -> Basic Info
                _uiState.update { it.copy(dialogStep = 1) }
            }
            3 -> {
                // From Salary -> System Access (if Main Worker) or Basic Info (if Normal Worker)
                val prevStep = if (workerType == WorkerType.MAIN) 2 else 1
                _uiState.update { it.copy(dialogStep = prevStep) }
            }
        }
    }

    fun updateDialogName(v: String) = _uiState.update { it.copy(dialogName = v) }
    fun updateDialogPhone(v: String) = _uiState.update { it.copy(dialogPhone = v) }
    fun updateDialogDescription(v: String) = _uiState.update { it.copy(dialogDescription = v) }
    fun updateDialogPhotoUrl(v: String?) = _uiState.update { it.copy(dialogPhotoUrl = v) }
    fun uploadWorkerPhoto(imageBytes: ByteArray) {
        AppLogger.d("Staff", "Uploading worker photo")
        viewModelScope.launch {
            workerRepository.uploadImage(imageBytes, "worker_${Clock.System.now().toEpochMilliseconds()}.jpg")
                .onSuccess { url ->
                    AppLogger.i("Staff", "Worker photo uploaded")
                    _uiState.update { it.copy(dialogPhotoUrl = url) }
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Staff", "Failed to upload worker photo", e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }
    fun updateDialogRole(v: String) = _uiState.update { it.copy(dialogRole = v) }
    fun updateDialogWorkerType(v: WorkerType) = _uiState.update { 
        it.copy(
            dialogWorkerType = v,
            dialogIsLoginEnabled = v == WorkerType.MAIN,
            // Auto-set role based on login role for main workers
            dialogRole = if (v == WorkerType.MAIN) {
                when (it.dialogLoginRole) {
                    "CASHIER" -> "Cashier"
                    "DELIVERY" -> "Delivery"
                    "MANAGER" -> "Manager"
                    else -> "Cashier"
                }
            } else it.dialogRole
        ) 
    }
    fun updateDialogSalaryType(v: SalaryType) = _uiState.update { it.copy(dialogSalaryType = v) }
    fun updateDialogSalaryAmount(v: String) = _uiState.update { it.copy(dialogSalaryAmount = v) }
    fun updateDialogIsLoginEnabled(v: Boolean) = _uiState.update { it.copy(dialogIsLoginEnabled = v) }
    fun updateDialogPassword(v: String) = _uiState.update { it.copy(dialogPassword = v) }
    fun updateDialogLoginRole(v: String) = _uiState.update { 
        it.copy(
            dialogLoginRole = v,
            // Auto-update role when login role changes for main workers
            dialogRole = if (it.dialogWorkerType == WorkerType.MAIN) {
                when (v) {
                    "CASHIER" -> "Cashier"
                    "DELIVERY" -> "Delivery"
                    "MANAGER" -> "Manager"
                    else -> "Cashier"
                }
            } else it.dialogRole
        )
    }
    fun updateDialogEmail(v: String) = _uiState.update { it.copy(dialogEmail = v) }
    fun updateDialogPin(v: String) {
        // Only allow numeric input, max 6 digits
        if (v.all { it.isDigit() } && v.length <= 6) {
            _uiState.update { it.copy(dialogPin = v) }
        }
    }
    fun updateDialogPinConfirm(v: String) {
        // Only allow numeric input, max 6 digits
        if (v.all { it.isDigit() } && v.length <= 6) {
            _uiState.update { it.copy(dialogPinConfirm = v) }
        }
    }
    fun toggleShowDialogPin() = _uiState.update { it.copy(showDialogPin = !it.showDialogPin) }
    fun toggleShowDialogPinConfirm() = _uiState.update { it.copy(showDialogPinConfirm = !it.showDialogPinConfirm) }

    fun saveWorker() {
        val s = _uiState.value
        AppLogger.d("Staff", "Saving worker: name=${s.dialogName}, editing=${s.editingWorker != null}")
        if (s.dialogName.isBlank() || s.dialogRole.isBlank()) return

        // Salary is mandatory
        val amount = s.dialogSalaryAmount.toDoubleOrNull() ?: 0.0
        if (amount <= 0) return

        // Validate PIN
        if (s.editingWorker == null) {
            // New worker: PIN is required
            if (s.dialogPin.length < 4 || s.dialogPin.length > 6) return
            if (s.dialogPin != s.dialogPinConfirm) return
        } else {
            // Edit worker: PIN is optional, but if provided must be valid
            if (s.dialogPin.isNotEmpty()) {
                if (s.dialogPin.length < 4 || s.dialogPin.length > 6) return
                if (s.dialogPin != s.dialogPinConfirm) return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            if (s.editingWorker != null) {
                workerRepository.updateWorker(
                    id = s.editingWorker.id,
                    fullName = s.dialogName,
                    phone = s.dialogPhone.ifBlank { null },
                    description = s.dialogDescription.ifBlank { null },
                    photoUrl = s.dialogPhotoUrl,
                    role = s.dialogRole,
                    salaryType = s.dialogSalaryType.name,
                    salaryAmount = amount,
                    pin = s.dialogPin.ifBlank { null }, // Only send PIN if not empty
                    active = null
                ).onSuccess {
                    AppLogger.i("Staff", "Worker updated successfully")
                    _uiState.update { it.copy(isSaving = false) }
                    clearWorkerDialogData()
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Staff", "Failed to update worker", e)
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
            } else {
                workerRepository.createWorker(
                    fullName = s.dialogName,
                    phone = s.dialogPhone.ifBlank { null },
                    description = s.dialogDescription.ifBlank { null },
                    photoUrl = s.dialogPhotoUrl,
                    role = s.dialogRole,
                    salaryType = s.dialogSalaryType,
                    salaryAmount = amount,
                    isLoginEnabled = s.dialogIsLoginEnabled,
                    password = if (s.dialogIsLoginEnabled) s.dialogPassword.ifBlank { null } else null,
                    loginRole = if (s.dialogIsLoginEnabled) s.dialogLoginRole else null,
                    pin = s.dialogPin
                ).onSuccess {
                    AppLogger.i("Staff", "Worker created successfully")
                    _uiState.update { it.copy(isSaving = false) }
                    clearWorkerDialogData()
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Staff", "Failed to create worker", e)
                    when {
                        e.isPlanLimitExceeded() -> _uiState.update { it.copy(
                            isSaving = false,
                            showPlanLimitDialog = true,
                            planLimitMessage = e.message ?: "",
                        ) }
                        e.isFeatureNotAvailableOrOffline() -> _uiState.update { it.copy(
                            isSaving = false,
                            showFeatureNotAvailable = true,
                            featureNotAvailableMessage = e.message ?: "",
                        ) }
                        else -> _uiState.update { it.copy(isSaving = false, error = e.message) }
                    }
                }
            }
        }
    }

    fun toggleWorkerActive(worker: Worker) {
        AppLogger.d("Staff", "Toggling worker active: id=${worker.id}, active=${!worker.active}")
        viewModelScope.launch {
            workerRepository.updateWorker(
                id = worker.id, fullName = null, phone = null,
                description = null, role = null, salaryType = null,
                salaryAmount = null, active = !worker.active
            ).onSuccess { loadData() }
        }
    }

    fun showDeleteWorkerConfirm(worker: Worker) {
        AppLogger.i("Staff", "User action: open delete worker confirmation for id=${worker.id}")
        _uiState.update { it.copy(showDeleteWorkerDialog = true, workerToDelete = worker) }
    }

    fun dismissDeleteWorkerDialog() {
        _uiState.update { it.copy(showDeleteWorkerDialog = false, workerToDelete = null) }
    }

    fun confirmDeleteWorker() {
        val worker = _uiState.value.workerToDelete ?: return
        viewModelScope.launch {
            AppLogger.d("Staff", "Deleting worker: id=${worker.id}")
            workerRepository.deleteWorker(worker.id)
                .onSuccess {
                    AppLogger.i("Staff", "Worker deleted")
                    _uiState.update { it.copy(showDeleteWorkerDialog = false, workerToDelete = null) }
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Staff", "Failed to delete worker", e)
                    _uiState.update { it.copy(showDeleteWorkerDialog = false, error = e.message) }
                }
        }
    }

    fun filterByRole(role: String?) {
        AppLogger.d("Staff", "Filter by role: $role")
        _uiState.update { it.copy(selectedRoleFilter = role) }
    }

    // ─── Roles ───────────────────────────────────────────────────

    fun showAddRoleDialog() {
        AppLogger.i("Staff", "User action: open add role dialog")
        _uiState.update { it.copy(showAddRoleDialog = true, dialogRoleName = "", dialogRoleDescription = "") }
    }

    fun dismissRoleDialog() {
        _uiState.update { it.copy(showAddRoleDialog = false) }
    }

    fun updateDialogRoleName(v: String) = _uiState.update { it.copy(dialogRoleName = v) }
    fun updateDialogRoleDescription(v: String) = _uiState.update { it.copy(dialogRoleDescription = v) }

    fun saveRole() {
        val s = _uiState.value
        if (s.dialogRoleName.isBlank()) return
        viewModelScope.launch {
            AppLogger.d("Staff", "Saving role: name=${s.dialogRoleName}")
            _uiState.update { it.copy(isSaving = true) }
            workerRepository.createWorkerRole(s.dialogRoleName, s.dialogRoleDescription.ifBlank { null })
                .onSuccess {
                    AppLogger.i("Staff", "Role created")
                    _uiState.update { it.copy(isSaving = false, showAddRoleDialog = false) }
                    workerRepository.refreshWorkerRoles().onSuccess { roles ->
                        _uiState.update { it.copy(workerRoles = roles) }
                    }
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Staff", "Failed to create role", e)
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun showDeleteRoleConfirm(role: WorkerRole) {
        AppLogger.i("Staff", "User action: open delete role confirmation for id=${role.id}")
        _uiState.update { it.copy(showDeleteRoleDialog = true, roleToDelete = role) }
    }

    fun dismissDeleteRoleDialog() {
        _uiState.update { it.copy(showDeleteRoleDialog = false, roleToDelete = null) }
    }

    fun confirmDeleteRole() {
        val role = _uiState.value.roleToDelete ?: return
        AppLogger.d("Staff", "Deleting role: id=${role.id}")
        viewModelScope.launch {
            workerRepository.deleteWorkerRole(role.id)
                .onSuccess {
                    AppLogger.i("Staff", "Role deleted: id=${role.id}")
                    _uiState.update { it.copy(showDeleteRoleDialog = false, roleToDelete = null) }
                    workerRepository.refreshWorkerRoles().onSuccess { roles ->
                        _uiState.update { it.copy(workerRoles = roles) }
                    }
                }
        }
    }

    // ─── Salary ──────────────────────────────────────────────────

    fun selectWorkerForSalary(workerId: String?) {
        AppLogger.d("Staff", "Selected worker for salary: workerId=$workerId")
        _uiState.update {
            it.copy(
                selectedSalaryWorkerId = workerId,
                selectedPaymentIds = emptySet(),
                salaryPaidFilter = null,
            )
        }
    }

    fun togglePaymentSelection(paymentId: String) {
        _uiState.update {
            val current = it.selectedPaymentIds
            it.copy(selectedPaymentIds = if (paymentId in current) current - paymentId else current + paymentId)
        }
    }

    fun showBatchPayDialog() {
        _uiState.update { it.copy(showBatchPayDialog = true, batchPayNote = "") }
    }

    fun dismissBatchPayDialog() {
        _uiState.update { it.copy(showBatchPayDialog = false) }
    }

    fun updateBatchPayNote(v: String) = _uiState.update { it.copy(batchPayNote = v) }

    fun batchPay() {
        val s = _uiState.value
        if (s.selectedPaymentIds.isEmpty()) return
        AppLogger.d("Staff", "Batch paying ${s.selectedPaymentIds.size} payments")
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            workerRepository.batchPaySalaries(
                paymentIds = s.selectedPaymentIds.toList(),
                note = s.batchPayNote.ifBlank { null }
            ).onSuccess {
                AppLogger.i("Staff", "Batch pay completed")
                _uiState.update {
                    it.copy(isSaving = false, showBatchPayDialog = false, selectedPaymentIds = emptySet())
                }
                workerRepository.refreshSalaryPayments()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                AppLogger.e("Staff", "Batch pay failed", e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun markUnpaid(payment: SalaryPayment) {
        AppLogger.d("Staff", "Marking unpaid: paymentId=${payment.id}")
        viewModelScope.launch {
            workerRepository.markUnpaid(payment.id)
                .onSuccess { workerRepository.refreshSalaryPayments() }
        }
    }

    fun filterSalaryByPaid(paid: Boolean?) {
        _uiState.update { it.copy(salaryPaidFilter = paid) }
    }

    // ─── Attendance Filters ──────────────────────────────────────

    fun setAttendanceWorkerFilter(workerId: String?) {
        _uiState.update { it.copy(attendanceWorkerFilter = workerId) }
        applyAttendanceFilters()
    }

    fun setAttendancePeriod(period: AttendancePeriod) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val (from, to) = when (period) {
            AttendancePeriod.TODAY -> today.toString() to today.toString()
            AttendancePeriod.WEEK -> {
                val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
                startOfWeek.toString() to today.toString()
            }
            AttendancePeriod.MONTH -> {
                val startOfMonth = LocalDate(today.year, today.month, 1)
                startOfMonth.toString() to today.toString()
            }
            AttendancePeriod.CUSTOM -> {
                _uiState.update { it.copy(attendancePeriod = period) }
                return // Don't auto-fetch, wait for user to set dates
            }
        }
        _uiState.update {
            it.copy(
                attendancePeriod = period,
                attendanceFromDate = from,
                attendanceToDate = to,
            )
        }
        applyAttendanceFilters()
    }

    fun setAttendanceFromDate(date: String) {
        _uiState.update { it.copy(attendanceFromDate = date) }
        applyAttendanceFilters()
    }

    fun setAttendanceToDate(date: String) {
        _uiState.update { it.copy(attendanceToDate = date) }
        applyAttendanceFilters()
    }

    fun setAttendanceStatusFilter(status: String?) {
        _uiState.update { it.copy(attendanceStatusFilter = status) }
    }

    fun setAttendanceRoleFilter(role: String?) {
        _uiState.update { it.copy(attendanceRoleFilter = role) }
    }

    private fun applyAttendanceFilters() {
        val s = _uiState.value
        refreshAttendance(
            workerId = s.attendanceWorkerFilter,
            fromDate = s.attendanceFromDate.ifBlank { null },
            toDate = s.attendanceToDate.ifBlank { null },
        )
    }

    val filteredAttendanceRecords: List<Attendance>
        get() {
            val s = _uiState.value
            var records = s.attendanceRecords

            // Apply status filter
            records = when (s.attendanceStatusFilter) {
                "PRESENT" -> records.filter { it.checkIn > 0 }
                "ABSENT" -> records.filter { it.checkIn <= 0 }
                else -> records
            }

            // Apply role filter
            if (s.attendanceRoleFilter != null) {
                records = records.filter {
                    it.workerRole.equals(s.attendanceRoleFilter, ignoreCase = true)
                }
            }

            return records
        }

    val filteredTodaySummary: List<AttendanceSummary>
        get() {
            val s = _uiState.value
            if (s.attendanceRoleFilter == null) return s.todaySummary
            return s.todaySummary.filter {
                it.workerRole.equals(s.attendanceRoleFilter, ignoreCase = true)
            }
        }

    fun dismissPlanLimitDialog() {
        _uiState.update { it.copy(showPlanLimitDialog = false, planLimitMessage = "") }
    }

    fun dismissFeatureNotAvailable() {
        _uiState.update { it.copy(showFeatureNotAvailable = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ─── Shift Summary (per worker) ─────────────────────────────────

    fun fetchShiftSummary(worker: Worker) {
        val userId = worker.userId ?: return
        AppLogger.d("Staff", "Fetching shift summary for worker: ${worker.fullName}, userId=$userId")
        _uiState.update {
            it.copy(
                showShiftSummary = true,
                shiftSummaryLoading = true,
                shiftSummaryError = null,
                shiftSummaryData = null,
                shiftSummaryWorkerName = worker.fullName,
            )
        }
        viewModelScope.launch {
            try {
                val response = apiClient.getUserShiftSummary(userId)
                _uiState.update {
                    it.copy(
                        shiftSummaryLoading = false,
                        shiftSummaryData = ShiftSummaryUiModel(
                            totalRevenue = response.totalRevenue,
                            totalOrders = response.totalOrders,
                            cashRevenue = response.cashRevenue,
                            walletRevenue = response.walletRevenue,
                            cardRevenue = response.cardRevenue,
                            cashOrders = response.cashOrders,
                            walletOrders = response.walletOrders,
                            cardOrders = response.cardOrders,
                            cancelledTotal = response.cancelledTotal,
                            cancelledCount = response.cancelledCount,
                            refundedTotal = response.refundedTotal,
                            refundedCount = response.refundedCount,
                        ),
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("Staff", "Failed to fetch shift summary", e)
                if (e.isFeatureNotAvailableOrOffline()) {
                    _uiState.update {
                        it.copy(
                            showShiftSummary = false,
                            shiftSummaryLoading = false,
                            showFeatureNotAvailable = true,
                            featureNotAvailableMessage = e.message ?: "",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            shiftSummaryLoading = false,
                            shiftSummaryError = e.message,
                        )
                    }
                }
            }
        }
    }

    fun dismissShiftSummary() {
        _uiState.update {
            it.copy(
                showShiftSummary = false,
                shiftSummaryData = null,
                shiftSummaryError = null,
                shiftSummaryWorkerName = "",
            )
        }
    }

    // ─── Overtime ─────────────────────────────────────────────────

    fun showAddOvertimeDialog(workerId: String) {
        AppLogger.i("Staff", "User action: open add overtime dialog for workerId=$workerId")
        _uiState.update {
            it.copy(
                showAddOvertimeDialog = true,
                overtimeWorkerId = workerId,
                overtimeDate = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
                overtimeHours = "",
                overtimeRatePerHour = "",
                overtimeNote = "",
            )
        }
    }

    fun dismissOvertimeDialog() {
        _uiState.update { it.copy(showAddOvertimeDialog = false) }
    }

    fun updateOvertimeDate(v: String) = _uiState.update { it.copy(overtimeDate = v) }
    fun updateOvertimeHours(v: String) = _uiState.update { it.copy(overtimeHours = v) }
    fun updateOvertimeRatePerHour(v: String) = _uiState.update { it.copy(overtimeRatePerHour = v) }
    fun updateOvertimeNote(v: String) = _uiState.update { it.copy(overtimeNote = v) }

    fun submitOvertime() {
        val s = _uiState.value
        val workerId = s.overtimeWorkerId ?: return
        val hours = s.overtimeHours.toDoubleOrNull() ?: return
        if (hours <= 0 || s.overtimeDate.isBlank()) return
        // Rate is optional: cashier sends 0, manager may provide a rate
        val rate = s.overtimeRatePerHour.toDoubleOrNull() ?: 0.0

        AppLogger.d("Staff", "Submitting overtime: workerId=$workerId, hours=$hours")
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            workerRepository.createOvertime(
                workerId = workerId,
                date = s.overtimeDate,
                hours = hours,
                ratePerHour = rate,
                note = s.overtimeNote.ifBlank { null }
            ).onSuccess {
                AppLogger.i("Staff", "Overtime submitted")
                _uiState.update { it.copy(isSaving = false, showAddOvertimeDialog = false) }
                refreshOvertimeForWorker(workerId)
                workerRepository.refreshSalaryPayments()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                AppLogger.e("Staff", "Failed to submit overtime", e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    // ─── Edit Rate (Manager only) ────────────────────────────────────

    fun showEditRateDialog(overtimeId: String, currentRate: Double) {
        AppLogger.i("Staff", "User action: open edit rate dialog for overtimeId=$overtimeId, currentRate=$currentRate")
        _uiState.update {
            it.copy(
                showEditRateDialog = true,
                editRateOvertimeId = overtimeId,
                editRateValue = if (currentRate > 0) currentRate.toInt().toString() else "",
            )
        }
    }

    fun dismissEditRateDialog() {
        _uiState.update { it.copy(showEditRateDialog = false, editRateOvertimeId = null) }
    }

    fun updateEditRateValue(v: String) = _uiState.update { it.copy(editRateValue = v) }

    fun submitEditRate() {
        val s = _uiState.value
        val overtimeId = s.editRateOvertimeId ?: return
        val rate = s.editRateValue.toDoubleOrNull() ?: return
        if (rate <= 0) return

        AppLogger.d("Staff", "Editing overtime rate: overtimeId=$overtimeId, rate=$rate")
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            workerRepository.updateOvertime(id = overtimeId, ratePerHour = rate).onSuccess {
                AppLogger.i("Staff", "Overtime rate updated")
                _uiState.update { it.copy(isSaving = false, showEditRateDialog = false) }
                // Refresh the overtime list for the current worker
                val workerId = s.overtimeWorkerId
                if (workerId != null) refreshOvertimeForWorker(workerId)
                workerRepository.refreshSalaryPayments()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                AppLogger.e("Staff", "Failed to update overtime rate", e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun deleteOvertime(overtimeId: String) {
        AppLogger.d("Staff", "Deleting overtime: id=$overtimeId")
        viewModelScope.launch {
            workerRepository.deleteOvertime(overtimeId).onSuccess {
                workerRepository.refreshOvertime()
                workerRepository.refreshSalaryPayments()
            }
        }
    }

    fun refreshOvertimeForWorker(workerId: String) {
        _uiState.update { it.copy(overtimeWorkerId = workerId) }
        viewModelScope.launch {
            workerRepository.refreshOvertime(workerId = workerId).onSuccess { entries ->
                _uiState.update { it.copy(overtimeEntries = entries) }
            }
        }
    }

    fun markOvertimePaid(overtimeId: String) {
        viewModelScope.launch {
            workerRepository.markOvertimePaid(overtimeId).onSuccess {
                val workerId = _uiState.value.overtimeWorkerId
                if (workerId != null) refreshOvertimeForWorker(workerId)
                workerRepository.refreshSalaryPayments()
            }
        }
    }

    fun markOvertimeUnpaid(overtimeId: String) {
        viewModelScope.launch {
            workerRepository.markOvertimeUnpaid(overtimeId).onSuccess {
                val workerId = _uiState.value.overtimeWorkerId
                if (workerId != null) refreshOvertimeForWorker(workerId)
                workerRepository.refreshSalaryPayments()
            }
        }
    }

    fun toggleOvertimeSelection(overtimeId: String) {
        _uiState.update {
            val current = it.selectedOvertimeIds
            it.copy(selectedOvertimeIds = if (overtimeId in current) current - overtimeId else current + overtimeId)
        }
    }

    fun clearOvertimeSelection() {
        _uiState.update { it.copy(selectedOvertimeIds = emptySet()) }
    }

    fun batchPayOvertime() {
        val ids = _uiState.value.selectedOvertimeIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            workerRepository.batchPayOvertime(ids.toList()).onSuccess {
                _uiState.update { it.copy(selectedOvertimeIds = emptySet()) }
                val workerId = _uiState.value.overtimeWorkerId
                if (workerId != null) refreshOvertimeForWorker(workerId)
                workerRepository.refreshSalaryPayments()
            }
        }
    }
}
