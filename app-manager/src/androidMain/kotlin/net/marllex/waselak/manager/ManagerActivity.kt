package net.marllex.waselak.manager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.ui.theme.WaselakTheme
import net.marllex.waselak.manager.navigation.ManagerNavHost
import org.koin.android.ext.android.inject

class ManagerActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaselakTheme {
                ManagerNavHost(authRepository = authRepository)
            }
        }
    }
}
