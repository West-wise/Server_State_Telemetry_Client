package com.SST.server_state_telemetry_client.presentation.ui.components.telemetry

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.R
import kotlin.math.max

@Composable
fun SevenSegPercentText(percent: Int) {
    val p = percent.coerceIn(0, 100)
    // Custom 7-segment font for digital look
    val dsegFont = FontFamily(Font(R.font.dseg7_classic))
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) {
        Text(
                text = String.format("%3d%%", p),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontFamily = dsegFont,
                style = MaterialTheme.typography.headlineMedium
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
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace
    )
}

@Composable
fun RatioText(left: Long, right: Long, suffix: String) {
    Text(
            text = "${max(0L,left)} / ${max(0L,right)}$suffix",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace
    )
}
