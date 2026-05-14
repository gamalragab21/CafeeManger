package net.marllex.waselak.core.ui.thermal

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

/**
 * Desktop-side ESC/POS raster builder. Mirrors the Android implementation
 * in [net.marllex.waselak.core.ui.thermal.toEscPosRasterBytes] so both
 * platforms talk the same byte protocol to thermal printers — the only
 * difference is the source image type (BufferedImage on desktop,
 * android.graphics.Bitmap on Android).
 *
 * Output layout:
 *   ESC @                — initialise / reset printer state
 *   ESC t 32             — select code page WPC1256 (Arabic)
 *   GS v 0  [bands]      — one or more raster bitmap blocks
 *   LF × 4               — feed past the cutter
 *   GS V B 50            — feed 50 dots then partial cut
 *
 * Some XP-class printer firmwares only honour up to ~256 rows per
 * `GS v 0` call before resetting the print buffer; we therefore split
 * the bitmap into [BAND_HEIGHT]-row bands and emit one `GS v 0` per
 * band. The print head receives a continuous strip — band boundaries
 * are invisible.
 */
fun BufferedImage.toEscPosReceipt(threshold: Int = 128): ByteArray {
    val out = ByteArrayOutputStream()
    // Init + Arabic code page so any future text-only commands render.
    out.write(byteArrayOf(0x1B, 0x40))                  // ESC @
    out.write(byteArrayOf(0x1B, 0x74, 0x20))            // ESC t 32 (WPC1256)

    out.write(toRasterBlocks(threshold))

    // Feed past cutter and partial-cut.
    out.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A))
    out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x32))      // GS V B 50
    return out.toByteArray()
}

/**
 * Same as [toEscPosReceipt] but does not append init / feed / cut. Useful
 * for callers that want to handle those bytes themselves or chain
 * multiple raster blocks in a single job.
 */
fun BufferedImage.toRasterBlocks(threshold: Int = 128): ByteArray {
    val widthBytes = (width + 7) / 8
    val out = ByteArrayOutputStream()

    var rowsRemaining = height
    var rowStart = 0
    while (rowsRemaining > 0) {
        val bandHeight = minOf(rowsRemaining, BAND_HEIGHT)
        val xL = widthBytes and 0xFF
        val xH = (widthBytes shr 8) and 0xFF
        val yL = bandHeight and 0xFF
        val yH = (bandHeight shr 8) and 0xFF
        // GS v 0 m xL xH yL yH : raster bit image, m=0 = normal scale.
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL.toByte(), xH.toByte(), yL.toByte(), yH.toByte()))

        for (row in rowStart until rowStart + bandHeight) {
            for (byteCol in 0 until widthBytes) {
                var b = 0
                for (bit in 0 until 8) {
                    val col = byteCol * 8 + bit
                    if (col < width) {
                        val argb = getRGB(col, row)
                        // ARGB → grayscale. Treat fully-transparent pixels
                        // as paper (white) so PNG logos with alpha cutouts
                        // don't show a black box around them.
                        val alpha = (argb shr 24) and 0xFF
                        val r = (argb shr 16) and 0xFF
                        val g = (argb shr 8) and 0xFF
                        val bb = argb and 0xFF
                        val gray = if (alpha < 128) 255 else (r + g + bb) / 3
                        if (gray < threshold) {
                            b = b or (1 shl (7 - bit))
                        }
                    }
                }
                out.write(b)
            }
        }

        rowStart += bandHeight
        rowsRemaining -= bandHeight
    }
    return out.toByteArray()
}

private const val BAND_HEIGHT = 96
