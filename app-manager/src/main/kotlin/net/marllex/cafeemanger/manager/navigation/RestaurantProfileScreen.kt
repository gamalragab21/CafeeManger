package net.marllex.cafeemanger.manager.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.VendorRepository
import net.marllex.cafeemanger.core.model.Vendor
import net.marllex.cafeemanger.core.ui.components.ErrorView
import net.marllex.cafeemanger.core.ui.components.LoadingIndicator
import net.marllex.cafeemanger.manager.R
import javax.inject.Inject

@HiltViewModel
class RestaurantProfileViewModel @Inject constructor(
    private val vendorRepository: VendorRepository,
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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadVendor() }

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
            )
        }
    }

    fun cancelEditing() { _uiState.update { it.copy(isEditing = false) } }
    fun updateName(v: String) { _uiState.update { it.copy(editName = v) } }
    fun updateAddress(v: String) { _uiState.update { it.copy(editAddress = v) } }
    fun updateContactPhone(v: String) { _uiState.update { it.copy(editContactPhone = v) } }
    fun updateWalletPhone(v: String) { _uiState.update { it.copy(editWalletPhone = v) } }
    fun updateLogoUrl(v: String) { _uiState.update { it.copy(editLogoUrl = v) } }

    fun saveProfile() {
        val s = _uiState.value
        if (s.editName.isBlank() || s.editAddress.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            // Manager only sends basic info + logo. Feature flags are NOT sent (admin-only via API).
            vendorRepository.updateVendor(
                name = s.editName,
                logoUrl = s.editLogoUrl.ifBlank { null },
                address = s.editAddress,
                contactPhone = s.editContactPhone,
                walletPhone = s.editWalletPhone.ifBlank { null },
            ).onSuccess {
                _uiState.update { it.copy(isSaving = false, isEditing = false, saveSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun clearSaveSuccess() { _uiState.update { it.copy(saveSuccess = false) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantProfileScreen(
    viewModel: RestaurantProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Profile updated successfully")
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.store_profile)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.vendor == null -> ErrorView(message = uiState.error!!, onRetry = viewModel::loadVendor)
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.isEditing) {
                        // ══ EDIT MODE ══
                        item { Text(stringResource(R.string.edit_store_info), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        // Logo preview + URL input
                        item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (uiState.editLogoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = uiState.editLogoUrl, contentDescription = null,
                                        modifier = Modifier.size(96.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(Modifier.size(96.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Store, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = uiState.editLogoUrl, onValueChange = viewModel::updateLogoUrl,
                                    label = { Text(stringResource(R.string.store_logo_url)) },
                                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                    supportingText = { Text(stringResource(R.string.store_logo_url_hint)) },
                                )
                            }
                        }
                        item {
                            OutlinedTextField(value = uiState.editName, onValueChange = viewModel::updateName, label = { Text(stringResource(R.string.store_name)) }, leadingIcon = { Icon(Icons.Filled.Store, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        item {
                            OutlinedTextField(value = uiState.editAddress, onValueChange = viewModel::updateAddress, label = { Text(stringResource(R.string.address)) }, leadingIcon = { Icon(Icons.Filled.LocationOn, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        item {
                            OutlinedTextField(value = uiState.editContactPhone, onValueChange = viewModel::updateContactPhone, label = { Text(stringResource(R.string.contact_phone)) }, leadingIcon = { Icon(Icons.Filled.Phone, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        item {
                            OutlinedTextField(value = uiState.editWalletPhone, onValueChange = viewModel::updateWalletPhone, label = { Text(stringResource(R.string.wallet_phone_optional)) }, leadingIcon = { Icon(Icons.Filled.Wallet, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        // Save/Cancel
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = viewModel::cancelEditing, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                                Button(onClick = viewModel::saveProfile, enabled = !uiState.isSaving && uiState.editName.isNotBlank() && uiState.editAddress.isNotBlank(), modifier = Modifier.weight(1f)) {
                                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else { Icon(Icons.Filled.Save, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.save)) }
                                }
                            }
                        }
                    } else {
                        // ══ VIEW MODE ══
                        item {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val logoUrl = uiState.vendor?.logoUrl
                                    if (!logoUrl.isNullOrBlank()) {
                                        AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.size(80.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape), contentScale = ContentScale.Crop)
                                        Spacer(Modifier.height(12.dp))
                                    }
                                    Text(uiState.vendor?.name ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(stringResource(R.string.store_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { ProfileInfoRow(Icons.Filled.LocationOn, stringResource(R.string.address), uiState.vendor?.address ?: "-") }
                        item { ProfileInfoRow(Icons.Filled.Phone, stringResource(R.string.contact_phone), uiState.vendor?.contactPhone ?: "-") }
                        item { ProfileInfoRow(Icons.Filled.Wallet, stringResource(R.string.wallet_phone_optional), uiState.vendor?.walletPhone ?: "-") }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::startEditing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.edit_profile))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(12.dp))
            Column { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
