package net.marllex.waselak.feature.manager.chatbot.di

import net.marllex.waselak.feature.manager.chatbot.ChatbotViewModel
import net.marllex.waselak.feature.manager.chatbot.data.ChatbotRepository
import net.marllex.waselak.feature.manager.chatbot.data.ChatbotRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val chatbotModule = module {
    singleOf(::ChatbotRepositoryImpl) bind ChatbotRepository::class
    viewModelOf(::ChatbotViewModel)
}
