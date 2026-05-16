package net.marllex.waselak.core.ui.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

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

    actual fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", text))
    }

    actual fun shareText(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    actual fun shareHtmlAsImage(htmlContent: String, fileName: String) {
        val activity = context.findActivity() ?: return

        Handler(Looper.getMainLooper()).post {
            try {
                // Must be called BEFORE creating any WebView
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    WebView.enableSlowWholeDocumentDraw()
                }

                val widthPx = 580
                val webView = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(Color.WHITE)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                }

                // Add invisible to activity layout so it can render
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                rootView.addView(webView, FrameLayout.LayoutParams(widthPx, 1).apply {
                    // Position off-screen
                    leftMargin = -widthPx
                    topMargin = -1
                })
                webView.visibility = View.INVISIBLE

                var pageFinishedCalled = false
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (pageFinishedCalled) return
                        pageFinishedCalled = true

                        // Use JS to get the actual content height
                        view?.evaluateJavascript(
                            "(function() { return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight); })()"
                        ) { heightStr ->
                            val contentHeight = heightStr?.toIntOrNull() ?: 800

                            // Resize webview to full content height
                            view.layoutParams = FrameLayout.LayoutParams(widthPx, contentHeight).apply {
                                leftMargin = -widthPx
                                topMargin = -contentHeight
                            }
                            view.requestLayout()

                            // Wait for re-layout and then capture
                            view.postDelayed({
                                try {
                                    view.measure(
                                        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
                                    )
                                    view.layout(0, 0, widthPx, contentHeight)

                                    val bitmap = Bitmap.createBitmap(widthPx, contentHeight, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    canvas.drawColor(Color.WHITE)
                                    view.draw(canvas)

                                    // Cleanup WebView
                                    rootView.removeView(view)
                                    view.destroy()

                                    // Save bitmap to cache
                                    val imagesDir = File(context.cacheDir, "shared_images")
                                    imagesDir.mkdirs()
                                    val imageFile = File(imagesDir, "$fileName.png")
                                    FileOutputStream(imageFile).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    bitmap.recycle()

                                    // Share via FileProvider
                                    val authority = "${context.packageName}.fileprovider"
                                    val imageUri = FileProvider.getUriForFile(context, authority, imageFile)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, imageUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share Receipt").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (e: Exception) {
                                    try { rootView.removeView(view) } catch (_: Exception) {}
                                    try { view.destroy() } catch (_: Exception) {}
                                    android.util.Log.e("ShareImage", "Capture failed", e)
                                }
                            }, 1000)
                        }
                    }
                }
                webView.loadDataWithBaseURL("https://receipt.local", htmlContent, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                android.util.Log.e("ShareImage", "Setup failed", e)
            }
        }
    }

    actual fun saveFileToDownloads(bytes: ByteArray, fileName: String): String {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }

    actual fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) {
        try {
            val cacheDir = File(context.cacheDir, "shared_logs")
            cacheDir.mkdirs()
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }

            val authority = "${context.packageName}.fileprovider"
            val fileUri = FileProvider.getUriForFile(context, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Share Logs").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("PlatformActions", "Failed to share file", e)
        }
    }

    actual val isNfcAvailable: Boolean
        get() {
            // getDefaultAdapter() returns null on devices without NFC
            // hardware. isEnabled() catches "NFC turned off in
            // settings" — the receipt screen should hide the button
            // in that case (no point arming an HCE service the OS will
            // never route APDUs to).
            val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
            return adapter.isEnabled
        }

    actual fun shareUrlViaNfc(url: String): Boolean {
        if (!isNfcAvailable) return false
        if (url.isBlank()) return false
        net.marllex.waselak.core.ui.nfc.NfcReceiptHceService.currentReceiptUrl.set(url)
        android.util.Log.i("PlatformActions", "NFC share armed for URL (len=${url.length})")
        return true
    }

    actual fun stopNfcShare() {
        net.marllex.waselak.core.ui.nfc.NfcReceiptHceService.currentReceiptUrl.set(null)
    }

    actual suspend fun downloadAppUpdate(
        url: String,
        filename: String,
        onProgress: (Float) -> Unit,
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // App-private external dir — no storage permission required
            // on any Android version, and visible to FileProvider via
            // <external-files-path> in file_paths.xml.
            val updatesDir = File(
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                "updates",
            )
            if (!updatesDir.exists() && !updatesDir.mkdirs()) {
                android.util.Log.w("PlatformActions", "Failed to create updates dir")
                return@withContext null
            }
            val outFile = File(updatesDir, filename)
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                android.util.Log.w("PlatformActions", "Update download HTTP $code on $url")
                // Surface the HTTP code so the UI can show "HTTP 404"
                // instead of a generic "Download failed" — much easier
                // to diagnose a stale download URL.
                throw java.io.IOException("HTTP $code on $url")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var read = 0L
            FileOutputStream(outFile).use { out ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
            android.util.Log.i("PlatformActions", "Update downloaded: ${outFile.absolutePath} ($read bytes)")
            outFile.absolutePath
        } catch (e: Throwable) {
            android.util.Log.w("PlatformActions", "Update download failed: ${e.message}")
            null
        }
    }

    actual fun installAppUpdate(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                android.util.Log.w("PlatformActions", "installAppUpdate: file not found at $filePath")
                return false
            }
            // Android 8+ requires the user to grant "Install unknown
            // apps" permission once for our app. If we don't have it,
            // open the system Settings page so they can grant it,
            // then return false — the user will tap Install again
            // after granting.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val packageManager = context.packageManager
                if (!packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    showToast("Please allow this app to install updates, then tap Install again.")
                    return false
                }
            }
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            android.util.Log.e("PlatformActions", "installAppUpdate failed: ${e.message}", e)
            false
        }
    }

    private fun Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    val context = LocalContext.current
    return remember(context) { PlatformActions(context) }
}
