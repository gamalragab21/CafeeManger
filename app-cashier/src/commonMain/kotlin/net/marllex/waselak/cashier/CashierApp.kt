package net.marllex.waselak.cashier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.marllex.waselak.core.ui.theme.WaselakTheme

@Composable
fun CashierApp() {
    WaselakTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Navigation will be moved here from androidMain after resource migration (Phase 5)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Waselak Cashier", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}
