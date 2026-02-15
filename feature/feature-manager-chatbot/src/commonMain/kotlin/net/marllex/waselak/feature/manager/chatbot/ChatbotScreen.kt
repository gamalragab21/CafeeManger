package net.marllex.waselak.feature.manager.chatbot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import net.marllex.waselak.feature.manager.chatbot.generated.resources.Res
import net.marllex.waselak.feature.manager.chatbot.generated.resources.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.marllex.waselak.feature.manager.chatbot.components.ChatInputBar
import net.marllex.waselak.feature.manager.chatbot.components.MessageBubble
import net.marllex.waselak.feature.manager.chatbot.components.QuickSuggestionsRow
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    viewModel: ChatbotViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.ai_assistant)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearChat) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.clear_chat)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (uiState.suggestions.isNotEmpty()) {
                    QuickSuggestionsRow(
                        suggestions = uiState.suggestions,
                        onSuggestionClick = viewModel::sendMessage
                    )
                }
                ChatInputBar(
                    onSend = viewModel::sendMessage,
                    enabled = !uiState.isLoading
                )
            }
        }
    ) { padding ->
        if (uiState.messages.isEmpty()) {
            // Welcome screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "👋 " + stringResource(Res.string.welcome_to_ai_assistant),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.ai_assistant_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(Res.string.try_asking),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }
}
