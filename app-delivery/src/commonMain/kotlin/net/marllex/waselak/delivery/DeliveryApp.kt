package net.marllex.waselak.delivery

import androidx.compose.runtime.Composable
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.ui.theme.WaselakTheme
import net.marllex.waselak.delivery.navigation.DeliveryNavHost
import org.koin.compose.koinInject

@Composable
fun DeliveryApp() {
    val authRepository: AuthRepository = koinInject()
    val vendorRepository: VendorRepository = koinInject()
    WaselakTheme {
        DeliveryNavHost(
            authRepository = authRepository,
            vendorRepository = vendorRepository,
        )
    }
}
