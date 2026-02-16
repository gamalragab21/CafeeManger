package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable

expect class BiometricAuthenticator {
    fun isAvailable(): Boolean
    suspend fun authenticate(reason: String): BiometricResult
}

@Composable
expect fun rememberBiometricAuthenticator(): BiometricAuthenticator
