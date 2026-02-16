package net.marllex.waselak.feature.auth.biometric

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class BiometricAuthenticator(private val context: Context) {

    actual fun isAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    actual suspend fun authenticate(reason: String): BiometricResult {
        val activity = context.findActivity()
            ?: return BiometricResult.Error("No activity found")

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            continuation.resume(BiometricResult.Cancelled)
                        } else {
                            continuation.resume(BiometricResult.Error(errString.toString()))
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    // Called on individual failed attempt (e.g. bad fingerprint)
                    // The prompt stays open, so do NOT resume here
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Waslek")
                .setSubtitle(reason)
                .setNegativeButtonText("Cancel")
                .build()

            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        }
    }

    private fun Context.findActivity(): FragmentActivity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    val context = LocalContext.current
    return remember(context) { BiometricAuthenticator(context) }
}
