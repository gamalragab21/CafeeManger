package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class BiometricAuthenticator {
    actual fun isAvailable(): Boolean = false
    actual suspend fun authenticate(reason: String): BiometricResult = BiometricResult.NotAvailable
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    return remember { BiometricAuthenticator() }
}
