package net.marllex.waselak.cashier

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import net.marllex.waselak.cashier.navigation.CashierNavHost
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.ui.theme.WaselakTheme
import org.koin.android.ext.android.inject

class CashierActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()
    private val vendorRepository: VendorRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaselakTheme {
                CashierNavHost(authRepository = authRepository, vendorRepository = vendorRepository)
            }
        }
    }
}
