package com.mapmonarchs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark-only by design.
 *
 * Dynamic colour is deliberately NOT used: it lets the device wallpaper repaint
 * the brand, which destroys a committed, ownable identity. A territory game needs
 * its colours to mean something consistent to every player.
 */
private val MapMonarchsColors = darkColorScheme(
    primary = Sovereign,
    onPrimary = Void,
    primaryContainer = SovereignDim,
    onPrimaryContainer = SovereignGlow,
    secondary = SovereignGlow,
    onSecondary = Void,
    background = Void,
    onBackground = TextPrimary,
    surface = Obsidian,
    onSurface = TextPrimary,
    surfaceVariant = Slate,
    onSurfaceVariant = TextMuted,
    outline = SlateEdge,
    error = Contested,
    onError = Void
)

@Composable
fun MapMonarchsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MapMonarchsColors,
        typography = Typography,
        content = content
    )
}