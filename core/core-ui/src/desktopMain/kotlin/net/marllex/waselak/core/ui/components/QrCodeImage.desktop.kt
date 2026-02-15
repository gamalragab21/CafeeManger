package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage

@Composable
actual fun QrCodeImage(
    content: String,
    modifier: Modifier,
) {
    val imageBitmap = remember(content) {
        val size = 512
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bufferedImage.setRGB(x, y, if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb())
            }
        }
        bufferedImage.toComposeImageBitmap()
    }
    Image(
        bitmap = imageBitmap,
        contentDescription = "QR Code",
        modifier = modifier,
    )
}
