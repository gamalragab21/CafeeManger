package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable

expect class BiometricAuthenticator {
    /** Whether system authentication (biometric/PIN/pattern/passcode) is available. */
    fun isAvailable(): Boolean

    /** Triggers the system built-in authentication (fingerprint, face, PIN, pattern, passcode). */
    suspend fun authenticate(reason: String): BiometricResult
}

@Composable
expect fun rememberBiometricAuthenticator(): BiometricAuthenticator
