package net.marllex.cafeemanger.feature.manager.chatbot.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import net.marllex.cafeemanger.feature.manager.chatbot.ChatbotScreen

const val CHATBOT_ROUTE = "chatbot"

fun NavController.navigateToChatbot(navOptions: NavOptions? = null) {
    navigate(CHATBOT_ROUTE, navOptions)
}

fun NavGraphBuilder.chatbotScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = CHATBOT_ROUTE) {
        ChatbotScreen(
            onNavigateBack = onNavigateBack
        )
    }
}
