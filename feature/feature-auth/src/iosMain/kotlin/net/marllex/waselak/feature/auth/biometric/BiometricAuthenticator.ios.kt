package net.marllex.waselak.feature.auth.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Foundation.NSError
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

// LAContext.canEvaluatePolicy/evaluatePolicy take ObjCObjectVar pointers via
// cinterop, which is gated on the foreign-API opt-in. Annotating the whole
// class so any new call sites here don't need their own opt-in.
@OptIn(ExperimentalForeignApi::class)
actual class BiometricAuthenticator {

    actual fun isAvailable(): Boolean {
        val context = LAContext()
        // Check biometric first, fall back to device passcode
        return context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error = null
        ) || context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthentication,
            error = null
        )
    }

    actual suspend fun authenticate(reason: String): BiometricResult {
        val context = LAContext()

        // Prefer biometric, fall back to device owner authentication (passcode)
        val policy = if (context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                error = null
            )
        ) {
            LAPolicyDeviceOwnerAuthenticationWithBiometrics
        } else if (context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthentication,
                error = null
            )
        ) {
            LAPolicyDeviceOwnerAuthentication
        } else {
            return BiometricResult.NotAvailable
        }

        return suspendCancellableCoroutine { continuation ->
            context.evaluatePolicy(
                policy,
                localizedReason = reason
            ) { success, error ->
                // Dispatch back to main thread to ensure safe coroutine resume
                dispatch_async(dispatch_get_main_queue()) {
                    if (continuation.isActive) {
                        when {
                            success -> continuation.resume(BiometricResult.Success)
                            error != null -> {
                                val nsError = error as NSError
                                val code = nsError.code
                                when (code) {
                                    -2L -> continuation.resume(BiometricResult.Cancelled) // userCancel
                                    -4L -> continuation.resume(BiometricResult.Cancelled) // systemCancel
                                    -3L -> continuation.resume(BiometricResult.Cancelled) // userFallback (passcode button)
                                    else -> continuation.resume(
                                        BiometricResult.Error(
                                            nsError.localizedDescription
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
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    return remember { BiometricAuthenticator() }
}
