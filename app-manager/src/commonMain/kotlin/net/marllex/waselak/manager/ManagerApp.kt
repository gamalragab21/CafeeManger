package net.marllex.waselak.manager

import androidx.compose.runtime.*
import net.marllex.waselak.config.BuildConfig
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.ui.components.MandatoryUpdateScreen
import net.marllex.waselak.core.ui.theme.WaselakTheme
import net.marllex.waselak.manager.navigation.ManagerNavHost
import org.koin.compose.koinInject

@Composable
fun ManagerApp() {
    val authRepository: AuthRepository = koinInject()
    val apiClient: WaselakApiClient = koinInject()

    var mandatoryUpdate by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val resp = apiClient.checkForUpdate(
                app = "manager",
                version = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                variant = if (BuildConfig.IS_DEBUG) "debug" else "release",
            )
            if (resp.hasUpdate && resp.updateStatus == "MANDATORY") {
                mandatoryUpdate = Triple(resp.latestVersion, resp.releaseNotesAr ?: resp.releaseNotes, resp.downloadUrl)
            }
        } catch (_: Exception) { }
    }

    WaselakTheme {
        val update = mandatoryUpdate
        if (update != null) {
            MandatoryUpdateScreen(
                appName = "Waselak Manager",
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = update.first,
                releaseNotes = update.second,
                downloadUrl = update.third,
            )
        } else {
            ManagerNavHost(authRepository = authRepository)
        }
    }
}
