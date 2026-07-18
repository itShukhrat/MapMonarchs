package com.mapmonarchs.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MapMonarchs palette.
 *
 * Rule: ONE accent (Sovereign). The map is the hero, so the UI stays near-black
 * and desaturated -- territory colour must be the most saturated thing on screen.
 * No gold, no rainbow, no dynamic/wallpaper colour.
 */

// Base surfaces -- near-black, not grey
val Void = Color(0xFF07090D)
val Obsidian = Color(0xFF0F131A)
val Slate = Color(0xFF1A1F29)
val SlateEdge = Color(0xFF2A3240)

// The single accent
val Sovereign = Color(0xFF4C8DFF)
val SovereignGlow = Color(0xFF7FB0FF)
val SovereignDim = Color(0xFF1E3A66)

// Semantic
val Contested = Color(0xFFFF4D4F)
val Neutral = Color(0xFF3A4354)

// Type
val TextPrimary = Color(0xFFE8EDF5)
val TextMuted = Color(0xFF8A94A6)