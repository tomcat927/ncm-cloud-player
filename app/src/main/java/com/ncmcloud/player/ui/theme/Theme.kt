package com.ncmcloud.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun NcmTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = darkColorScheme(
        primary = NeteaseRed,
        onPrimary = Color.White,
        background = BackgroundDark,
        onBackground = Color.White,
        surface = SurfaceDark,
        onSurface = Color.White,
        surfaceVariant = SurfaceLight,
        onSurfaceVariant = TextGray,
        error = NeteaseRed,
        onError = Color.White,
    )

    MaterialTheme(colorScheme = colors, content = content)
}
