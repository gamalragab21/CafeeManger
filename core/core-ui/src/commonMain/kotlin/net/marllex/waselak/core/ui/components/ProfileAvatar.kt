package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import waselak.core.core_ui.generated.resources.Res
import waselak.core.core_ui.generated.resources.waslek_logo

@Composable
fun ProfileAvatar(
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = null,
) {
    if (!photoUrl.isNullOrBlank()) {
        val logoPainter = painterResource(Res.drawable.waslek_logo)
        AsyncImage(
            model = photoUrl,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = logoPainter,
            error = logoPainter,
            fallback = logoPainter,
        )
    } else {
        Image(
            painter = painterResource(Res.drawable.waslek_logo),
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}
