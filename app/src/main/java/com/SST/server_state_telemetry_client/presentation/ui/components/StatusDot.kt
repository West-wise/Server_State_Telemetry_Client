package com.SST.server_state_telemetry_client.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusDot(isOnline: Boolean) {

    val baseColor = if (isOnline)
        Color(0xFF00E676)
    else
        Color(0xFFFF1744)

    Canvas(
        modifier = Modifier.size(15.dp)
    ) {

        val radius = size.minDimension / 4f

        // ---- 1️⃣ 바깥 Glow Layer (가장 흐림) ----
        drawCircle(
            color = baseColor.copy(alpha = 0.15f),
            radius = radius * 2.5f
        )

        // ---- 2️⃣ 중간 Glow Layer ----
        drawCircle(
            color = baseColor.copy(alpha = 0.3f),
            radius = radius * 1.8f
        )

        // ---- 3️⃣ 내부 Glow Layer ----
        drawCircle(
            color = baseColor.copy(alpha = 0.6f),
            radius = radius * 1.3f
        )

        // ---- 4️⃣ 중심 Core LED ----
        drawCircle(
            color = baseColor,
            radius = radius
        )
    }
}