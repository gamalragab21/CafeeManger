package net.marllex.waselak.manager.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.PlanFeaturesResponse
import net.marllex.waselak.core.network.dto.UpdateUserRequest
import net.marllex.waselak.core.common.crash.CrashReporter

class RestaurantProfileViewModel(
    private val vendorRepository: VendorRepository,
    private val authRepository: AuthRepository,
    private val api: WaselakApiClient,
) : ViewModel() {

    data class UiState(
        val vendor: Vendor? = null,
        val isLoading: Boolean = true,
        val isEditing: Boolean = false,
        val isSaving: Boolean = false,
        val error: String? = null,
        val saveSuccess: Boolean = false,
        val editName: String = "",
        val editAddress: String = "",
        val editContactPhone: String = "",
        val editWalletPhone: String = "",
        val editLogoUrl: String = "",
        val managerName: String = "",
        val editManagerName: String = "",
        val planInfo: PlanFeaturesResponse? = null,
        val planLoading: Boolean = false,
        // Loyalty & Discount settings
        val loyaltySaving: Boolean = false,
        // Tax settings — surfaced as their own pair so the screen can
        // show an in-flight spinner on the tax card independently of
        // the rest of the profile.
        val taxSaving: Boolean = false,
        val editTaxEnabled: Boolean = false,
        val editTaxPercent: String = "0",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadVendor()
        loadManagerName()
        loadPlan()
    }

    private fun loadPlan() {
        viewModelScope.launch {
            _uiState.update { it.copy(planLoading = true) }
            try {
                val plan = api.getMyPlan()
                _uiState.update { it.copy(planInfo = plan, planLoading = false) }
            } catch (e: Exception) {
                AppLogger.e("Profile", "Failed to load plan info", e)
                _uiState.update { it.copy(planLoading = false) }
            }
        }
    }

    private fun loadManagerName() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(managerName = user?.name ?: "", editManagerName = user?.name ?: "") }
            }
        }
    }

    fun loadVendor() {
        viewModelScope.launch {
            AppLogger.d("Profile", "Loading vendor profile")
            _uiState.update { it.copy(isLoading = true, error = null) }
            vendorRepository.refreshVendor()
            vendorRepository.getMyVendor().collect { vendor ->
                _uiState.update {
                    it.copy(
                        vendor = vendor,
                        isLoading = false,
                        editName = vendor?.name ?: "",
                        editAddress = vendor?.address ?: "",
                        editContactPhone = vendor?.contactPhone ?: "",
                        editWalletPhone = vendor?.walletPhone ?: "",
                        editLogoUrl = vendor?.logoUrl ?: "",
                        editTaxEnabled = vendor?.taxEnabled ?: false,
                        editTaxPercent = vendor?.defaultTaxPercent?.let {
                            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                        } ?: "0",
                    )
                }
            }
        }
    }

    fun startEditing() {
        AppLogger.d("Profile", "Started editing profile")
        val v = _uiState.value.vendor ?: return
        _uiState.update {
            it.copy(
                isEditing = true,
                editName = v.name,
                editAddress = v.address,
                editContactPhone = v.contactPhone,
                editWalletPhone = v.walletPhone ?: "",
                editLogoUrl = v.logoUrl ?: "",
                editManagerName = it.managerName,
            )
        }
    }

    fun cancelEditing() {
        AppLogger.d("Profile", "User cancelled editing")
        _uiState.update { it.copy(isEditing = false) }
    }
    fun updateName(v: String) { _uiState.update { it.copy(editName = v) } }
    fun updateAddress(v: String) { _uiState.update { it.copy(editAddress = v) } }
    fun updateContactPhone(v: String) { _uiState.update { it.copy(editContactPhone = v) } }
    fun updateWalletPhone(v: String) { _uiState.update { it.copy(editWalletPhone = v) } }
    fun updateLogoUrl(v: String) { _uiState.update { it.copy(editLogoUrl = v) } }
    fun uploadLogo(imageBytes: ByteArray) {
        viewModelScope.launch {
            AppLogger.d("Profile", "Uploading logo")
            try {
                val response = api.uploadImage(imageBytes, "vendor_logo_${kotlin.random.Random.nextInt(100000, 999999)}.jpg")
                _uiState.update { it.copy(editLogoUrl = response.url) }
                AppLogger.i("Profile", "Logo uploaded")
            } catch (e: Exception) {
                AppLogger.e("Profile", "Failed to upload logo", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    fun updateManagerName(v: String) { _uiState.update { it.copy(editManagerName = v) } }

    fun saveProfile() {
        val s = _uiState.value
        if (s.editName.isBlank() || s.editAddress.isBlank()) return
        viewModelScope.launch {
            AppLogger.d("Profile", "Saving profile: name=${s.editName}")
            _uiState.update { it.copy(isSaving = true) }
            vendorRepository.updateVendor(
                name = s.editName,
                logoUrl = s.editLogoUrl.ifBlank { null },
                address = s.editAddress,
                contactPhone = s.editContactPhone,
                walletPhone = s.editWalletPhone.ifBlank { null },
            ).onSuccess {
                if (s.editManagerName.isNotBlank() && s.editManagerName != s.managerName) {
                    try {
                        api.updateMyProfile(UpdateUserRequest(name = s.editManagerName))
                        _uiState.update { it.copy(managerName = s.editManagerName) }
                        authRepository.refreshToken()
                    } catch (_: Exception) { }
                }
                AppLogger.i("Profile", "Profile saved successfully")
                _uiState.update { it.copy(isSaving = false, isEditing = false, saveSuccess = true) }
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                AppLogger.e("Profile", "Failed to save profile", e)
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateStoreConfiguration(
        biometricRequired: Boolean? = null,
    ) {
        viewModelScope.launch {
            AppLogger.d("Profile", "Updating store configuration")
            vendorRepository.updateVendor(
                biometricRequired = biometricRequired,
            ).onSuccess {
                vendorRepository.refreshVendor()
            }
        }
    }

    // ─── Loyalty & Discount Settings ────────────────────────────

    fun toggleLoyalty(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(loyaltySaving = true) }
            vendorRepository.updateVendor(loyaltyEnabled = enabled).onSuccess {
                vendorRepository.refreshVendor()
            }
            _uiState.update { it.copy(loyaltySaving = false) }
        }
    }

    fun updateLoyaltySettings(
        pointsEarnRate: Double? = null,
        pointsRedeemRate: Double? = null,
        minPointsRedeem: Int? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(loyaltySaving = true) }
            vendorRepository.updateVendor(
                pointsEarnRate = pointsEarnRate,
                pointsRedeemRate = pointsRedeemRate,
                minPointsRedeem = minPointsRedeem,
            ).onSuccess {
                vendorRepository.refreshVendor()
                _uiState.update { it.copy(saveSuccess = true) }
            }
            _uiState.update { it.copy(loyaltySaving = false) }
        }
    }

    fun updateDiscountSettings(
        maxManualDiscountPercent: Double? = null,
        manualDiscountRequiresPin: Boolean? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(loyaltySaving = true) }
            vendorRepository.updateVendor(
                maxManualDiscountPercent = maxManualDiscountPercent,
                manualDiscountRequiresPin = manualDiscountRequiresPin,
            ).onSuccess {
                vendorRepository.refreshVendor()
                _uiState.update { it.copy(saveSuccess = true) }
            }
            _uiState.update { it.copy(loyaltySaving = false) }
        }
    }

    fun clearSaveSuccess() { _uiState.update { it.copy(saveSuccess = false) } }

    // ─── Tax Settings ───────────────────────────────────────────
    //
    // Two-step pattern: live edits update the local UI state (so the
    // user sees their typing reflected immediately), and [saveTaxSettings]
    // commits them to the backend. We refresh the vendor afterwards so
    // the cashier app's next read sees the new value.

    fun updateTaxEnabled(enabled: Boolean) {
        _uiState.update { it.copy(editTaxEnabled = enabled) }
    }

    fun updateTaxPercent(value: String) {
        // Only accept numeric input + optional single decimal point so a
        // bad paste doesn't put the field into a state the backend would
        // reject.
        if (value.isEmpty() || value.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
            _uiState.update { it.copy(editTaxPercent = value) }
        }
    }

    fun saveTaxSettings() {
        val s = _uiState.value
        val percent = s.editTaxPercent.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            AppLogger.d("Profile", "Saving tax settings: enabled=${s.editTaxEnabled} percent=$percent")
            _uiState.update { it.copy(taxSaving = true) }
            vendorRepository.updateVendor(
                taxEnabled = s.editTaxEnabled,
                defaultTaxPercent = percent,
            ).onSuccess {
                vendorRepository.refreshVendor()
                _uiState.update { it.copy(taxSaving = false, saveSuccess = true) }
            }.onFailure { e ->
                CrashReporter.captureException(e)
                AppLogger.e("Profile", "Failed to save tax settings", e)
                _uiState.update { it.copy(taxSaving = false, error = e.message) }
            }
        }
    }
}
