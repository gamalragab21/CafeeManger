package net.marllex.waselak.manager.myaccount

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

/**
 * The manager's personal "My Account" screen. Today it holds:
 *   • Identity header (name + role)
 *   • Card: POS Override PIN — set/change (the whole reason this screen exists)
 *   • Logout row
 *
 * Deliberately **small**. It's a natural home for future self-service: change
 * password, change photo, notification prefs. The PIN is never shown back to
 * the manager — only "set" / "not set" status + ability to replace it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAccountScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val viewModel: MyAccountViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("حسابي") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Identity header ──────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.userName.ifBlank { "-" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = state.userPhone.ifBlank { "" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ─── Override PIN card ────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "رمز موافقة الخصم (PIN)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "يستخدم الكاشير هذا الرمز لطلب موافقتك على الخصومات من جهاز البيع. " +
                            "لا يراه أحد غيرك، ويمكنك تغييره في أي وقت.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (statusText, statusColor) = if (state.pinSet) {
                            "✓ تم التعيين" to Color(0xFF2E7D32)
                        } else {
                            "⚠️ لم يتم التعيين بعد" to Color(0xFFE65100)
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { showPinDialog = true },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(if (state.pinSet) "تغيير الرمز" else "تعيين الرمز")
                        }
                    }
                    // Confirmation of the last change, rendered in subtle text.
                    state.lastSuccessMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(Modifier.size(4.dp))
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32),
                            )
                        }
                    }
                }
            }

            // ─── Logout row ───────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Surface(
                    onClick = onSignOut,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "تسجيل الخروج",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        SetOverridePinDialog(
            isChanging = state.pinSet,
            submitting = state.submitting,
            error = state.dialogError,
            onDismiss = {
                showPinDialog = false
                viewModel.clearDialogState()
            },
            onSubmit = { password, pin ->
                viewModel.setOverridePin(password, pin) { success ->
                    if (success) showPinDialog = false
                }
            },
        )
    }
}

/**
 * Dialog for setting or changing the override PIN. Always asks for the current
 * password so a briefly unlocked device can't silently overwrite the PIN.
 */
@Composable
private fun SetOverridePinDialog(
    isChanging: Boolean,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (password: String, pin: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    val mismatched = pin.isNotEmpty() && pinConfirm.isNotEmpty() && pin != pinConfirm
    val pinLenOk = pin.length in 4..6 && pin.all { it.isDigit() }
    val canSubmit = password.isNotBlank() && pinLenOk && pin == pinConfirm && !submitting

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = { onSubmit(password, pin) },
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
        title = { Text(if (isChanging) "تغيير رمز الخصم" else "تعيين رمز الخصم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "اختر رمزاً مكوّناً من 4 إلى 6 أرقام. لا تشاركه مع أحد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة السر الحالية") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) pin = new },
                    label = { Text("الرمز الجديد") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pinConfirm,
                    onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) pinConfirm = new },
                    label = { Text("تأكيد الرمز") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = mismatched,
                    supportingText = if (mismatched) { { Text("الرمز غير متطابق") } } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    HorizontalDivider()
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}
