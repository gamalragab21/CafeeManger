package net.marllex.waselak.admin.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import net.marllex.waselak.admin.network.AdminApiClient
import net.marllex.waselak.admin.session.AdminSessionManager
import net.marllex.waselak.admin.ui.screens.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import waselak.app_admin.generated.resources.*
import waselak.app_admin.generated.resources.Res

private data class NavItem(
    val labelRes: StringResource,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(Res.string.nav_home, Icons.Default.Home),
    NavItem(Res.string.nav_vendors, Icons.Default.Store),
    NavItem(Res.string.nav_plans, Icons.Default.CreditCard),
    NavItem(Res.string.nav_analytics, Icons.Default.BarChart),
    NavItem(Res.string.nav_logs, Icons.Default.BugReport),
    NavItem(Res.string.nav_settings, Icons.Default.Settings),
)

@Composable
fun AdminNavHost() {
    val sessionManager = koinInject<AdminSessionManager>()
    val apiClient = koinInject<AdminApiClient>()
    var startDestination by remember { mutableStateOf<String?>(null) }

    // Determine start destination before building NavHost
    // AdminApiClient auto-restores tokens from session in init,
    // so we just need to validate them with a profile call
    LaunchedEffect(Unit) {
        val savedToken = sessionManager.getToken()
        if (savedToken != null) {
            Logger.i("AdminNavHost") { "Found saved token, validating session..." }
            // getProfile() will auto-refresh if access token is expired (via Ktor Auth plugin)
            val profile = apiClient.getProfile()
            if (profile != null) {
                Logger.i("AdminNavHost") { "Session restored for ${profile.email}" }
                startDestination = "main"
            } else {
                Logger.w("AdminNavHost") { "Session invalid, clearing tokens" }
                sessionManager.clearToken()
                apiClient.clearToken()
                startDestination = "login"
            }
        } else {
            startDestination = "login"
        }
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = resolvedStart,
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            MainScaffold(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun MainScaffold(
    onLogout: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateVendor by remember { mutableStateOf(false) }
    var vendorDetailId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints {
        val useBottomNav = maxWidth < 600.dp

        if (useBottomNav) {
            // Phone layout: Bottom Navigation Bar
            Scaffold(
                bottomBar = {
                    if (!showCreateVendor && vendorDetailId == null) {
                        NavigationBar {
                            navItems.forEachIndexed { index, item ->
                                val label = stringResource(item.labelRes)
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = {
                                        selectedTab = index
                                        showCreateVendor = false
                                        vendorDetailId = null
                                    },
                                    icon = { Icon(item.icon, contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    when {
                        vendorDetailId != null -> {
                            VendorDetailScreen(
                                vendorId = vendorDetailId!!,
                                onBack = { vendorDetailId = null },
                            )
                        }
                        showCreateVendor -> {
                            CreateVendorScreen(
                                onBack = { showCreateVendor = false },
                            )
                        }
                        else -> when (selectedTab) {
                            0 -> HomeScreen()
                            1 -> VendorsScreen(
                                onCreateVendor = { showCreateVendor = true },
                                onVendorClick = { vendorDetailId = it },
                            )
                            2 -> PlansScreen()
                            3 -> AnalyticsScreen()
                            4 -> LogsDashboardScreen()
                            5 -> SettingsScreen(onLogout = onLogout)
                        }
                    }
                }
            }
        } else {
            // Tablet/Desktop layout: Navigation Rail
            Scaffold { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    NavigationRail {
                        navItems.forEachIndexed { index, item ->
                            val label = stringResource(item.labelRes)
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = {
                                    selectedTab = index
                                    showCreateVendor = false
                                    vendorDetailId = null
                                },
                                icon = { Icon(item.icon, contentDescription = label) },
                                label = { Text(label) },
                            )
                        }
                    }

                    // Content area
                    when {
                        vendorDetailId != null -> {
                            VendorDetailScreen(
                                vendorId = vendorDetailId!!,
                                onBack = { vendorDetailId = null },
                            )
                        }
                        showCreateVendor -> {
                            CreateVendorScreen(
                                onBack = { showCreateVendor = false },
                            )
                        }
                        else -> when (selectedTab) {
                            0 -> HomeScreen()
                            1 -> VendorsScreen(
                                onCreateVendor = { showCreateVendor = true },
                                onVendorClick = { vendorDetailId = it },
                            )
                            2 -> PlansScreen()
                            3 -> AnalyticsScreen()
                            4 -> LogsDashboardScreen()
                            5 -> SettingsScreen(onLogout = onLogout)
                        }
                    }
                }
            }
        }
    }
}
