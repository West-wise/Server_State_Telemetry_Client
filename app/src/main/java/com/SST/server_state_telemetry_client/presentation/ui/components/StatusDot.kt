package com.SST.server_state_telemetry_client.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import com.SST.server_state_telemetry_client.ui.theme.Bad
import com.SST.server_state_telemetry_client.ui.theme.BadSoft
import com.SST.server_state_telemetry_client.ui.theme.Ok
import com.SST.server_state_telemetry_client.ui.theme.OkSoft

@Composable
fun StatusDot(isOnline: Boolean, size: Dp = 10.dp) {
    val color = if (isOnline) Ok else Bad
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.18f), radius = r)
        drawCircle(color, radius = r * 0.55f)
    }
}

@Composable
fun StatusPill(online: Boolean, modifier: Modifier = Modifier) {
    val (bg, fg) = if (online) OkSoft to Ok else BadSoft to Bad
    Row(
        modifier = modifier
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).background(fg, CircleShape))
        Text(
            if (online) "온라인" else "오프라인",
            color = fg,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusDotPreview() {
    Server_State_Telemetry_ClientTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(isOnline = true)
                StatusDot(isOnline = false)
                StatusDot(isOnline = true, size = 16.dp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusPillPreview() {
    Server_State_Telemetry_ClientTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(online = true)
                StatusPill(online = false)
            }
        }
    }
}
