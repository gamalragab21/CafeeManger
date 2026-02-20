package net.marllex.waselak.feature.cashier.attendance.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrScannerDialog(
    title: String,
    onQrCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var hasScanned by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Camera Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cameraError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            )
                            Text(
                                text = cameraError!!,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        UIKitViewController(
                            factory = {
                                QrScannerViewController(
                                    onScanned = { qrData ->
                                        if (!hasScanned) {
                                            hasScanned = true
                                            onQrCodeScanned(qrData)
                                        }
                                    },
                                    onError = { error ->
                                        cameraError = error
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Instructions
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Position the QR code within the camera frame",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class QrScannerViewController(
    private val onScanned: (String) -> Unit,
    private val onError: (String) -> Unit,
) : UIViewController(nibName = null, bundle = null) {

    private var captureSession: AVCaptureSession? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        setupCamera()
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        captureSession?.stopRunning()
    }

    private fun setupCamera() {
        val session = AVCaptureSession()
        session.sessionPreset = AVCaptureSessionPresetHigh

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            onError("Camera not available")
            return
        }

        val input = try {
            AVCaptureDeviceInput.deviceInputWithDevice(device, null)
        } catch (e: Exception) {
            onError("Could not access camera: ${e.message}")
            return
        }

        if (input == null) {
            onError("Could not create camera input")
            return
        }

        if (session.canAddInput(input)) {
            session.addInput(input)
        } else {
            onError("Could not add camera input")
            return
        }

        val output = AVCaptureMetadataOutput()
        if (session.canAddOutput(output)) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(
                objectsDelegate = MetadataOutputDelegate(onScanned),
                queue = dispatch_get_main_queue(),
            )
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        } else {
            onError("Could not add metadata output")
            return
        }

        val previewLayer = AVCaptureVideoPreviewLayer(session = session)
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        previewLayer.frame = view.layer.bounds
        view.layer.addSublayer(previewLayer)

        captureSession = session
        session.startRunning()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class MetadataOutputDelegate(
    private val onScanned: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    private var hasScanned = false

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        if (hasScanned) return

        for (metadata in didOutputMetadataObjects) {
            val readable = metadata as? AVMetadataMachineReadableCodeObject ?: continue
            if (readable.type == AVMetadataObjectTypeQRCode) {
                val value = readable.stringValue ?: continue
                hasScanned = true
                onScanned(value)
                return
            }
        }
    }
}
