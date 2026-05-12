package net.marllex.waselak.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.auth.generated.resources.Res
import net.marllex.waselak.feature.auth.generated.resources.*
import waselak.core.core_ui.generated.resources.Res as CoreRes
import waselak.core.core_ui.generated.resources.waslek_logo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.core.ui.components.LanguageSelector
import net.marllex.waselak.feature.auth.biometric.rememberBiometricAuthenticator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    appType: String = "MANAGER",
    viewModel: LoginViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // Use null initial so we can distinguish "loading" from "not logged in"
    val isLoggedIn by viewModel.isLoggedIn.collectAsState(initial = null)
    var showBiometricGate by remember { mutableStateOf(false) }
    // Track whether we already handled navigation to prevent double-fire
    var navigated by remember { mutableStateOf(false) }
    // Track manual login so the LaunchedEffect doesn't also trigger
    var manualLoginDone by remember { mutableStateOf(false) }

    val biometricAuth = rememberBiometricAuthenticator()

    // Navigate through biometric gate only on platforms with system auth
    val navigateWithBiometric: () -> Unit = {
        if (!navigated) {
            if (biometricAuth.isAvailable()) {
                showBiometricGate = true
            } else {
                // Desktop or no system auth — skip gate
                navigated = true
                onLoginSuccess()
            }
        }
    }

    // Auto-login: when isLoggedIn transitions to true (app reopen with saved token)
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn == true && !navigated && !manualLoginDone) {
            navigateWithBiometric()
        }
    }

    // Biometric gate overlay — shown after login success (auto or manual)
    if (showBiometricGate) {
        BiometricGateScreen(
            biometricAuth = biometricAuth,
            adminBypassCode = when (appType) {
                "MANAGER" -> "2580"
                "CASHIER" -> "1470"
                "KDS" -> "3690"
                "DELIVERY" -> "7531"
                else -> "0000"
            },
            onSuccess = {
                showBiometricGate = false
                navigated = true
                onLoginSuccess()
            },
        )
        return
    }

    // While loading initial state, show nothing (avoids login form flash)
    if (isLoggedIn == null || (isLoggedIn == true && !navigated)) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    // Manual login path: gate through biometric after successful credentials login
    val gatedOnLoginSuccess: () -> Unit = {
        manualLoginDone = true
        navigateWithBiometric()
    }

    LoginContent(
        uiState = uiState,
        appType = appType,
        onPhoneChange = viewModel::updatePhone,
        onPasswordChange = viewModel::updatePassword,
        onLoginClick = { viewModel.login(appType, gatedOnLoginSuccess) },
    )
}

@Composable
private fun LoginContent(
    uiState: LoginViewModel.UiState,
    appType: String,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val (appLabel, appSubtitle) = when (appType) {
        "MANAGER" -> Pair(
            stringResource(Res.string.manager_panel),
            stringResource(Res.string.manager_subtitle),
        )

        "CASHIER" -> Pair(
            stringResource(Res.string.cashier_panel),
            stringResource(Res.string.cashier_subtitle),
        )

        "DELIVERY" -> Pair(
            stringResource(Res.string.delivery_panel),
            stringResource(Res.string.delivery_subtitle),
        )

        else -> Pair(
            stringResource(Res.string.app_name),
            stringResource(Res.string.login_continue),
        )
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            MaterialTheme.colorScheme.background,
        ),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(innerPadding),
            // Tap-to-dismiss-keyboard is now app-wide via WaselakTheme →
            // DismissKeyboardOnOutsideTap, so no per-screen handler needed
            // here.
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Spacer(modifier = Modifier.height(100.dp))

            // App logo
            Image(
                painter = painterResource(CoreRes.drawable.waslek_logo),
                contentDescription = "Waslek",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = appLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = appSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(56.dp))

            // Phone field
            OutlinedTextField(
                value = uiState.phone,
                onValueChange = onPhoneChange,
                label = { Text(stringResource(Res.string.phone_number)) },
                placeholder = { Text(stringResource(Res.string.phone_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Phone,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
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
                    onDone = {
                        focusManager.clearFocus()
                        onLoginClick()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
            )

            // Error message
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Sign in button
            Button(
                onClick = onLoginClick,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 0.dp,
                ),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.sign_in),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            LanguageSelector(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            )
        }
        }
    }
}
