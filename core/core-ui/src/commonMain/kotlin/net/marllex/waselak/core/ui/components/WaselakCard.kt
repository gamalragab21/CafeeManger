package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.marllex.waselak.core.ui.theme.WaselakDimens
import net.marllex.waselak.core.ui.theme.WaselakShapes

/**
 * Standard card for the Waselak design system.
 *
 * Uses soft rounded corners (16 dp) and a subtle 1 dp elevation
 * to achieve the "Clean & Minimal" look across all screens.
 */
@Composable
fun WaselakCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = WaselakShapes.medium,
            colors = colors,
            elevation = CardDefaults.cardElevation(
                defaultElevation = WaselakDimens.CardElevation,
            ),
            content = content,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = WaselakShapes.medium,
            colors = colors,
            elevation = CardDefaults.cardElevation(
                defaultElevation = WaselakDimens.CardElevation,
            ),
            content = content,
        )
    }
}
