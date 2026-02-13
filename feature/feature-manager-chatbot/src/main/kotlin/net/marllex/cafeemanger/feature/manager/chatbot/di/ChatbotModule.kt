package net.marllex.cafeemanger.feature.manager.chatbot.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.feature.manager.chatbot.data.ChatbotRepository
import net.marllex.cafeemanger.feature.manager.chatbot.data.ChatbotRepositoryImpl
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatbotPreferences

@Module
@InstallIn(SingletonComponent::class)
object ChatbotModule {
    
    @Provides
    @Singleton
    @ChatbotPreferences
    fun provideChatbotSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                "chatbot_encrypted_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences("chatbot_prefs", Context.MODE_PRIVATE)
        }
    }
    
    @Provides
    @Singleton
    fun provideChatbotRepository(
        api: CafeeMangerApi,
        @ChatbotPreferences sharedPreferences: SharedPreferences
    ): ChatbotRepository {
        return ChatbotRepositoryImpl(api, sharedPreferences)
    }
}
