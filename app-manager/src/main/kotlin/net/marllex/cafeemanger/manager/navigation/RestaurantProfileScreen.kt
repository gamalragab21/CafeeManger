package net.marllex.cafeemanger.manager.navigation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun updateName(v: String) { _uiState.update { it.copy(editName = v) } }
    fun updateAddress(v: String) { _uiState.update { it.copy(editAddress = v) } }
    fun updateContactPhone(v: String) { _uiState.update { it.copy(editContactPhone = v) } }
    fun updateWalletPhone(v: String) { _uiState.update { it.copy(editWalletPhone = v) } }

    fun saveProfile() {
        val s = _uiState.value
        if (s.editName.isBlank() || s.editAddress.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            vendorRepository.updateVendor(
                name = s.editName,
                logoUrl = null,
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

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantProfileScreen(
    viewModel: RestaurantProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Profile updated successfully")
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restaurant_profile)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null && uiState.vendor == null -> ErrorView(
                message = uiState.error!!,
                onRetry = viewModel::loadVendor,
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.isEditing) {
                        // Edit mode
                        item {
                            Text(
                                stringResource(R.string.edit_restaurant_info),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = uiState.editName,
                                onValueChange = viewModel::updateName,
                                label = { Text(stringResource(R.string.restaurant_name)) },
                                leadingIcon = { Icon(Icons.Filled.Store, null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = uiState.editAddress,
                                onValueChange = viewModel::updateAddress,
                                label = { Text(stringResource(R.string.address)) },
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = uiState.editContactPhone,
                                onValueChange = viewModel::updateContactPhone,
                                label = { Text(stringResource(R.string.contact_phone)) },
                                leadingIcon = { Icon(Icons.Filled.Phone, null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = uiState.editWalletPhone,
                                onValueChange = viewModel::updateWalletPhone,
                                label = { Text(stringResource(R.string.wallet_phone_optional)) },
                                leadingIcon = { Icon(Icons.Filled.Wallet, null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = viewModel::cancelEditing,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Button(
                                    onClick = viewModel::saveProfile,
                                    enabled = !uiState.isSaving && uiState.editName.isNotBlank() && uiState.editAddress.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (uiState.isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text(stringResource(R.string.save))
                                    }
                                }
                            }
                        }
                    } else {
                        // View mode
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                ),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = uiState.vendor?.name ?: "",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.restaurant_info),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        item {
                            ProfileInfoRow(
                                icon = Icons.Filled.LocationOn,
                                label = stringResource(R.string.address),
                                value = uiState.vendor?.address ?: "-",
                            )
                        }
                        item {
                            ProfileInfoRow(
                                icon = Icons.Filled.Phone,
                                label =stringResource(R.string.contact_phone),
                                value = uiState.vendor?.contactPhone ?: "-",
                            )
                        }
                        item {
                            ProfileInfoRow(
                                icon = Icons.Filled.Wallet,
                                label = "Wallet Phone",
                                value = uiState.vendor?.walletPhone ?: "Not set",
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = viewModel::startEditing,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.edit_profile))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
