package net.marllex.cafeemanger.core.auth.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.marllex.cafeemanger.core.auth.AuthInterceptor
import net.marllex.cafeemanger.core.auth.AuthRepositoryImpl
import net.marllex.cafeemanger.core.auth.TokenManager
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import okhttp3.Interceptor
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthProvidesModule {

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "cafeemanger_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @IntoSet
    @Singleton
    fun provideAuthInterceptorIntoSet(
        tokenManager: TokenManager,
        apiProvider: Provider<CafeeMangerApi>
    ): Interceptor {
        val interceptor = AuthInterceptor(tokenManager)
        interceptor.apiProvider = { apiProvider.get() }
        return interceptor
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindsModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
