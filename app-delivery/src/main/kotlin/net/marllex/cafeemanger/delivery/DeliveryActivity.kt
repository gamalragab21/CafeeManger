package net.marllex.cafeemanger.delivery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.ui.theme.CafeeMangerTheme
import net.marllex.cafeemanger.delivery.navigation.DeliveryNavHost
import javax.inject.Inject

@AndroidEntryPoint
class DeliveryActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CafeeMangerTheme {
                DeliveryNavHost(authRepository = authRepository)
            }
        }
    }
}
