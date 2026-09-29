package com.ncmcloud.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun NcmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = md_primary_dark,
            onPrimary = md_onPrimary_dark,
            background = md_background_dark,
            onBackground = md_onBackground_dark,
            surface = md_surface_dark,
            onSurface = md_onSurface_dark,
        )
    } else {
        lightColorScheme(
            primary = md_primary,
            onPrimary = md_onPrimary,
            background = md_background,
            onBackground = md_onBackground,
            surface = md_surface,
            onSurface = md_onSurface,
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
