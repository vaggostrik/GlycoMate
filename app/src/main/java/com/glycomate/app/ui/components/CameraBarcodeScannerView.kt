package com.glycomate.app.ui.components

import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private const val TAG = "BarcodeScanner"

@Composable
fun CameraBarcodeScannerView(
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State to prevent processing multiple barcodes at once
    var scanning by remember { mutableStateOf(true) }

    // Background executor for image analysis
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                // Must use main executor for cameraProviderFuture.addListener
                val mainExecutor = ContextCompat.getMainExecutor(ctx)

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = try {
                        cameraProviderFuture.get()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get CameraProvider", e)
                        return@addListener
                    }

                    // Preview use case
                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    // ML Kit barcode scanner — optimised for product barcodes
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E,
                            Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_CODE_39,
                            Barcode.FORMAT_CODE_93
                        ).build()
                    val barcodeScanner = BarcodeScanning.getClient(options)

                    // Image analysis use case
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        if (!scanning) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (!scanning) return@addOnSuccessListener
                                val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                if (barcode != null) {
                                    scanning = false
                                    Log.d(TAG, "Detected: ${barcode.rawValue}")
                                    // Call back on main thread
                                    mainExecutor.execute {
                                        onBarcodeDetected(barcode.rawValue!!)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Barcode scan failed", e)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        Log.d(TAG, "Camera bound to lifecycle")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind camera", e)
                    }

                }, mainExecutor)  // ← CRITICAL: main executor, not background

                previewView
            }
        )

        // Scanning frame overlay
        ScanningOverlay()
    }

    DisposableEffect(Unit) {
        onDispose {
            scanning = false
            analysisExecutor.shutdown()
        }
    }
}

@Composable
private fun ScanningOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent top + bottom masks
        Surface(
            modifier = Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter),
            color    = Color.Black.copy(alpha = 0.55f)
        ) {}
        Surface(
            modifier = Modifier.fillMaxWidth().height(160.dp).align(Alignment.BottomCenter),
            color    = Color.Black.copy(alpha = 0.55f)
        ) {}

        // Scan window border corners
        Box(
            modifier = Modifier.size(280.dp, 150.dp).align(Alignment.Center)
        ) {
            // Top-left
            Surface(modifier = Modifier.size(28.dp, 4.dp).align(Alignment.TopStart),
                color = Color.White) {}
            Surface(modifier = Modifier.size(4.dp, 28.dp).align(Alignment.TopStart),
                color = Color.White) {}
            // Top-right
            Surface(modifier = Modifier.size(28.dp, 4.dp).align(Alignment.TopEnd),
                color = Color.White) {}
            Surface(modifier = Modifier.size(4.dp, 28.dp).align(Alignment.TopEnd),
                color = Color.White) {}
            // Bottom-left
            Surface(modifier = Modifier.size(28.dp, 4.dp).align(Alignment.BottomStart),
                color = Color.White) {}
            Surface(modifier = Modifier.size(4.dp, 28.dp).align(Alignment.BottomStart),
                color = Color.White) {}
            // Bottom-right
            Surface(modifier = Modifier.size(28.dp, 4.dp).align(Alignment.BottomEnd),
                color = Color.White) {}
            Surface(modifier = Modifier.size(4.dp, 28.dp).align(Alignment.BottomEnd),
                color = Color.White) {}
        }

        // Instruction label
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            shape    = RoundedCornerShape(20.dp),
            color    = Color.Black.copy(alpha = 0.65f)
        ) {
            Text(
                text       = "Στόχευσε το barcode του προϊόντος",
                color      = Color.White,
                fontSize   = 14.sp,
                fontWeight = FontWeight.W500,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
