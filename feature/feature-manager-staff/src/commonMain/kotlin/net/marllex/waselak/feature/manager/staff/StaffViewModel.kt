package net.marllex.waselak.feature.manager.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.*

class StaffViewModel constructor(
    private val workerRepository: WorkerRepository,
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

        // Add Worker Dialog - Multi-Step
        val showAddWorkerDialog: Boolean = false,
        val editingWorker: Worker? = null,
        val dialogStep: Int = 1, // 1 = Basic Info, 2 = System Access, 3 = Salary
        val dialogName: String = "",
        val dialogPhone: String = "",
        val dialogDescription: String = "",
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
        loadData()
        observeWorkers()
        observeSalaryPayments()
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                workerRepository.refreshWorkers()
                workerRepository.refreshWorkerRoles().onSuccess { roles ->
                    _uiState.update { it.copy(workerRoles = roles) }
                }
                workerRepository.getTodayAttendance().onSuccess { summary ->
                    _uiState.update { it.copy(todaySummary = summary) }
                }
                //workerRepository.refreshAttendance()
                refreshAttendance()
                workerRepository.refreshSalaryPayments()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
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
        _uiState.update {
            it.copy(
                showAddWorkerDialog = true,
                editingWorker = worker,
                dialogStep = 1, // Always start at step 1
                dialogName = worker?.fullName ?: "",
                dialogPhone = worker?.phone ?: "",
                dialogDescription = worker?.description ?: "",
                dialogRole = worker?.role ?: "",
                dialogWorkerType = if (worker?.isLoginEnabled == true) WorkerType.MAIN else WorkerType.NORMAL,
                dialogSalaryType = worker?.salaryType ?: SalaryType.DAILY,
                dialogSalaryAmount = worker?.salaryAmount?.let { a -> if (a > 0) a.toLong().let { l -> if (a == l.toDouble()) l.toString() else a.toString() } else "" } ?: "",
                dialogIsLoginEnabled = worker?.isLoginEnabled ?: false,
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

    fun dismissWorkerDialog() {
        _uiState.update { it.copy(showAddWorkerDialog = false, editingWorker = null, dialogStep = 1) }
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
        if (s.dialogName.isBlank() || s.dialogRole.isBlank()) return
        
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
            val amount = s.dialogSalaryAmount.toDoubleOrNull() ?: 0.0

            if (s.editingWorker != null) {
                workerRepository.updateWorker(
                    id = s.editingWorker.id,
                    fullName = s.dialogName,
                    phone = s.dialogPhone.ifBlank { null },
                    description = s.dialogDescription.ifBlank { null },
                    role = s.dialogRole,
                    salaryType = s.dialogSalaryType.name,
                    salaryAmount = amount,
                    pin = s.dialogPin.ifBlank { null }, // Only send PIN if not empty
                    active = null
                ).onSuccess {
                    _uiState.update { it.copy(isSaving = false, showAddWorkerDialog = false, editingWorker = null) }
                    loadData()
                }.onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
            } else {
                workerRepository.createWorker(
                    fullName = s.dialogName,
                    phone = s.dialogPhone.ifBlank { null },
                    description = s.dialogDescription.ifBlank { null },
                    role = s.dialogRole,
                    salaryType = s.dialogSalaryType,
                    salaryAmount = amount,
                    isLoginEnabled = s.dialogIsLoginEnabled,
                    password = if (s.dialogIsLoginEnabled) s.dialogPassword.ifBlank { null } else null,
                    loginRole = if (s.dialogIsLoginEnabled) s.dialogLoginRole else null,
                    pin = s.dialogPin
                ).onSuccess {
                    _uiState.update { it.copy(isSaving = false, showAddWorkerDialog = false) }
                    loadData()
                }.onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
            }
        }
    }

    fun toggleWorkerActive(worker: Worker) {
        viewModelScope.launch {
            workerRepository.updateWorker(
                id = worker.id, fullName = null, phone = null,
                description = null, role = null, salaryType = null,
                salaryAmount = null, active = !worker.active
            ).onSuccess { loadData() }
        }
    }

    fun showDeleteWorkerConfirm(worker: Worker) {
        _uiState.update { it.copy(showDeleteWorkerDialog = true, workerToDelete = worker) }
    }

    fun dismissDeleteWorkerDialog() {
        _uiState.update { it.copy(showDeleteWorkerDialog = false, workerToDelete = null) }
    }

    fun confirmDeleteWorker() {
        val worker = _uiState.value.workerToDelete ?: return
        viewModelScope.launch {
            workerRepository.deleteWorker(worker.id)
                .onSuccess {
                    _uiState.update { it.copy(showDeleteWorkerDialog = false, workerToDelete = null) }
                    loadData()
                }.onFailure { e ->
                    _uiState.update { it.copy(showDeleteWorkerDialog = false, error = e.message) }
                }
        }
    }

    fun filterByRole(role: String?) {
        _uiState.update { it.copy(selectedRoleFilter = role) }
    }

    // ─── Roles ───────────────────────────────────────────────────

    fun showAddRoleDialog() {
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
            _uiState.update { it.copy(isSaving = true) }
            workerRepository.createWorkerRole(s.dialogRoleName, s.dialogRoleDescription.ifBlank { null })
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, showAddRoleDialog = false) }
                    workerRepository.refreshWorkerRoles().onSuccess { roles ->
                        _uiState.update { it.copy(workerRoles = roles) }
                    }
                }.onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun showDeleteRoleConfirm(role: WorkerRole) {
        _uiState.update { it.copy(showDeleteRoleDialog = true, roleToDelete = role) }
    }

    fun dismissDeleteRoleDialog() {
        _uiState.update { it.copy(showDeleteRoleDialog = false, roleToDelete = null) }
    }

    fun confirmDeleteRole() {
        val role = _uiState.value.roleToDelete ?: return
        viewModelScope.launch {
            workerRepository.deleteWorkerRole(role.id)
                .onSuccess {
                    _uiState.update { it.copy(showDeleteRoleDialog = false, roleToDelete = null) }
                    workerRepository.refreshWorkerRoles().onSuccess { roles ->
                        _uiState.update { it.copy(workerRoles = roles) }
                    }
                }
        }
    }

    // ─── Salary ──────────────────────────────────────────────────

    fun selectWorkerForSalary(workerId: String?) {
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
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            workerRepository.batchPaySalaries(
                paymentIds = s.selectedPaymentIds.toList(),
                note = s.batchPayNote.ifBlank { null }
            ).onSuccess {
                _uiState.update {
                    it.copy(isSaving = false, showBatchPayDialog = false, selectedPaymentIds = emptySet())
                }
                workerRepository.refreshSalaryPayments()
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun markUnpaid(payment: SalaryPayment) {
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
