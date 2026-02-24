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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun QrScan(onQrScanned: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var scanLocked by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted
            if (!granted) errorText = "카메라 권한이 필요합니다."
        }

    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED

        hasCameraPermission = granted

        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA) // TODO(modified): 자동 권한 요청 복구
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) { // TODO(modified): fillMaxSize로 변경(레이아웃 안정)
        Column(modifier = Modifier.fillMaxSize()) { // TODO(modified)
            if (errorText != null) {
                Text(
                    text = errorText ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // TODO(modified): return@Surface 제거 -> if/else로 컴포저블 트리 유지
            if (!hasCameraPermission) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("카메라 권한이 없어 스캔할 수 없습니다")
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("권한 요청")
                        }
                    }
                }
            } else {
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

    // TODO(modified): 단일 스레드 분석 실행기 (성능/순서 안정)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // TODO(modified): MLKit 스캐너는 remember로 재사용(매 프레임 생성 금지)
    val scanner = remember {
        val options =
                BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        BarcodeScanning.getClient(options)
    }

    // TODO(modified): AndroidView로 PreviewView 띄우기
    AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener(
                        {
                            try {
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build()
                                preview.setSurfaceProvider(previewView.surfaceProvider)

                                val analysis =
                                        ImageAnalysis.Builder()
                                                .setBackpressureStrategy(
                                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                                ) // TODO(modified): 성능 우선
                                                .build()

                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }

                                    try {
                                        val inputImage =
                                                InputImage.fromMediaImage(
                                                        mediaImage,
                                                        imageProxy.imageInfo.rotationDegrees
                                                )

                                        scanner.process(inputImage)
                                                .addOnSuccessListener { barcodes ->
                                                    // TODO(modified): QR 텍스트 안전 처리
                                                    val raw = barcodes.firstOrNull()?.rawValue
                                                    if (!raw.isNullOrBlank()) {
                                                        // 너무 긴 QR은 방어(악성 입력)
                                                        if (raw.length <= 2048) { // TODO(modified)
                                                            onQrText(raw)
                                                        } else {
                                                            onError("QR 데이터가 너무 깁니다.")
                                                        }
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    // 실패는 흔함(흔들림/초점). 과도한 에러 표시는 UX 악화라 최소화.
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy
                                                            .close() // TODO(modified): 반드시 close (안
                                                    // 하면 프리뷰 멈춤)
                                                }
                                    } catch (e: Exception) {
                                        imageProxy.close()
                                    }
                                }

                                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        analysis
                                )
                            } catch (e: Exception) {
                                onError("카메라 초기화 실패: ${e.message ?: "unknown"}")
                            }
                        },
                        ContextCompat.getMainExecutor(ctx)
                )

                previewView
            }
    )

    // TODO(modified): 리소스 정리
    DisposableEffect(Unit) {
        onDispose {
            try {
                scanner.close()
            } catch (_: Exception) {}
            analysisExecutor.shutdown()
        }
    }
}

object QrServerParser {

    data class Parsed(val name: String?, val ip: String?, val port: Int?, val hmacKey: String?)

    fun parse(qrText: String?): Parsed? {
        if (qrText.isNullOrBlank()) return null
        if (qrText.length > 2048) return null // TODO(modified): 길이 제한

        return try {
            val uri = Uri.parse(qrText)

            // TODO(modified): 스킴/호스트 고정 (임의 URL 차단)
            if (uri.scheme != "sst") return null
            if (uri.host != "server") return null

            val name = uri.getQueryParameter("name")?.take(64)
            val ip = uri.getQueryParameter("ip")?.take(64)
            val portStr = uri.getQueryParameter("port")?.take(6)
            val hmac = uri.getQueryParameter("hmac")?.take(256)

            val port = portStr?.toIntOrNull()?.takeIf { it in 1..65535 }

            Parsed(name = name, ip = ip, port = port, hmacKey = hmac)
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
        val hmac = form.hmacKey.trim()

        if (name.isBlank()) return "서버 별칭을 입력하세요."
        if (name.length > 64) return "서버 별칭이 너무 깁니다."

        if (ip.isBlank()) return "IP 주소를 입력하세요."
        if (ip.length > 64) return "IP 주소가 너무 깁니다."
        // TODO: IPv4/IPv6 형식 검증은 다음 단계에서 추가 가능(정규식/InetAddress)

        val p = port.toIntOrNull() ?: return "포트는 숫자여야 합니다."
        if (p !in 1..65535) return "포트 범위가 올바르지 않습니다."

        if (hmac.isBlank()) return "HMAC 키를 입력하세요."
        if (hmac.length < 16) return "HMAC 키가 너무 짧습니다."
        if (hmac.length > 256) return "HMAC 키가 너무 깁니다."

        return null
    }
}
