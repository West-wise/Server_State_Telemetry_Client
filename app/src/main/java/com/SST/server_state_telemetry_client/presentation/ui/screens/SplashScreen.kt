package com.SST.server_state_telemetry_client.presentation.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.SST.server_state_telemetry_client.presentation.navigation.Screen
import com.SST.server_state_telemetry_client.ui.theme.Card
import com.SST.server_state_telemetry_client.ui.theme.Divider
import com.SST.server_state_telemetry_client.ui.theme.Primary
import com.SST.server_state_telemetry_client.ui.theme.Server_State_Telemetry_ClientTheme
import com.SST.server_state_telemetry_client.ui.theme.Text2
import com.SST.server_state_telemetry_client.ui.theme.Text3
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController?) {
    var progress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "splash_progress"
    )

    val percent = (animatedProgress * 100).toInt().coerceIn(0, 100)

    LaunchedEffect(Unit) {
        val steps = 20
        for (i in 1..steps) {
            delay(80)
            progress = i.toFloat() / steps.toFloat()
        }
        delay(300)
        navController?.navigate(Screen.Dashboard.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Card)
    ) {
        // Top group
        Column(
            modifier = Modifier
                .padding(top = 80.dp, start = 28.dp, end = 28.dp)
        ) {
            // Logo box
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(10.dp, RoundedCornerShape(18.dp))
                    .background(Primary, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "서버 상태를\n한눈에 확인해요",
                style = MaterialTheme.typography.displaySmall,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "CPU·메모리·네트워크까지,\n실시간으로 안전하게.",
                style = MaterialTheme.typography.bodyLarge,
                color = Text2,
            )
        }

        Spacer(Modifier.weight(1f))

        // Bottom group
        Column(
            modifier = Modifier
                .padding(start = 28.dp, end = 28.dp, bottom = 60.dp)
        ) {
            // Track + fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Divider)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = Primary,
                    trackColor = Color.Transparent,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "서버 목록 불러오는 중",
                    style = MaterialTheme.typography.labelLarge,
                    color = Text3,
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = Text3,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    Server_State_Telemetry_ClientTheme {
        SplashScreen(navController = null)
    }
}
