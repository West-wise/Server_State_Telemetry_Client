package com.SST.server_state_telemetry_client.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RetroColorScheme = lightColorScheme(
    primary = PrimaryColor,
    onPrimary = OnPrimaryColor,
    secondary = Win95Teal,
    onSecondary = TextWhite,
    tertiary = Win95DarkGray,
    onTertiary = TextWhite,
    background = BackgroundColor,
    onBackground = TextBlack,
    surface = SurfaceColor,
    onSurface = TextBlack,
    error = ErrorColor,
    onError = TextWhite
)

@Composable
fun Server_State_Telemetry_ClientTheme(
    darkTheme: Boolean = false, // Forced Light/Gray Mode
    dynamicColor: Boolean = false, // Disable dynamic color
    content: @Composable () -> Unit
) {
    val colorScheme = RetroColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            // Status bar icons should be dark if background is light
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}