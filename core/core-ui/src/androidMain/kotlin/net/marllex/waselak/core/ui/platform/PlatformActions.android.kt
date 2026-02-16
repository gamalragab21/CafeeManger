package net.marllex.waselak.core.ui.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
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
        val activity = when (context) {
            is android.app.Activity -> context
            is android.content.ContextWrapper -> {
                var ctx: Context? = context
                while (ctx is android.content.ContextWrapper) {
                    if (ctx is android.app.Activity) break
                    ctx = ctx.baseContext
                }
                ctx as? android.app.Activity
            }
            else -> null
        } ?: return

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val webView = WebView(activity)
                webView.settings.javaScriptEnabled = true
                webView.setBackgroundColor(android.graphics.Color.WHITE)

                // Enable off-screen rendering
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    WebView.enableSlowWholeDocumentDraw()
                }

                // Add WebView to the activity's root layout (invisible, off-screen)
                val rootView = activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
                val params = android.widget.FrameLayout.LayoutParams(
                    580,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
                webView.visibility = android.view.View.INVISIBLE
                webView.layoutParams = params
                rootView.addView(webView)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.postDelayed({
                            try {
                                view.measure(
                                    android.view.View.MeasureSpec.makeMeasureSpec(580, android.view.View.MeasureSpec.EXACTLY),
                                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                                )
                                view.layout(0, 0, 580, view.measuredHeight)

                                val bitmapHeight = view.measuredHeight.coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(580, bitmapHeight, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                view.draw(canvas)

                                // Remove WebView from parent
                                rootView.removeView(view)
                                view.destroy()

                                // Save to cache dir
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
                                rootView.removeView(view)
                                view?.destroy()
                                println("Share as image failed: ${e.message}")
                                e.printStackTrace()
                            }
                        }, 800)
                    }
                }
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                println("Share as image setup failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    val context = LocalContext.current
    return remember(context) { PlatformActions(context) }
}
