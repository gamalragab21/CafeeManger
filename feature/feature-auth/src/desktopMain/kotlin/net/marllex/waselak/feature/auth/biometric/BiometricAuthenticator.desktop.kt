package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class BiometricAuthenticator {

    /**
     * Desktop has no biometric hardware — the Compose BiometricGateScreen
     * will show a password verification UI instead.
     */
    actual val hasBiometricHardware: Boolean = false

    /**
     * Always true — the password-based security gate is always available on desktop.
     */
    actual fun isAvailable(): Boolean = true

    /**
     * Returns NotAvailable so that BiometricGateScreen knows to show
     * the password verification UI instead of a biometric prompt.
     */
    actual suspend fun authenticate(reason: String): BiometricResult {
        return BiometricResult.NotAvailable
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    return remember { BiometricAuthenticator() }
}
