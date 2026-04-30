package net.marllex.waselak.manager.installments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.domain.repository.CustomerRepository
import net.marllex.waselak.core.domain.repository.InstallmentRepository
import net.marllex.waselak.core.model.Customer
import net.marllex.waselak.core.model.InstallmentAnalytics
import net.marllex.waselak.core.model.InstallmentPayment
import net.marllex.waselak.core.model.InstallmentPlan
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class InstallmentsViewModel(
    private val installmentRepository: InstallmentRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Installments" }

    data class UiState(
        val plans: List<InstallmentPlan> = emptyList(),
        val analytics: InstallmentAnalytics? = null,
        val selectedPlan: InstallmentPlan? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedTab: Int = 0,
        val statusFilter: String? = null,
        // Create plan dialog
        val showCreateDialog: Boolean = false,
        val createCustomerId: String = "",
        val createCustomerName: String = "",
        val createTotalAmount: String = "",
        val createDownPayment: String = "0",
        val createMonths: String = "3",
        val createLateFeePercent: String = "0",
        val createStartMonth: Int = 0, // 0=this month, 1=next month, 2=+2 months, etc.
        val isCreating: Boolean = false,
        // Customer selection
        val customers: List<Customer> = emptyList(),
        val customerSearchQuery: String = "",
        val selectedCustomer: Customer? = null,
        // Create new customer
        val showCreateCustomer: Boolean = false,
        val newCustomerPhone: String = "",
        val newCustomerName: String = "",
        val newCustomerAddress: String = "",
        val isCreatingCustomer: Boolean = false,
        val createCustomerError: String? = null,
        // Record payment dialog
        val showPaymentDialog: Boolean = false,
        val targetPaymentId: String? = null,
        val paymentAmount: String = "",
        val paymentNote: String = "",
        val isSaving: Boolean = false,
        // Status update
        val showStatusDialog: InstallmentPlan? = null,
        val successMessage: String? = null,
    ) {
        val filteredPlans: List<InstallmentPlan>
            get() = if (statusFilter == null) plans
            else plans.filter { it.status == statusFilter }

        val filteredCustomers: List<Customer>
            get() = if (customerSearchQuery.isBlank()) customers
            else customers.filter {
                (it.name?.contains(customerSearchQuery, ignoreCase = true) == true) ||
                    it.phone.contains(customerSearchQuery)
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        loadCustomers()
    }

    fun load() {
        CrashReporter.addBreadcrumb("load()", TAG)
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            installmentRepository.getPlans()
                .onSuccess { list ->
                    AppLogger.i(TAG, "Loaded ${list.size} plans")
                    _uiState.update { it.copy(plans = list, isLoading = false) }
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e)
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
        viewModelScope.launch {
            installmentRepository.getAnalytics()
                .onSuccess { a -> _uiState.update { it.copy(analytics = a) } }
        }
    }

    private fun loadCustomers() {
        viewModelScope.launch {
            customerRepository.refreshCustomers()
            customerRepository.getCustomers().collect { list ->
                _uiState.update { it.copy(customers = list) }
            }
        }
    }

    fun onCustomerSearch(query: String) {
        _uiState.update { it.copy(customerSearchQuery = query) }
    }

    fun selectCustomer(customer: Customer) {
        _uiState.update {
            it.copy(
                selectedCustomer = customer,
                createCustomerId = customer.id,
                createCustomerName = customer.name ?: customer.phone,
                customerSearchQuery = "",
            )
        }
    }

    fun clearCustomerSelection() {
        _uiState.update {
            it.copy(selectedCustomer = null, createCustomerId = "", createCustomerName = "")
        }
    }

    fun showCreateCustomerForm() {
        _uiState.update { it.copy(showCreateCustomer = true, newCustomerPhone = "", newCustomerName = "", newCustomerAddress = "") }
    }

    fun hideCreateCustomerForm() {
        _uiState.update { it.copy(showCreateCustomer = false) }
    }

    fun onNewCustomerPhone(v: String) {
        val digitsOnly = v.filter { it.isDigit() || it == '+' }
        _uiState.update { it.copy(newCustomerPhone = digitsOnly, createCustomerError = null) }
    }
    fun onNewCustomerName(v: String) { _uiState.update { it.copy(newCustomerName = v, createCustomerError = null) } }
    fun onNewCustomerAddress(v: String) { _uiState.update { it.copy(newCustomerAddress = v) } }

    fun createNewCustomer() {
        val s = _uiState.value
        if (s.newCustomerPhone.isBlank() || s.newCustomerName.isBlank()) return

        // Check locally first if phone already exists
        val existing = s.customers.find { it.phone == s.newCustomerPhone }
        if (existing != null) {
            _uiState.update {
                it.copy(createCustomerError = "phone_exists:${existing.name ?: existing.phone}")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingCustomer = true, createCustomerError = null) }
            customerRepository.createCustomer(
                phone = s.newCustomerPhone,
                name = s.newCustomerName,
            ).onSuccess { customer ->
                // Add address if provided
                if (s.newCustomerAddress.isNotBlank()) {
                    customerRepository.addAddress(
                        customerId = customer.id,
                        address = s.newCustomerAddress,
                        isDefault = true,
                    )
                }
                _uiState.update {
                    it.copy(
                        isCreatingCustomer = false,
                        showCreateCustomer = false,
                        createCustomerError = null,
                        selectedCustomer = customer,
                        createCustomerId = customer.id,
                        createCustomerName = customer.name ?: customer.phone,
                        customerSearchQuery = "",
                    )
                }
                loadCustomers() // Refresh list
            }.onFailure { e ->
                val msg = e.message ?: ""
                if (msg.contains("PHONE_EXISTS") || msg.contains("Conflict")) {
                    _uiState.update { it.copy(isCreatingCustomer = false, createCustomerError = "phone_exists:${s.newCustomerPhone}") }
                } else {
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isCreatingCustomer = false, error = e.message) }
                }
            }
        }
    }

    fun onStatusFilter(status: String?) {
        _uiState.update { it.copy(statusFilter = status) }
    }

    fun selectPlan(plan: InstallmentPlan) {
        CrashReporter.addBreadcrumb("selectPlan(${plan.id})", TAG)
        viewModelScope.launch {
            installmentRepository.getPlan(plan.id)
                .onSuccess { full -> _uiState.update { it.copy(selectedPlan = full) } }
                .onFailure { _uiState.update { it.copy(selectedPlan = plan) } }
        }
    }

    fun clearSelection() { _uiState.update { it.copy(selectedPlan = null) } }
    fun clearSuccessMessage() { _uiState.update { it.copy(successMessage = null) } }

    // ── Create Plan ──

    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                createCustomerId = "", createCustomerName = "",
                createTotalAmount = "", createDownPayment = "0",
                createMonths = "3", createLateFeePercent = "0", createStartMonth = 0,
                selectedCustomer = null, customerSearchQuery = "",
                showCreateCustomer = false, newCustomerPhone = "", newCustomerName = "", newCustomerAddress = "",
            )
        }
    }

    fun dismissCreateDialog() { _uiState.update { it.copy(showCreateDialog = false) } }
    fun onCreateCustomerId(v: String) { _uiState.update { it.copy(createCustomerId = v) } }
    fun onCreateCustomerName(v: String) { _uiState.update { it.copy(createCustomerName = v) } }
    fun onCreateTotalAmount(v: String) { _uiState.update { it.copy(createTotalAmount = v) } }
    fun onCreateDownPayment(v: String) { _uiState.update { it.copy(createDownPayment = v) } }
    fun onCreateMonths(v: String) { _uiState.update { it.copy(createMonths = v) } }
    fun onCreateLateFeePercent(v: String) { _uiState.update { it.copy(createLateFeePercent = v) } }
    fun onCreateStartMonth(v: Int) { _uiState.update { it.copy(createStartMonth = v) } }

    fun createPlan() {
        CrashReporter.addBreadcrumb("createPlan()", TAG)
        val s = _uiState.value
        val total = s.createTotalAmount.toDoubleOrNull() ?: return
        val down = s.createDownPayment.toDoubleOrNull() ?: 0.0
        val months = s.createMonths.toIntOrNull() ?: return
        val fee = s.createLateFeePercent.toDoubleOrNull() ?: 0.0
        if (s.createCustomerId.isBlank() || total <= 0 || months <= 0) return

        // Calculate start date based on selected month offset. Was on
        // java.time.LocalDate which is JVM-only — kotlinx.datetime works
        // on iOS too. Same arithmetic: today → first-of-month at offset
        // months → midnight in the device's local zone → epoch millis.
        val startDate = if (s.createStartMonth > 0) {
            val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
            val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(tz).date
            val startMonth = today
                .plus(s.createStartMonth, kotlinx.datetime.DateTimeUnit.MONTH)
                .let { kotlinx.datetime.LocalDate(it.year, it.monthNumber, 1) }
            startMonth.atStartOfDayIn(tz).toEpochMilliseconds()
        } else null // null = backend uses current time

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }
            installmentRepository.createPlan(
                customerId = s.createCustomerId,
                totalAmount = total,
                numInstallments = months,
                downPayment = down,
                lateFeePercent = fee,
                startDate = startDate,
            )
                .onSuccess {
                    AppLogger.i(TAG, "Plan created successfully")
                    _uiState.update { it.copy(isCreating = false, showCreateDialog = false, successMessage = "plan_created") }
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isCreating = false, error = e.message) }
                }
        }
    }

    // ── Record Payment ──

    fun showPaymentDialog() { showPaymentDialogForPayment(null) }

    fun showPaymentDialogForPayment(paymentId: String?) {
        _uiState.update { it.copy(showPaymentDialog = true, targetPaymentId = paymentId, paymentAmount = "", paymentNote = "") }
    }

    fun dismissPaymentDialog() { _uiState.update { it.copy(showPaymentDialog = false) } }
    fun onPaymentAmount(v: String) { _uiState.update { it.copy(paymentAmount = v) } }
    fun onPaymentNote(v: String) { _uiState.update { it.copy(paymentNote = v) } }

    fun recordPayment() {
        CrashReporter.addBreadcrumb("recordPayment()", TAG)
        val s = _uiState.value
        val plan = s.selectedPlan ?: return
        val amount = s.paymentAmount.toDoubleOrNull() ?: return
        if (amount <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            installmentRepository.recordPayment(plan.id, amount, s.paymentNote.ifBlank { null }, s.targetPaymentId)
                .onSuccess {
                    AppLogger.i(TAG, "Payment recorded")
                    _uiState.update { it.copy(isSaving = false, showPaymentDialog = false, successMessage = "payment_recorded") }
                    selectPlan(plan)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    // ── Status Update ──

    fun showStatusDialog(plan: InstallmentPlan) { _uiState.update { it.copy(showStatusDialog = plan) } }
    fun dismissStatusDialog() { _uiState.update { it.copy(showStatusDialog = null) } }

    fun updateStatus(status: String) {
        CrashReporter.addBreadcrumb("updateStatus($status)", TAG)
        val plan = _uiState.value.showStatusDialog ?: return
        viewModelScope.launch {
            installmentRepository.updatePlanStatus(plan.id, status)
                .onSuccess {
                    _uiState.update { it.copy(showStatusDialog = null, successMessage = "status_updated") }
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ── Late Fee ──

    fun applyLateFeeForPayment(paymentId: String) {
        CrashReporter.addBreadcrumb("applyLateFee($paymentId)", TAG)
        val plan = _uiState.value.selectedPlan ?: return
        viewModelScope.launch {
            installmentRepository.applyLateFee(plan.id, paymentId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "late_fee_applied") }
                    selectPlan(plan)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun applyLateFee() {
        CrashReporter.addBreadcrumb("applyLateFee()", TAG)
        val plan = _uiState.value.selectedPlan ?: return
        viewModelScope.launch {
            installmentRepository.applyLateFee(plan.id)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "late_fee_applied") }
                    selectPlan(plan)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    // ── Toggle Late Fee ──

    fun toggleLateFee(paymentId: String, enabled: Boolean) {
        CrashReporter.addBreadcrumb("toggleLateFee($paymentId, $enabled)", TAG)
        val plan = _uiState.value.selectedPlan ?: return
        viewModelScope.launch {
            installmentRepository.toggleLateFee(plan.id, paymentId, enabled)
                .onSuccess {
                    selectPlan(plan)
                    load()
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }
}
