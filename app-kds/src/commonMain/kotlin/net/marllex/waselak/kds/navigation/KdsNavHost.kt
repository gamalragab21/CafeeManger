package net.marllex.waselak.kds.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import net.marllex.waselak.core.common.crash.CrashReporter
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.drop
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.feature.auth.navigation.AUTH_ROUTE
import net.marllex.waselak.feature.auth.navigation.authScreen
import net.marllex.waselak.kds.display.KdsDisplayScreen
import net.marllex.waselak.kds.profile.KdsProfileScreen

private const val KDS_DISPLAY_ROUTE = "kds_display"
private const val KDS_PROFILE_ROUTE = "kds_profile"
private const val KDS_ABOUT_ROUTE = "kds_about"

@Composable
fun KdsNavHost(
    authRepository: AuthRepository,
) {
    val navController = rememberNavController()
    val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = true)

    // Navigate to login if session is invalidated
    LaunchedEffect(Unit) {
        snapshotFlow { isLoggedIn }
            .drop(1)
            .collect { loggedIn ->
                if (!loggedIn) {
                    navController.navigate(AUTH_ROUTE) { popUpTo(0) { inclusive = true } }
                }
            }
    }

    NavHost(
        navController = navController,
        startDestination = AUTH_ROUTE,
        modifier = Modifier.fillMaxSize(),
    ) {
        authScreen(
            onLoginSuccess = {
                navController.navigate(KDS_DISPLAY_ROUTE) {
                    popUpTo(AUTH_ROUTE) { inclusive = true }
                }
            },
            appType = "KDS",
        )

        composable(KDS_DISPLAY_ROUTE) {
            KdsDisplayScreen(
                onLogout = {
                    navController.navigate(AUTH_ROUTE) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(KDS_PROFILE_ROUTE)
                },
            )
        }

        composable(KDS_PROFILE_ROUTE) {
            KdsProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(AUTH_ROUTE) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToAbout = {
                    navController.navigate(KDS_ABOUT_ROUTE)
                },
            )
        }

        composable(KDS_ABOUT_ROUTE) {
            val kdsApi = org.koin.java.KoinJavaComponent.getKoin().get<net.marllex.waselak.core.network.WaselakApiClient>()
            net.marllex.waselak.core.ui.components.AboutScreen(
                appName = "Waselak KDS",
                versionName = net.marllex.waselak.config.BuildConfig.VERSION_NAME,
                versionCode = net.marllex.waselak.config.BuildConfig.VERSION_CODE,
                onCheckUpdate = {
                    val resp = kdsApi.checkForUpdate("kds", net.marllex.waselak.config.BuildConfig.VERSION_NAME, net.marllex.waselak.config.BuildConfig.VERSION_CODE)
                    net.marllex.waselak.core.ui.components.UpdateInfo(hasUpdate = resp.hasUpdate, latestVersion = resp.latestVersion, updateStatus = resp.updateStatus, releaseNotes = resp.releaseNotesAr ?: resp.releaseNotes, downloadUrl = resp.downloadUrl, facebookUrl = resp.facebookUrl, landingPageUrl = resp.landingPageUrl, instagramUrl = resp.instagramUrl, whatsappNumber = resp.whatsappNumber)
                },
                onDownload = { url, onProgress -> kdsApi.downloadFile(url, onProgress) },
            )
        }
    }
}
