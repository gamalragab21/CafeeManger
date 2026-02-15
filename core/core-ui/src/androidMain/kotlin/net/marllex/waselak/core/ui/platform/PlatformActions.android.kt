package net.marllex.waselak.core.ui.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class PlatformActions(private val context: Context) {
    actual fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    actual fun openMap(address: String?, lat: Double?, lng: Double?) {
        val uri = when {
            !address.isNullOrBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            lat != null && lng != null -> Uri.parse("geo:$lat,$lng")
            else -> return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    actual fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    val context = LocalContext.current
    return remember(context) { PlatformActions(context) }
}
