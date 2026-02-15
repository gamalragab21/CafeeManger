package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable

expect class PlatformActions {
    fun openUrl(url: String)
    fun openMap(address: String?, lat: Double?, lng: Double?)
    fun showToast(message: String)
}

@Composable
expect fun rememberPlatformActions(): PlatformActions
