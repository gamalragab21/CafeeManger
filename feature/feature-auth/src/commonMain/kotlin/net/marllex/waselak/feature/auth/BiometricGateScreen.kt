package net.marllex.waselak.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
 * Security gate screen shown before granting access to the app.
 *
 * - On devices with biometric hardware (Android/iOS): shows biometric prompt
 * - On desktop (no biometric): shows a password verification form
 *
 * @param biometricAuth platform-specific authenticator
 * @param onSuccess called when authentication succeeds
 * @param onVerifyPassword called on desktop to verify password via API; returns null on success, error message on failure
 */
@Composable
fun BiometricGateScreen(
    biometricAuth: BiometricAuthenticator,
    onSuccess: () -> Unit,
    onVerifyPassword: (suspend (password: String) -> String?)? = null,
) {
    if (biometricAuth.hasBiometricHardware) {
        BiometricGateContent(
            biometricAuth = biometricAuth,
            onSuccess = onSuccess,
        )
    } else {
        PasswordGateContent(
            onSuccess = onSuccess,
            onVerifyPassword = onVerifyPassword,
        )
    }
}

/**
 * Biometric gate for Android/iOS — shows fingerprint/face prompt.
 */
@Composable
private fun BiometricGateContent(
    biometricAuth: BiometricAuthenticator,
    onSuccess: () -> Unit,
) {
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Resolve string resources at composition time for use in coroutines
    val authReason = stringResource(Res.string.biometric_subtitle)
    val cancelledMsg = stringResource(Res.string.biometric_cancelled)
    val notAvailableMsg = stringResource(Res.string.biometric_error)

    fun doAuthenticate() {
        scope.launch {
            errorMessage = null
            when (val result = biometricAuth.authenticate(authReason)) {
                is BiometricResult.Success -> onSuccess()
                is BiometricResult.Cancelled -> errorMessage = cancelledMsg
                is BiometricResult.Error -> errorMessage = result.message
                is BiometricResult.NotAvailable -> errorMessage = notAvailableMsg
            }
        }
    }

    // Auto-trigger biometric on first composition
    LaunchedEffect(Unit) {
        when (val result = biometricAuth.authenticate(authReason)) {
            is BiometricResult.Success -> onSuccess()
            is BiometricResult.Cancelled -> errorMessage = cancelledMsg
            is BiometricResult.Error -> errorMessage = result.message
            is BiometricResult.NotAvailable -> errorMessage = notAvailableMsg
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
                contentDescription = "Waslek",
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

            // Fingerprint icon button
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

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Password gate for Desktop — shows password field with verify button.
 */
@Composable
private fun PasswordGateContent(
    onSuccess: () -> Unit,
    onVerifyPassword: (suspend (password: String) -> String?)?,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val verifyPasswordLabel = stringResource(Res.string.desktop_verify_subtitle)

    fun doVerify() {
        if (password.isBlank() || onVerifyPassword == null) return
        scope.launch {
            isLoading = true
            errorMessage = null
            val error = onVerifyPassword(password)
            isLoading = false
            if (error == null) {
                onSuccess()
            } else {
                errorMessage = error
            }
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
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp),
        ) {
            // App logo
            Image(
                painter = painterResource(CoreRes.drawable.waslek_logo),
                contentDescription = "Waslek",
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
                text = verifyPasswordLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text(stringResource(Res.string.password)) },
                placeholder = { Text(stringResource(Res.string.password_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Rounded.VisibilityOff
                            else Icons.Rounded.Visibility,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { doVerify() },
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
            )

            // Error message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Verify button
            Button(
                onClick = { doVerify() },
                enabled = !isLoading && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.verify_button),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
