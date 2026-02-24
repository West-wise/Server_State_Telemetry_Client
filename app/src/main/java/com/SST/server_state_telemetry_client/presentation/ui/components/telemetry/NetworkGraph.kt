package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path // TODO(modified): 올바른 Path
import androidx.compose.ui.graphics.drawscope.Stroke // TODO(modified): Stroke import
import androidx.compose.ui.unit.dp
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
    val maxSize = 60 // TODO(modified): 최근 60초
    if (list.size > maxSize) {
        repeat(list.size - maxSize) { list.removeAt(0) }
    }
}

@Composable
fun NetworkGraphCard(
        currentBps: Long,
        history: List<Long>?, // null 방어
        label: String
) {
    val safeHistory = history ?: emptyList()
    val current = max(0L, currentBps)
    val colorPrimary = MaterialTheme.colorScheme.primary

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
        val path = Path()

        safeHistory.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v.toFloat() / maxVal.toFloat()) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path = path, color = colorPrimary, style = Stroke(width = 4f))
    }
}
