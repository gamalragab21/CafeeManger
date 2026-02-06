package net.marllex.cafeemanger.cashier

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import net.marllex.cafeemanger.cashier.navigation.CashierNavHost
import net.marllex.cafeemanger.core.ui.theme.CafeeMangerTheme

@AndroidEntryPoint
class CashierActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CafeeMangerTheme {
                CashierNavHost()
            }
        }
    }
}
