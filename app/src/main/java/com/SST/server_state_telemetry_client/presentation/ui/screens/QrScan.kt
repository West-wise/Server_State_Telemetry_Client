package com.SST.server_state_telemetry_client.presentation.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.SST.server_state_telemetry_client.ui.theme.Primary
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private val DarkBg = Color(0xFF0B0F14)

@Composable
fun QrScan(onQrScanned: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var scanLocked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) errorText = "카메라 권한이 필요합니다."
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        hasCameraPermission = granted
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "카메라 권한이 없어 스캔할 수 없습니다",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("권한 요청")
                }
            }
        } else {
            // Camera preview fills the whole screen
            CameraPreviewWithQrAnalyzer(
                modifier = Modifier.fillMaxSize(),
                onError = { msg -> errorText = msg },
                onQrText = { qrText ->
                    if (scanLocked) return@CameraPreviewWithQrAnalyzer
                    scanLocked = true
                    onQrScanned(qrText)
                },
                lifecycleOwner = lifecycleOwner
            )

            // Dim overlay with viewfinder hole (layered boxes)
            Box(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Close button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        "QR 스캔",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    // Flash placeholder
                    Box(Modifier.size(36.dp))
                }

                // Viewfinder
                val viewfinderSize = 260.dp
                val transition = rememberInfiniteTransition(label = "scan_line")
                val scanLineY by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scan_y"
                )

                Box(
                    modifier = Modifier
                        .size(viewfinderSize)
                        .align(BiasAlignment(0f, -0.08f)),
                ) {
                    // Corner brackets
                    val bracketSize = 42.dp
                    val bracketWidth = 4.dp

                    // Top-left
                    Box(
                        Modifier
                            .size(bracketSize)
                            .align(Alignment.TopStart)
                            .border(
                                width = bracketWidth,
                                color = Primary,
                                shape = RoundedCornerShape(topStart = 28.dp)
                            )
                    )
                    // Top-right
                    Box(
                        Modifier
                            .size(bracketSize)
                            .align(Alignment.TopEnd)
                            .border(
                                width = bracketWidth,
                                color = Primary,
                                shape = RoundedCornerShape(topEnd = 28.dp)
                            )
                    )
                    // Bottom-left
                    Box(
                        Modifier
                            .size(bracketSize)
                            .align(Alignment.BottomStart)
                            .border(
                                width = bracketWidth,
                                color = Primary,
                                shape = RoundedCornerShape(bottomStart = 28.dp)
                            )
                    )
                    // Bottom-right
                    Box(
                        Modifier
                            .size(bracketSize)
                            .align(Alignment.BottomEnd)
                            .border(
                                width = bracketWidth,
                                color = Primary,
                                shape = RoundedCornerShape(bottomEnd = 28.dp)
                            )
                    )

                    // Scan line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(horizontal = 8.dp)
                            .offset(y = (viewfinderSize * scanLineY.coerceIn(0f, 1f)) - viewfinderSize / 2)
                            .align(Alignment.Center)
                            .background(Primary)
                    )
                }

                // Bottom info card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                        .background(
                            DarkBg.copy(alpha = 0.6f),
                            RoundedCornerShape(18.dp)
                        )
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Column {
                        Text(
                            "서버의 QR 코드를 비춰주세요",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "데몬에서 --show-qr 명령으로 생성한\nQR 코드를 카메라에 비춰주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    Color.White.copy(alpha = 0.10f),
                                    CircleShape
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "직접 입력하기",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            if (errorText != null) {
                Text(
                    text = errorText ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp, start = 16.dp, end = 16.dp),
                )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraPreviewWithQrAnalyzer(
    modifier: Modifier,
    onError: (String) -> Unit,
    onQrText: (String) -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val context = LocalContext.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        try {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage, imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    val raw = barcodes.firstOrNull()?.rawValue
                                    if (!raw.isNullOrBlank() && raw.length <= 2048) {
                                        onQrText(raw)
                                    } else if (raw != null && raw.length > 2048) {
                                        onError("QR 데이터가 너무 깁니다.")
                                    }
                                }
                                .addOnFailureListener { }
                                .addOnCompleteListener { imageProxy.close() }
                        } catch (_: Exception) {
                            imageProxy.close()
                        }
                    }

                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                } catch (e: Exception) {
                    onError("카메라 초기화 실패: ${e.message ?: "unknown"}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            try { scanner.close() } catch (_: Exception) {}
            analysisExecutor.shutdown()
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "QR Scanner Overlay")
@Composable
private fun QrScanOverlayPreview() {
    Server_State_Telemetry_ClientTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
        ) {
            // Simulate the overlay without camera
            Box(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    Text("QR 스캔", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.size(36.dp))
                }

                // Viewfinder
                val viewfinderSize = 260.dp
                Box(
                    modifier = Modifier
                        .size(viewfinderSize)
                        .align(BiasAlignment(0f, -0.08f)),
                ) {
                    val bracketSize = 42.dp
                    val bracketWidth = 4.dp
                    Box(Modifier.size(bracketSize).align(Alignment.TopStart).border(bracketWidth, Primary, RoundedCornerShape(topStart = 28.dp)))
                    Box(Modifier.size(bracketSize).align(Alignment.TopEnd).border(bracketWidth, Primary, RoundedCornerShape(topEnd = 28.dp)))
                    Box(Modifier.size(bracketSize).align(Alignment.BottomStart).border(bracketWidth, Primary, RoundedCornerShape(bottomStart = 28.dp)))
                    Box(Modifier.size(bracketSize).align(Alignment.BottomEnd).border(bracketWidth, Primary, RoundedCornerShape(bottomEnd = 28.dp)))
                    // Static scan line for preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(horizontal = 8.dp)
                            .align(Alignment.Center)
                            .background(Primary)
                    )
                }

                // Bottom info card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                        .background(DarkBg.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Column {
                        Text("서버의 QR 코드를 비춰주세요", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text("데몬에서 --show-qr 명령으로 생성한\nQR 코드를 카메라에 비춰주세요.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.65f))
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.10f), CircleShape)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("직접 입력하기", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

object QrServerParser {
    data class Parsed(val name: String?, val ip: String?, val port: Int?, val pubKey: String?)

    fun parse(qrText: String?): Parsed? {
        if (qrText.isNullOrBlank()) return null
        if (qrText.length > 2048) return null

        return try {
            val uri = Uri.parse(qrText)
            if (uri.scheme != "sst") return null
            if (uri.host != "server") return null

            val name = uri.getQueryParameter("name")?.take(64)
            val ip = uri.getQueryParameter("ip")?.take(64)
            val portStr = uri.getQueryParameter("port")?.take(6)
            // Gen3: pub_key = 서버 X25519 정적 공개키 (64자리 hex = 32바이트)
            val pubKey = uri.getQueryParameter("pub_key")?.take(128)
            val port = portStr?.toIntOrNull()?.takeIf { it in 1..65535 }

            Parsed(name = name, ip = ip, port = port, pubKey = pubKey)
        } catch (_: Exception) {
            null
        }
    }
}

object FormValidator {
    fun validate(form: ServerRegisterFormState?): String? {
        if (form == null) return "내부 오류: 폼이 비어있습니다."

        val name = form.name.trim()
        val ip = form.ip.trim()
        val port = form.port.trim()
        val pubKey = form.pubKey.trim()

        if (name.isBlank()) return "서버 별칭을 입력하세요."
        if (name.length > 64) return "서버 별칭이 너무 깁니다."
        if (ip.isBlank()) return "IP 주소를 입력하세요."
        if (ip.length > 64) return "IP 주소가 너무 깁니다."

        val p = port.toIntOrNull() ?: return "포트는 숫자여야 합니다."
        if (p !in 1..65535) return "포트 범위가 올바르지 않습니다."

        // X25519 공개키: 정확히 64자리 hex (32바이트)
        if (pubKey.isBlank()) return "서버 공개키를 입력하세요."
        if (pubKey.length != 64) return "공개키는 정확히 64자리 hex여야 합니다."
        if (!pubKey.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return "공개키는 hex(0-9, a-f)만 사용해야 합니다."
        }

        return null
    }
}
