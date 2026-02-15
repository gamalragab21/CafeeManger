package net.marllex.waselak.feature.manager.analytics

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAnalyticsScreen(
    onNavigateToExport: () -> Unit = {},
    viewModel: AnalyticsViewModel = koinViewModel(),
) {
    // Use the simplified, user-friendly analytics screen
    SimplifiedAnalyticsScreen(
        viewModel = viewModel,
        onNavigateToExport = onNavigateToExport
    )
}
