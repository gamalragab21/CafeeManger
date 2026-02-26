package net.marllex.waselak.manager.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.UpdateUserRequest

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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadVendor()
        loadManagerName()
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
                    )
                }
            }
        }
    }

    fun startEditing() {
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

    fun cancelEditing() { _uiState.update { it.copy(isEditing = false) } }
    fun updateName(v: String) { _uiState.update { it.copy(editName = v) } }
    fun updateAddress(v: String) { _uiState.update { it.copy(editAddress = v) } }
    fun updateContactPhone(v: String) { _uiState.update { it.copy(editContactPhone = v) } }
    fun updateWalletPhone(v: String) { _uiState.update { it.copy(editWalletPhone = v) } }
    fun updateLogoUrl(v: String) { _uiState.update { it.copy(editLogoUrl = v) } }
    fun updateManagerName(v: String) { _uiState.update { it.copy(editManagerName = v) } }

    fun saveProfile() {
        val s = _uiState.value
        if (s.editName.isBlank() || s.editAddress.isBlank()) return
        viewModelScope.launch {
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
                _uiState.update { it.copy(isSaving = false, isEditing = false, saveSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateStoreConfiguration(
        offlineModeEnabled: Boolean? = null,
        biometricRequired: Boolean? = null,
    ) {
        viewModelScope.launch {
            vendorRepository.updateVendor(
                offlineModeEnabled = offlineModeEnabled,
                biometricRequired = biometricRequired,
            ).onSuccess {
                vendorRepository.refreshVendor()
            }
        }
    }

    fun clearSaveSuccess() { _uiState.update { it.copy(saveSuccess = false) } }
}
