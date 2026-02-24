package net.marllex.waselak.feature.manager.dashboard

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
    onNavigateToChatbot: () -> Unit = {},
) {
    ModernDashboardScreen(
        viewModel = viewModel,
        onNavigateToChatbot = onNavigateToChatbot,
    )
}
