package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun LanguageSelector(modifier: Modifier) {
    // TODO: Implement iOS language switching
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Language settings are managed in iOS Settings",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
