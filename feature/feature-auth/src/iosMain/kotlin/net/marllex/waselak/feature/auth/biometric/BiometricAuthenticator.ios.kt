package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Foundation.NSError
import kotlin.coroutines.resume

actual class BiometricAuthenticator {

    actual fun isAvailable(): Boolean {
        val context = LAContext()
        return context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error = null
        )
    }

    actual suspend fun authenticate(reason: String): BiometricResult {
        val context = LAContext()

        return suspendCancellableCoroutine { continuation ->
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = reason
            ) { success, error ->
                if (continuation.isActive) {
                    when {
                        success -> continuation.resume(BiometricResult.Success)
                        error != null -> {
                            val code = (error as NSError).code
                            when (code) {
                                -2L -> continuation.resume(BiometricResult.Cancelled) // userCancel
                                -4L -> continuation.resume(BiometricResult.Cancelled) // systemCancel
                                else -> continuation.resume(
                                    BiometricResult.Error(
                                        (error as NSError).localizedDescription
                                            ?: "Biometric authentication failed"
                                    )
                                )
                            }
                        }
                        else -> continuation.resume(BiometricResult.Error("Unknown error"))
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    return remember { BiometricAuthenticator() }
}
