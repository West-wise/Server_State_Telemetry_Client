package com.SST.server_state_telemetry_client.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn // TODO(modified)
import androidx.compose.foundation.lazy.items // TODO(modified)
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf // TODO(modified)
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf // TODO(modified)
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign // TODO(modified)
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.SST.server_state_telemetry_client.presentation.navigation.Screen
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import com.SST.server_state_telemetry_client.ui.theme.TerminalBlack
import com.SST.server_state_telemetry_client.ui.theme.TerminalGreen
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun SplashScreen(navController: NavController?) { // TODO(modified): Preview 안전을 위해 nullable
    val bootSequence = remember { // TODO(modified): remember로 고정(불필요 재생성 방지)
        listOf(
            "SST BIOS v1.0.0",
            "Copyright (C) 2026 SST Corp.",
            "",
            "[OK] Main Processor   : Kotlin Coroutines",
            "[OK] Network Stack    : Ktor Client",
            "[OK] UI Subsystem     : Jetpack Compose",
            "[..] Loading profiles : /data/user/0/.../files",
            "[..] Checking servers : registered.db",
            "[..] Starting daemon  : sst-client",
            "",
            "Booting Dashboard..."
        )
    }

    val displayedLines = remember { mutableStateListOf<String>() }
    var stepIndex by remember { mutableIntStateOf(0) } // TODO(modified): 진행 단계
    var cursorOn by remember { mutableStateOf(true) } // TODO(modified): 커서 깜빡임

    val maxLines = 14 // TODO(modified): 로그 최대 줄 수(메모리/성능 방어)

    LaunchedEffect(Unit) { // TODO(modified)
        // TODO(modified): 커서 깜빡임 (독립 루프)
        while (stepIndex < bootSequence.size) {
            cursorOn = !cursorOn
            delay(350)
        }
    }

    LaunchedEffect(Unit) {
        bootSequence.forEachIndexed { idx, line ->
            // TODO(modified): 타자 효과(한 글자씩) - 너무 느리면 delay 줄이세요
            if (line.isEmpty()) {
                displayedLines.add("")
                stepIndex = idx + 1
                delay(180)
                return@forEachIndexed
            }

            val sb = StringBuilder()
            displayedLines.add("") // 자리 확보
            val linePos = displayedLines.lastIndex

            line.forEach { ch ->
                sb.append(ch)
                displayedLines[linePos] = sb.toString()
                delay(12) // TODO(modified): 타이핑 속도
            }

            stepIndex = idx + 1

            // TODO(modified): 로그가 너무 길어지면 앞에서 자름(성능/메모리)
            while (displayedLines.size > maxLines) {
                displayedLines.removeAt(0)
            }

            delay(120) // TODO(modified): 라인 간 템포
        }

        delay(600) // TODO(modified): 마지막 여운

        // TODO(modified): navController null 체크 (Preview/테스트 안전)
        navController?.navigate(Screen.Dashboard.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    // TODO(modified): 진행률 (0.0~1.0)
    val progress = if (bootSequence.isEmpty()) 0f else stepIndex.toFloat() / bootSequence.size.toFloat()
    val percent = min(100, max(0, (progress * 100f).toInt()))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            // ===== Header =====
            Text(
                text = "SST // BOOT",
                color = TerminalGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(), // TODO(modified)
                textAlign = TextAlign.Center // TODO(modified)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Initializing telemetry client...",
                color = TerminalGreen.copy(alpha = 0.8f), // TODO(modified)
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ===== Log Area (scroll-like, but keeps last N lines) =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // TODO(modified): 남은 공간을 로그가 차지 -> 레이아웃 안정
                    .background(TerminalBlack)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(displayedLines) { line ->
                        Text(
                            text = line,
                            color = TerminalGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }

                    // TODO(modified): 깜빡 커서(항상 마지막에)
                    item {
                        Text(
                            text = if (cursorOn) "▌" else " ", // TODO(modified): 커서 모양 변경
                            color = TerminalGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ===== Footer: progress bar + percent =====
            BootProgressBar(
                progress = progress
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Loading...",
                    color = TerminalGreen.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Text(
                    text = "$percent%",
                    color = TerminalGreen.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// TODO(modified): 외부 의존성 없이 터미널 느낌 진행 바 구현(성능 매우 가벼움)
@Composable
private fun BootProgressBar(progress: Float) {
    val safe = when {
        progress.isNaN() -> 0f // TODO(modified): 방어
        progress < 0f -> 0f
        progress > 1f -> 1f
        else -> progress
    }

    val totalBlocks = 28 // TODO(modified)
    val filled = (safe * totalBlocks).toInt().coerceIn(0, totalBlocks) // TODO(modified)

    val bar = buildString {
        append("[")
        repeat(filled) { append("█") }
        repeat(totalBlocks - filled) { append(" ") }
        append("]")
    }

    Text(
        text = bar,
        color = TerminalGreen,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    Server_State_Telemetry_ClientTheme {
        SplashScreen(navController = null) // TODO(modified): Preview는 null로
    }
}
