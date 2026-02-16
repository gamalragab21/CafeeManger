package net.marllex.waselak.feature.auth.biometric

sealed class BiometricResult {
    data object Success : BiometricResult()
    data class Error(val message: String) : BiometricResult()
    data object NotAvailable : BiometricResult()
    data object Cancelled : BiometricResult()
}
