package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable

expect class BiometricAuthenticator {
    /** Whether the security gate is available (always true on all platforms). */
    fun isAvailable(): Boolean

    /** Whether the device has biometric hardware (fingerprint, face, etc.). */
    val hasBiometricHardware: Boolean

    /** Authenticate using biometric prompt (Android/iOS) or returns NotAvailable on desktop. */
    suspend fun authenticate(reason: String): BiometricResult
}

@Composable
expect fun rememberBiometricAuthenticator(): BiometricAuthenticator
