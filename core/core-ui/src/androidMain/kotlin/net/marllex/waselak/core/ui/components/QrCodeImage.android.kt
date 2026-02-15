package net.marllex.waselak.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
actual fun QrCodeImage(
    content: String,
    modifier: Modifier,
) {
    val bitmap = remember(content) { generateQrBitmap(content, 512) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR Code",
        modifier = modifier,
    )
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { idx ->
        if (bitMatrix[idx % size, idx / size]) Color.Black.toArgb() else Color.White.toArgb()
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}
