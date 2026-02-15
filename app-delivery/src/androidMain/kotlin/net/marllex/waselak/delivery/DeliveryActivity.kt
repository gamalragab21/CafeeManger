package net.marllex.waselak.delivery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.ui.theme.WaselakTheme
import net.marllex.waselak.delivery.navigation.DeliveryNavHost
import org.koin.android.ext.android.inject

class DeliveryActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()
    private val vendorRepository: VendorRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaselakTheme {
                DeliveryNavHost(
                    authRepository = authRepository,
                    vendorRepository = vendorRepository,
                )
            }
        }
    }
}
