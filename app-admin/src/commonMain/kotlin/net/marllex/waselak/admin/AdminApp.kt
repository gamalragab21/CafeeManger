package net.marllex.waselak.admin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.marllex.waselak.admin.navigation.AdminNavHost
import net.marllex.waselak.core.ui.theme.WaselakTheme

// ─── Dark mode state (global, accessible from Settings) ──────────
object ThemeState {
    var isDarkMode by mutableStateOf(false)
    var useSystemTheme by mutableStateOf(true)
}

@Composable
fun AdminApp() {
    val useDark = if (ThemeState.useSystemTheme) {
        isSystemInDarkTheme()
    } else {
        ThemeState.isDarkMode
    }

    WaselakTheme(darkTheme = useDark) {
        AdminNavHost()
    }
}
