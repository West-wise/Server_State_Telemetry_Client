package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.ui.theme.Bad
import com.SST.server_state_telemetry_client.ui.theme.Ok
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import com.SST.server_state_telemetry_client.ui.theme.Divider
import com.SST.server_state_telemetry_client.ui.theme.Primary
import kotlin.math.max

@Composable
fun MetricRing(
    percent: Int,
    size: Dp = 64.dp,
    stroke: Dp = 8.dp,
    color: Color = Primary,
) {
    val p = percent.coerceIn(0, 100)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            drawArc(
                Divider, -90f, 360f, false,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            drawArc(
                color, -90f, 360f * p / 100f, false,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        Text(
            "$p%",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold
            ),
        )
    }
}

@Composable
fun UptimeText(seconds: Long) {
    val s = max(0L, seconds)
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    Text(
        text = String.format("%dd %02d:%02d:%02d", d, h, m, sec),
        style = MaterialTheme.typography.titleLarge,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
fun RatioText(left: Long, right: Long, suffix: String) {
    Text(
        text = "${max(0L, left)} / ${max(0L, right)}$suffix",
        style = MaterialTheme.typography.titleLarge,
        fontFamily = FontFamily.Monospace
    )
}

@Preview(showBackground = true)
@Composable
private fun MetricRingPreview() {
    Server_State_Telemetry_ClientTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricRing(percent = 72)
                MetricRing(percent = 45, size = 88.dp, stroke = 10.dp, color = Ok)
                MetricRing(percent = 91, size = 48.dp, stroke = 6.dp, color = Bad)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UptimeTextPreview() {
    Server_State_Telemetry_ClientTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                UptimeText(seconds = 1_051_868L)
                UptimeText(seconds = 3661L)
                UptimeText(seconds = 0L)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RatioTextPreview() {
    Server_State_Telemetry_ClientTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                RatioText(left = 384, right = 1024, suffix = "")
                RatioText(left = 12048, right = 65535, suffix = " fds")
            }
        }
    }
}
