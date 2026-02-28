package net.marllex.waselak.manager.navigation

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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.marllex.waselak.core.ui.components.ErrorView
import net.marllex.waselak.core.ui.components.LoadingIndicator
import net.marllex.waselak.core.ui.components.WaslekLogo
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantProfileScreen(
    viewModel: RestaurantProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
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
                title = { Text("Store Profile") },
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
                        item { Text("Edit Store Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                        item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (uiState.editLogoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = uiState.editLogoUrl, contentDescription = null,
                                        modifier = Modifier.size(96.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    WaslekLogo(modifier = Modifier.size(96.dp).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = uiState.editLogoUrl, onValueChange = viewModel::updateLogoUrl,
                                    label = { Text("Logo URL") },
                                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                    supportingText = { Text("Enter your store logo URL") },
                                )
                            }
                        }
                        item {
                            OutlinedTextField(value = uiState.editManagerName, onValueChange = viewModel::updateManagerName, label = { Text("Manager Name") }, leadingIcon = { Icon(Icons.Filled.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        item {
                            OutlinedTextField(value = uiState.editName, onValueChange = viewModel::updateName, label = { Text("Store Name") }, leadingIcon = { Icon(Icons.Filled.Store, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        item {
                            OutlinedTextField(value = uiState.editAddress, onValueChange = viewModel::updateAddress, label = { Text("Address") }, leadingIcon = { Icon(Icons.Filled.LocationOn, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        item {
                            OutlinedTextField(value = uiState.editContactPhone, onValueChange = viewModel::updateContactPhone, label = { Text("Contact Phone") }, leadingIcon = { Icon(Icons.Filled.Phone, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        item {
                            OutlinedTextField(value = uiState.editWalletPhone, onValueChange = viewModel::updateWalletPhone, label = { Text("Wallet Phone (Optional)") }, leadingIcon = { Icon(Icons.Filled.Wallet, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = viewModel::cancelEditing, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                Button(onClick = viewModel::saveProfile, enabled = !uiState.isSaving && uiState.editName.isNotBlank() && uiState.editAddress.isNotBlank(), modifier = Modifier.weight(1f)) {
                                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else { Icon(Icons.Filled.Save, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Save") }
                                }
                            }
                        }
                    } else {
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
                                    Text("Store Information", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { ProfileInfoRow(Icons.Filled.Person, "Manager Name", uiState.managerName.ifBlank { "-" }) }
                        item { ProfileInfoRow(Icons.Filled.LocationOn, "Address", uiState.vendor?.address ?: "-") }
                        item { ProfileInfoRow(Icons.Filled.Phone, "Contact Phone", uiState.vendor?.contactPhone ?: "-") }
                        item { ProfileInfoRow(Icons.Filled.Wallet, "Wallet Phone (Optional)", uiState.vendor?.walletPhone ?: "-") }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::startEditing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Edit Profile")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(12.dp))
            Column { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}
