package com.blackchapel.recipai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.blackchapel.recipai.data.ThemeMode

val LocalColors = staticCompositionLocalOf { DarkColors }

/** Shorthand for the current [RecipaiColors]. */
val colors: RecipaiColors
    @Composable get() = LocalColors.current

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Material 3 is only the base (menus, sheets, switches); everything visible
 * reads [LocalColors]. The M3 scheme is filled in so stock components match.
 */
@Composable
fun RecipaiTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.saffron, onPrimary = OnFlame, background = c.canvas, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.surface2, onSurfaceVariant = c.mute,
            surfaceContainer = c.surface2, surfaceContainerHigh = c.surface2, surfaceContainerLow = c.surface,
            outline = c.lineStrong, outlineVariant = c.line, error = c.danger, scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.saffron, onPrimary = OnFlame, background = c.canvas, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.surface2, onSurfaceVariant = c.mute,
            surfaceContainer = c.surface2, surfaceContainerHigh = c.surface2, surfaceContainerLow = c.surface,
            outline = c.lineStrong, outlineVariant = c.line, error = c.danger, scrim = c.scrim,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalColors provides c,
            LocalContentColor provides c.body,
            LocalTextSelectionColors provides TextSelectionColors(handleColor = c.saffron, backgroundColor = c.saffron.copy(alpha = 0.35f)),
            content = content,
        )
    }
}
