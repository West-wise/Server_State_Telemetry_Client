package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.ui.theme.Primary
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import kotlin.math.max

fun formatBytesPerSec(bps: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val v = bps.toDouble()
    return when {
        v >= gb -> String.format("%.2f GB/s", v / gb)
        v >= mb -> String.format("%.2f MB/s", v / mb)
        v >= kb -> String.format("%.2f KB/s", v / kb)
        else -> "$bps B/s"
    }
}

fun pushHistory(list: MutableList<Long>, value: Long) {
    val safe = max(0L, value)
    list.add(safe)
    val maxSize = 60
    if (list.size > maxSize) {
        repeat(list.size - maxSize) { list.removeAt(0) }
    }
}

@Composable
fun NetworkGraphCard(
    currentBps: Long,
    history: List<Long>?,
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val safeHistory = history ?: emptyList()
    val current = max(0L, currentBps)

    Text(
        text = "${label}: ${formatBytesPerSec(current)}",
        style = MaterialTheme.typography.bodyMedium
    )

    Spacer(Modifier.height(8.dp))

    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
        if (safeHistory.size < 2) return@Canvas

        val maxVal = max(1L, safeHistory.maxOrNull() ?: 1L)
        val w = size.width
        val h = size.height

        val step = w / (safeHistory.size - 1).toFloat()

        val points = safeHistory.mapIndexed { i, v ->
            val x = i * step
            val y = h - (v.toFloat() / maxVal.toFloat()) * h
            x to y
        }

        val linePath = Path().apply {
            points.forEachIndexed { i, (x, y) ->
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        val areaPath = Path().apply {
            moveTo(0f, h)
            points.forEach { (x, y) -> lineTo(x, y) }
            lineTo(w, h)
            close()
        }

        val brush = Brush.verticalGradient(
            listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))
        )

        drawPath(areaPath, brush)
        drawPath(
            linePath, color,
            style = Stroke(
                2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkGraphCardPreview() {
    Server_State_Telemetry_ClientTheme {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = com.SST.server_state_telemetry_client.ui.theme.Card
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                NetworkGraphCard(
                    currentBps = 2_560_000L,
                    history = listOf(
                        500_000L, 800_000L, 1_200_000L, 2_000_000L, 1_800_000L,
                        2_560_000L, 2_200_000L, 1_900_000L, 2_100_000L, 2_560_000L,
                        3_000_000L, 2_800_000L, 2_400_000L, 1_500_000L, 1_000_000L,
                    ),
                    label = "↓ 다운로드",
                    color = Primary,
                )

                Spacer(Modifier.height(12.dp))

                NetworkGraphCard(
                    currentBps = 384_000L,
                    history = listOf(
                        100_000L, 200_000L, 384_000L, 300_000L, 250_000L,
                        150_000L, 384_000L, 420_000L, 380_000L, 350_000L,
                    ),
                    label = "↑ 업로드",
                    color = Color(0xFF9B6CDF),
                )
            }
        }
    }
}
