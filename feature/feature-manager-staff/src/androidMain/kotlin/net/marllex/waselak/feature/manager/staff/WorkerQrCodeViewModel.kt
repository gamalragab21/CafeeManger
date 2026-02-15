package net.marllex.waselak.feature.manager.staff

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.Worker
import java.io.File
import java.io.FileOutputStream

class WorkerQrCodeViewModel constructor(
    private val workerRepository: WorkerRepository,
    private val vendorRepository: net.marllex.waselak.core.domain.repository.VendorRepository,
    private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val worker: Worker? = null,
        val vendor: net.marllex.waselak.core.model.Vendor? = null,
        val qrCodeBitmap: Bitmap? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val successMessage: String? = null,
        val showRegenerateDialog: Boolean = false,
    )

    private val workerId: String = checkNotNull(savedStateHandle["workerId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadWorkerAndQrCode()
    }

    private fun loadWorkerAndQrCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Load vendor information first
                val vendor = vendorRepository.refreshVendor().getOrNull()
                
                // Get all workers and find the one we need
                workerRepository.refreshWorkers().onSuccess { workers ->
                    val worker = workers.find { it.id == workerId }
                    if (worker == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Worker not found"
                            )
                        }
                        return@launch
                    }

                    // Load QR code image
                    workerRepository.getWorkerQrCode(workerId).onSuccess { bytes ->
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        _uiState.update {
                            it.copy(
                                worker = worker,
                                vendor = vendor,
                                qrCodeBitmap = bitmap,
                                isLoading = false
                            )
                        }
                    }.onFailure { e ->
                        _uiState.update {
                            it.copy(
                                worker = worker,
                                vendor = vendor,
                                isLoading = false,
                                error = "Failed to load QR code: ${e.message}"
                            )
                        }
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load worker: ${e.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun downloadQrCode() {
        viewModelScope.launch {
            val worker = _uiState.value.worker
            val bitmap = _uiState.value.qrCodeBitmap

            if (worker == null || bitmap == null) {
                _uiState.update { it.copy(error = "No QR code to download") }
                return@launch
            }

            try {
                val fileName = "QR_${worker.fullName.replace(" ", "_")}_${worker.workerId}.png"
                saveBitmapToStorage(bitmap, fileName, "QR_Codes")
                _uiState.update {
                    it.copy(successMessage = "QR code saved successfully")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to save QR code: ${e.message}")
                }
            }
        }
    }

    fun downloadFullBadge() {
        viewModelScope.launch {
            val worker = _uiState.value.worker
            val vendor = _uiState.value.vendor
            val qrBitmap = _uiState.value.qrCodeBitmap

            if (worker == null || qrBitmap == null) {
                _uiState.update { it.copy(error = "No badge to download") }
                return@launch
            }

            try {
                // Create a badge bitmap programmatically with vendor info
                val badgeBitmap = createBadgeBitmap(worker, vendor, qrBitmap)

                val fileName = "Badge_${worker.fullName.replace(" ", "_")}_${worker.workerId}.png"
                saveBitmapToStorage(badgeBitmap, fileName, "Employee_Badges")
                _uiState.update {
                    it.copy(successMessage = "Employee badge saved successfully")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to save badge: ${e.message}")
                }
            }
        }
    }

    private fun createBadgeBitmap(
        worker: Worker,
        vendor: net.marllex.waselak.core.model.Vendor?,
        qrBitmap: Bitmap
    ): Bitmap {
        val width = 1400
        val height = 2000

        // Modern Color Palette
        val primaryColor = Color.parseColor("#1A237E") // Deep Navy
        val accentColor = Color.parseColor("#3F51B5")  // Indigo
        val surfaceColor = Color.parseColor("#FFFFFF")
        val textColorPrimary = Color.parseColor("#121212")
        val textColorSecondary = Color.parseColor("#5F6368")
        val badgeBg = Color.parseColor("#F8F9FA")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        // 1. Background & Main Card
        canvas.drawColor(Color.parseColor("#E0E0E0")) // "Table" background
        paint.color = surfaceColor
        val cardRect = RectF(40f, 40f, width - 40f, height - 40f)
        canvas.drawRoundRect(cardRect, 60f, 60f, paint)

        // 2. Header Section (Brand Identity) - Extended for vendor info
        paint.color = primaryColor
        val headerPath = Path().apply {
            addRoundRect(RectF(40f, 40f, width - 40f, 520f),
                floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f), Path.Direction.CW)
        }
        canvas.drawPath(headerPath, paint)

        // Draw vendor logo placeholder (circle at top)
        val logoY = 120f
        val logoRadius = 50f
        paint.color = Color.WHITE
        canvas.drawCircle(width / 2f, logoY, logoRadius, paint)
        
        // Draw logo border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = accentColor
        canvas.drawCircle(width / 2f, logoY, logoRadius, paint)
        
        // Draw business icon inside logo
        paint.style = Paint.Style.FILL
        paint.color = accentColor
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🏪", width / 2f, logoY + 15f, paint)

        // Draw vendor name
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 52f
        val vendorName = vendor?.name ?: "CAFE MANAGER SYSTEM"
        canvas.drawText(vendorName.uppercase(), width / 2f, logoY + 100f, paint)

        // Draw vendor location with icon
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val vendorLocation = vendor?.address ?: "Location"
        // Truncate if too long
        val displayLocation = if (vendorLocation.length > 45) {
            vendorLocation.substring(0, 42) + "..."
        } else {
            vendorLocation
        }
        canvas.drawText("📍 $displayLocation", width / 2f, logoY + 150f, paint)

        // Draw "STAFF PASS" title
        paint.textSize = 80f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("STAFF PASS", width / 2f, logoY + 240f, paint)

        // 3. Profile Image Area
        val centerY = 620f
        val radius = 180f

        // Clean White Border for the Avatar
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(width / 2f, centerY, radius + 10f, paint)

        // Avatar Background
        paint.color = Color.parseColor("#E8EAF6")
        canvas.drawCircle(width / 2f, centerY, radius, paint)

        // Simple modern User Icon (Placeholder)
        paint.color = accentColor
        canvas.drawCircle(width / 2f, centerY - 40f, 60f, paint)
        canvas.drawRoundRect(RectF(width/2f - 100f, centerY + 30f, width/2f + 100f, centerY + 130f), 40f, 40f, paint)

        // 4. Employee Details (The "Who")
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = textColorPrimary
        paint.textSize = 90f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(worker.fullName.uppercase(), width / 2f, 920f, paint)

        paint.color = textColorSecondary
        paint.textSize = 50f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("ID: ${worker.workerId} • ${worker.role}", width / 2f, 1010f, paint)

        // 5. QR Code Section (The "Action")
        // Use a light-colored box to make the QR "pop" for the scanner
        val qrSectionRect = RectF(250f, 1120f, width - 250f, 1820f)
        paint.color = badgeBg
        canvas.drawRoundRect(qrSectionRect, 40f, 40f, paint)

        val qrSize = 550
        val qrLeft = (width - qrSize) / 2f
        val qrTop = 1190f
        val scaledQr = Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, true)
        canvas.drawBitmap(scaledQr, qrLeft, qrTop, paint)

        // Label for the QR
        paint.color = primaryColor
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("SCAN FOR ATTENDANCE", width / 2f, 1780f, paint)

        // 6. Footer (Security/Version)
        paint.color = Color.parseColor("#BDBDBD")
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        canvas.drawText("VER: ${worker.qrCodeVersion} | SECURE ENCRYPTED PASS", width / 2f, 1920f, paint)

        return bitmap
    }
    private fun saveBitmapToStorage(bitmap: Bitmap, fileName: String, folderName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Use MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/CafeeManager/$folderName"
                )
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            } ?: throw Exception("Failed to create file")
        } else {
            // Android 9 and below - Use external storage
            val picturesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(picturesDir, "CafeeManager/$folderName")

            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val file = File(targetDir, fileName)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            // Notify media scanner
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    fun showRegenerateDialog() {
        _uiState.update { it.copy(showRegenerateDialog = true) }
    }

    fun dismissRegenerateDialog() {
        _uiState.update { it.copy(showRegenerateDialog = false) }
    }

    fun regenerateQrCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showRegenerateDialog = false) }

            workerRepository.regenerateWorkerQrCode(workerId).onSuccess {
                _uiState.update { it.copy(successMessage = "QR code regenerated successfully") }
                loadWorkerAndQrCode()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to regenerate QR code: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
