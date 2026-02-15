package net.marllex.waselak.feature.manager.chatbot.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.marllex.waselak.feature.manager.chatbot.model.QuickSuggestion

@Composable
fun QuickSuggestionsRow(
    suggestions: List<QuickSuggestion>,
    onSuggestionClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionChip(
                text = suggestion.text,
                icon = suggestion.icon,
                onClick = { onSuggestionClick(suggestion.text) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionChip(
    text: String,
    icon: String? = null,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        border = FilterChipDefaults.filterChipBorder(
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            selected = false,
            enabled = true
        )
    )
}
