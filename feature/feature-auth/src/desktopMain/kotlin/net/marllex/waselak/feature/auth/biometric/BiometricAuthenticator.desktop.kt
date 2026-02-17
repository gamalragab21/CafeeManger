package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class BiometricAuthenticator {

    /**
     * Desktop JVM has no system-level biometric/credential API.
     * Returns false so the app skips the biometric gate on desktop.
     */
    actual fun isAvailable(): Boolean = false

    actual suspend fun authenticate(reason: String): BiometricResult {
        return BiometricResult.NotAvailable
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    return remember { BiometricAuthenticator() }
}
