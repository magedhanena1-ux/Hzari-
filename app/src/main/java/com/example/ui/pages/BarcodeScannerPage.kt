package com.example.ui.pages

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.api.ApiClient
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerPage(
    onBarcodeScanned: (String) -> Unit,
    onNavigateToManualAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val scope = rememberCoroutineScope()

    var isScanningActive by remember { mutableStateOf(false) }
    var scannedWarningItem by remember { mutableStateOf<com.example.model.PlatformItem?>(null) }
    var scannedWarningInfo by remember { mutableStateOf<com.example.api.BarcodeLookup.ExpiryWarningMessage?>(null) }
    var isCheckingExpiryAfterScan by remember { mutableStateOf(false) }

    if (scannedWarningItem != null && scannedWarningInfo != null) {
        val warning = scannedWarningInfo!!
        val item = scannedWarningItem!!
        val warningColor = Color(android.graphics.Color.parseColor("#" + warning.colorHex))

        AlertDialog(
            onDismissRequest = {
                scannedWarningItem = null
                scannedWarningInfo = null
                isScanningActive = true
            },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = "تحذير انتهاء الصلاحية",
                        tint = warningColor
                    )
                    Text(
                        text = "صنف مسجل مسبقاً وقريب الانتهاء!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "الرمز المفحوص: ${item.barcode ?: ""}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "اسم الصنف: ${item.itemName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "تاريخ الانتهاء المسجل: ${item.expiryDate ?: "غير محدد"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(warningColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = warning.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = warningColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = warning.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = warningColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val barcodeVal = item.barcode ?: ""
                        scannedWarningItem = null
                        scannedWarningInfo = null
                        onBarcodeScanned(barcodeVal)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("إجراء إضافة أو تعديل")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        scannedWarningItem = null
                        scannedWarningInfo = null
                        isScanningActive = true
                    }
                ) {
                    Text("إلغاء والمسح مجدداً")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("scanner_root")
            .background(Color(0xFF0F172A)) // Slate-900 visual vibe
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "قارئ الباركود والرموز",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = "وجه الكاميرا نحو باركود الصنف (EAN-13, UPC-A, Code 128) ليتم جلبه وربطه تلقائياً.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Box holding camera or placeholders
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            if (isScanningActive && permissionState.status.isGranted) {
                CameraLiveView(
                    onBarcodeFound = { barcodeValue ->
                        isScanningActive = false
                        playFeedbackAlert(context)
                        
                        // Check if this item already exists and is expiring soon
                        isCheckingExpiryAfterScan = true
                        scope.launch {
                            try {
                                val matchingItems = ApiClient.fetchItems(context, barcodeValue)
                                val exactMatch = matchingItems.find { it.barcode == barcodeValue }
                                val warning = if (exactMatch != null && !exactMatch.expiryDate.isNullOrEmpty()) {
                                    com.example.api.BarcodeLookup.checkScannedProductExpiry(exactMatch.expiryDate)
                                } else {
                                    null
                                }
                                
                                if (exactMatch != null && warning != null) {
                                    scannedWarningItem = exactMatch
                                    scannedWarningInfo = warning
                                } else {
                                    onBarcodeScanned(barcodeValue)
                                }
                            } catch (e: Exception) {
                                onBarcodeScanned(barcodeValue)
                            } finally {
                                isCheckingExpiryAfterScan = false
                            }
                        }
                    }
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "",
                        modifier = Modifier.size(56.dp),
                        tint = Color(0xFF64748B)
                    )
                    Text(
                        text = if (!permissionState.status.isGranted) "مطلوب صلاحية الكاميرا للمسح" else "الكاميرا متوقفة",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!permissionState.status.isGranted) {
                        Button(
                            onClick = { permissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("منح الصلاحية")
                        }
                    }
                }
            }

            if (isCheckingExpiryAfterScan) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "جاري التحقق من الصلاحية...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isScanningActive) {
                Button(
                    onClick = {
                        if (permissionState.status.isGranted) {
                            isScanningActive = true
                        } else {
                            permissionState.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("start_scan_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بدء المسح", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { isScanningActive = false },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("stop_scan_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إيقاف مسح", fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onNavigateToManualAdd,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("manual_input_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Keyboard, contentDescription = "")
                Spacer(modifier = Modifier.width(8.dp))
                Text("إدخال يدوي", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun CameraLiveView(
    onBarcodeFound: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Configure ML Kit scanner to scan all codes (EAN-13, QR, UPC, etc)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_QR_CODE
                )
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy, scanner, onBarcodeFound)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraLiveView", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeFound: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrEmpty()) {
                        onBarcodeFound(rawValue)
                        break
                    }
                }
            }
            .addOnFailureListener {
                // Ignore scanning failures
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

// Play notification sound & vibration feedback
private fun playFeedbackAlert(context: Context) {
    // 1. Play feedback sound
    val prefs = context.getSharedPreferences("hathari_prefs", Context.MODE_PRIVATE)
    val isSoundEnabled = prefs.getBoolean("alert_sound_enabled", true)
    if (isSoundEnabled) {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(context, notificationUri)
            r.play()
        } catch (e: Exception) {
            // Fallback play beep
            try {
                val mp = MediaPlayer.create(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                mp.start()
            } catch (e2: Exception) {
                // Ignore audio errors
            }
        }
    }

    // 2. Play tactile vibration
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(150)
    } catch (e: Exception) {
        // Ignore vibration permission or API failures
    }
}
