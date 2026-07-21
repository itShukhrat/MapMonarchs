package com.mapmonarchs.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Premium surface brushes.
 *
 * WHY GRADIENTS AT ALL: a flat `Void` fill is what made the non-map screens read
 * as raw utility rather than as a strategy product. A dark game surface has a
 * light source -- these give the app a faint horizon glow at the top fading to
 * near-black, so every screen reads as a lit control room instead of a black
 * rectangle. The effect is deliberately subtle: it must never compete with the
 * map or with territory colour for attention.
 *
 * These are static brushes (no size needed) so they cost nothing to recompose.
 */
object MapMonarchsBrush {

    /** The command-screen background: a lit horizon at the top settling into Void. */
    val screen: Brush = Brush.verticalGradient(
        0f to HorizonTop,
        0.35f to Obsidian,
        1f to Void
    )

    /**
     * A raised card: top edge catches light, body settles darker. Used to group
     * readouts into surfaces instead of stranding them on bare black.
     */
    val card: Brush = Brush.verticalGradient(
        0f to ObsidianRaised,
        1f to Obsidian
    )

    /**
     * The primary action when it is READY. A quiet diagonal lift toward the
     * accent -- present enough to feel high-value and tactile, restrained enough
     * that it is edge-and-glow, not a saturated slab (the slab was a real bug:
     * it wore a territory colour).
     */
    val actionReady: Brush = Brush.verticalGradient(
        0f to Color(0xFF17233A),
        1f to Color(0xFF0E1626)
    )

    /** A soft accent halo, for the glow behind a live selection or action. */
    val accentHalo: Brush = Brush.radialGradient(
        0f to SovereignHalo,
        1f to Color(0x00000000)
    )

    /**
     * Prestige wash -- used ONLY on the locked future surfaces (Empire, Shop) to
     * make "not yet" read as desirable rather than disabled. Still the accent, not
     * a second hue: a locked destination should feel like withheld power, and the
     * app's power colour is Sovereign.
     */
    val prestige: Brush = Brush.verticalGradient(
        0f to Color(0xFF1A2740),
        0.6f to Obsidian,
        1f to Void
    )

    /**
     * The command dock. A milled instrument catches light on its top face and
     * falls away below -- a flat fill reads as a plastic tab bar, which is the
     * one reference this product is trying not to resemble.
     */
    val dock: Brush = Brush.verticalGradient(
        0f to Color(0xFF161D29),
        0.5f to FloatCore,
        1f to Color(0xFF070A10)
    )
}
