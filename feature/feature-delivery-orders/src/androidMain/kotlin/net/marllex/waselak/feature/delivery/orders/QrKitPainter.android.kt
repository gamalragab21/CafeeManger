package net.marllex.waselak.feature.delivery.orders

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import qrgenerator.qrkitpainter.rememberQrKitPainter

@Composable
actual fun rememberQrKitPainterMP(data: String): Painter = rememberQrKitPainter(data = data)
