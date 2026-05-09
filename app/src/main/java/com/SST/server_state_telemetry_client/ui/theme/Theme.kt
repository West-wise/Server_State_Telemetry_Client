package com.SST.server_state_telemetry_client.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary            = Primary,
    onPrimary          = Color.White,
    primaryContainer   = PrimarySoft,
    onPrimaryContainer = Primary,
    background         = Bg,
    onBackground       = Text1,
    surface            = Card,
    onSurface          = Text1,
    surfaceVariant     = CardAlt,
    onSurfaceVariant   = Text2,
    outline            = Divider,
    error              = Bad,
    onError            = Color.White,
    errorContainer     = BadSoft,
    onErrorContainer   = Bad,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun Server_State_Telemetry_ClientTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Bg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
