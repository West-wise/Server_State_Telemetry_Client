package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.domain.model.DiskSummary
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PartitionsDetailDialog(disk: DiskSummary?, onDismiss: () -> Unit) {
    val d = disk
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partitions") },
        text = {
            if (d == null) {
                Text("No disk info.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PartitionPieRow("/", d.usedRoot, d.totalRoot)
                    PartitionPieRow("/home", d.usedHome, d.totalHome)
                    PartitionPieRow("/var", d.usedVar, d.totalVar)
                    PartitionPieRow("/boot", d.usedBoot, d.totalBoot)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
fun PartitionPieRow(label: String, used: Long, total: Long) {
    val safeTotal = max(1L, total)
    val safeUsed = min(max(0L, used), safeTotal)
    val ratio = safeUsed.toFloat() / safeTotal.toFloat()
    val colorPrimary = MaterialTheme.colorScheme.primary

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(54.dp)) {
            val sweep = 360f * ratio
            drawArc(
                color = colorPrimary,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 10f)
            )
            drawArc(
                color = colorPrimary,
                startAngle = -90f + sweep,
                sweepAngle = 360f - sweep,
                useCenter = false,
                style = Stroke(width = 10f)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("${(ratio * 100).roundToInt()}%  (${formatBytes(safeUsed)} / ${formatBytes(safeTotal)})",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatBytes(v: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val tb = gb * 1024.0
    val d = v.toDouble()
    return when {
        d >= tb -> String.format("%.2f TB", d / tb)
        d >= gb -> String.format("%.2f GB", d / gb)
        d >= mb -> String.format("%.2f MB", d / mb)
        d >= kb -> String.format("%.2f KB", d / kb)
        else -> "$v B"
    }
}
