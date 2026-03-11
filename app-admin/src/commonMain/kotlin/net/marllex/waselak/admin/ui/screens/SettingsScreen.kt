package net.marllex.waselak.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.marllex.waselak.admin.ThemeState
import net.marllex.waselak.admin.util.LocalWindowSizeClass
import net.marllex.waselak.admin.util.UiMessage
import net.marllex.waselak.admin.util.WindowWidthSizeClass
import net.marllex.waselak.admin.util.padZero
import net.marllex.waselak.admin.util.resolve
import net.marllex.waselak.admin.viewmodel.SettingsViewModel
import net.marllex.waselak.core.ui.components.LanguageSelector
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val widthClass = LocalWindowSizeClass.current
    val profile by viewModel.profile.collectAsState()
    val currentPassword by viewModel.currentPassword.collectAsState()
    val newPassword by viewModel.newPassword.collectAsState()
    val confirmPassword by viewModel.confirmPassword.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val resolvedMessage = message?.resolve()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    LaunchedEffect(message) {
        resolvedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            // ─── Profile & Appearance Sections ─────────────────
            if (widthClass == WindowWidthSizeClass.EXPANDED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Profile card
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.profile),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            HorizontalDivider()

                            if (profile != null) {
                                val p = profile!!
                                ProfileRow(stringResource(Res.string.name), p.name)
                                ProfileRow(stringResource(Res.string.email), p.email)
                                ProfileRow(
                                    stringResource(Res.string.last_login),
                                    if (p.last_login_at != null) formatTimestamp(p.last_login_at)
                                    else stringResource(Res.string.never)
                                )
                                ProfileRow(
                                    stringResource(Res.string.account_status),
                                    if (p.active) stringResource(Res.string.active)
                                    else stringResource(Res.string.inactive)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    // Appearance card
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.appearance),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.use_system_theme),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Switch(
                                    checked = ThemeState.useSystemTheme,
                                    onCheckedChange = { ThemeState.useSystemTheme = it },
                                )
                            }

                            if (!ThemeState.useSystemTheme) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (ThemeState.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(Res.string.dark_mode),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    Switch(
                                        checked = ThemeState.isDarkMode,
                                        onCheckedChange = { ThemeState.isDarkMode = it },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ─── Profile Section ──────────────────────────────
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.profile),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        HorizontalDivider()

                        if (profile != null) {
                            val p = profile!!
                            ProfileRow(stringResource(Res.string.name), p.name)
                            ProfileRow(stringResource(Res.string.email), p.email)
                            ProfileRow(
                                stringResource(Res.string.last_login),
                                if (p.last_login_at != null) formatTimestamp(p.last_login_at)
                                else stringResource(Res.string.never)
                            )
                            ProfileRow(
                                stringResource(Res.string.account_status),
                                if (p.active) stringResource(Res.string.active)
                                else stringResource(Res.string.inactive)
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                // ─── Appearance Section (Dark/Light mode) ─────────
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.appearance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.use_system_theme),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Switch(
                                checked = ThemeState.useSystemTheme,
                                onCheckedChange = { ThemeState.useSystemTheme = it },
                            )
                        }

                        if (!ThemeState.useSystemTheme) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = if (ThemeState.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = stringResource(Res.string.dark_mode),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                Switch(
                                    checked = ThemeState.isDarkMode,
                                    onCheckedChange = { ThemeState.isDarkMode = it },
                                )
                            }
                        }
                    }
                }
            }

            // ─── Language Section ─────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageSelector(modifier = Modifier.fillMaxWidth())
                }
            }

            // ─── Change Password Section ──────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.change_password),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider()

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { viewModel.updateCurrentPassword(it) },
                        label = { Text(stringResource(Res.string.current_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { viewModel.updateNewPassword(it) },
                        label = { Text(stringResource(Res.string.new_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { viewModel.updateConfirmPassword(it) },
                        label = { Text(stringResource(Res.string.confirm_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Button(
                        onClick = { viewModel.changePassword() },
                        enabled = !isLoading && currentPassword.isNotBlank() &&
                                newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(Res.string.change_password))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Logout Button ────────────────────────────────
            Button(
                onClick = { viewModel.logout(onLogout) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(Res.string.logout))
            }

            Spacer(Modifier.height(32.dp))
        }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    val seconds = epochMillis / 1000
    val minutes = (seconds / 60) % 60
    val hours = (seconds / 3600) % 24
    val days = seconds / 86400
    val year = 1970 + (days / 365.25).toInt()
    val remainingDays = (days % 365.25).toInt()
    val month = (remainingDays / 30) + 1
    val day = (remainingDays % 30) + 1
    return "${padZero(year, 4)}-${padZero(month.coerceIn(1, 12), 2)}-${padZero(day.coerceIn(1, 31), 2)} ${padZero(hours.toInt(), 2)}:${padZero(minutes.toInt(), 2)}"
}
