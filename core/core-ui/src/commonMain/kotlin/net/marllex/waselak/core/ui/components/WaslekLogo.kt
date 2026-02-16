package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.waslek_logo

/**
 * Waslek logo image used as:
 * - Placeholder when vendor/user image is not available
 * - Login screen header
 * - Splash screen branding
 */
@Composable
fun WaslekLogo(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentDescription: String? = "Waslek",
) {
    Image(
        painter = painterResource(Res.drawable.waslek_logo),
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = ContentScale.Crop,
    )
}
