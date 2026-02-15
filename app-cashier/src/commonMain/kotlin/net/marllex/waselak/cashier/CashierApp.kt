package net.marllex.waselak.cashier

import androidx.compose.runtime.Composable
import net.marllex.waselak.cashier.navigation.CashierNavHost
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.ui.theme.WaselakTheme
import org.koin.compose.koinInject

@Composable
fun CashierApp() {
    val authRepository: AuthRepository = koinInject()
    val vendorRepository: VendorRepository = koinInject()
    WaselakTheme {
        CashierNavHost(
            authRepository = authRepository,
            vendorRepository = vendorRepository,
        )
    }
}
