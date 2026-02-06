package net.marllex.cafeemanger.manager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import net.marllex.cafeemanger.core.ui.theme.CafeeMangerTheme
import net.marllex.cafeemanger.manager.navigation.ManagerNavHost

@AndroidEntryPoint
class ManagerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CafeeMangerTheme {
                ManagerNavHost()
            }
        }
    }
}
