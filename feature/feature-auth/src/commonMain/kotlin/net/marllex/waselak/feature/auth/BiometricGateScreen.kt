package net.marllex.waselak.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.marllex.waselak.feature.auth.biometric.BiometricAuthenticator
import net.marllex.waselak.feature.auth.biometric.BiometricResult
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.auth.generated.resources.Res
import net.marllex.waselak.feature.auth.generated.resources.*
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.waslek_logo

/**
 * Security gate screen with biometric + admin bypass code.
 *
 * Flow:
 * 1. Auto-trigger biometric on open
 * 2. If biometric succeeds → go home
 * 3. If biometric fails → show "Try Again" + "Use Admin Code" option
 * 4. Admin code is a static per-app PIN
 *
 * @param biometricAuth platform-specific authenticator
 * @param adminBypassCode static admin code per app (e.g. "1234" for manager, "5678" for cashier)
 * @param onSuccess called when authentication succeeds
 */
@Composable
fun BiometricGateScreen(
    biometricAuth: BiometricAuthenticator,
    adminBypassCode: String = "0000",
    onSuccess: () -> Unit,
) {
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdminCode by rememberSaveable { mutableStateOf(false) }
    var adminCodeInput by rememberSaveable { mutableStateOf("") }
    var adminCodeError by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val authReason = stringResource(Res.string.biometric_subtitle)
    val cancelledMsg = stringResource(Res.string.biometric_cancelled)

    fun doAuthenticate() {
        scope.launch {
            errorMessage = null
            when (val result = biometricAuth.authenticate(authReason)) {
                is BiometricResult.Success -> onSuccess()
                is BiometricResult.Cancelled -> {
                    errorMessage = cancelledMsg
                    showAdminCode = true
                }
                is BiometricResult.Error -> {
                    errorMessage = result.message
                    showAdminCode = true
                }
                is BiometricResult.NotAvailable -> onSuccess()
            }
        }
    }

    // Auto-trigger on first composition
    LaunchedEffect(Unit) {
        when (val result = biometricAuth.authenticate(authReason)) {
            is BiometricResult.Success -> onSuccess()
            is BiometricResult.Cancelled -> {
                errorMessage = cancelledMsg
                showAdminCode = true
            }
            is BiometricResult.Error -> {
                errorMessage = result.message
                showAdminCode = true
            }
            is BiometricResult.NotAvailable -> onSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            // App logo
            Image(
                painter = painterResource(CoreRes.drawable.waslek_logo),
                contentDescription = "Waselak",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(Res.string.verify_identity),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.biometric_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (!showAdminCode) {
                // Fingerprint icon button — tap to retry
                IconButton(
                    onClick = { doAuthenticate() },
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = stringResource(Res.string.tap_to_retry),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.tap_to_retry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            // Admin bypass code section — shows after biometric fails
            if (showAdminCode) {
                Spacer(modifier = Modifier.height(24.dp))

                // Try biometric again button
                OutlinedButton(onClick = { showAdminCode = false; doAuthenticate() }) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.tap_to_retry))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(0.8f),
                ) {
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Text(
                        text = "  OR  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = adminCodeInput,
                    onValueChange = {
                        adminCodeInput = it
                        adminCodeError = false
                    },
                    label = { Text("Admin Code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = adminCodeError,
                    supportingText = if (adminCodeError) {{ Text("Wrong code") }} else null,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (adminCodeInput == adminBypassCode) {
                            onSuccess()
                        } else {
                            adminCodeError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.6f),
                ) {
                    Text("Continue")
                }
            }
        }
    }
}
