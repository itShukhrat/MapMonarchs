package com.mapmonarchs.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MapMonarchs palette.
 *
 * Rule: ONE accent (Sovereign). The map is the hero, so the UI stays near-black
 * and desaturated -- territory colour must be the most saturated thing on screen.
 * No gold, no rainbow, no dynamic/wallpaper colour.
 *
 * ---------------------------------------------------------------------------
 * RAW vs SEMANTIC
 *
 * The values in the first half are the physical palette. New code should use the
 * SEMANTIC names in the second half, because a colour referenced directly cannot
 * be reasoned about: `Contested` names a hue, `DangerFg` names a meaning. When
 * territory rendering lands, enemy ground and a failed request will need to
 * diverge -- and they can only diverge if they were never the same constant.
 */

// ---- Raw surfaces: near-black, not grey ------------------------------------
val Void = Color(0xFF07090D)
val Obsidian = Color(0xFF0F131A)
val Slate = Color(0xFF1A1F29)
val SlateEdge = Color(0xFF2A3240)

// ---- The single accent -----------------------------------------------------
val Sovereign = Color(0xFF4C8DFF)
val SovereignGlow = Color(0xFF7FB0FF)
val SovereignDim = Color(0xFF1E3A66)

// ---- Raw hues --------------------------------------------------------------
val Contested = Color(0xFFFF4D4F)
val Neutral = Color(0xFF3A4354)

// ---- Type ------------------------------------------------------------------
val TextPrimary = Color(0xFFE8EDF5)
val TextMuted = Color(0xFF8A94A6)

// ============================================================================
// SEMANTIC TOKENS
// ============================================================================

// ---- Territory --------------------------------------------------------------
/**
 * A player's OWN ground is deliberately absent from this list. It is drawn in
 * their derived palette colour (see IdentityDerivation) because in a game about
 * ownership your land has to be recognisably yours, not a shared "friendly"
 * blue. These are the things it contrasts against.
 */
val TerritoryNeutral = Neutral
val TerritoryEnemy = Contested
val TerritoryUnclaimed = Color(0xFF141922)

/**
 * Fog: ground that exists but has not been observed.
 * Darker than the base surface, never lighter -- unexplored map should read as
 * absence, not as a scrim laid over something.
 */
val Fog = Color(0xFF04060A)

/** Discovery: something newly visible. Cyan, so it never reads as the accent. */
val Discovery = Color(0xFF22D3EE)

// ---- State -------------------------------------------------------------------
val DangerFg = Contested
val DangerEdge = Color(0xFF7A2326)
val SuccessFg = Color(0xFF10B981)
val SuccessEdge = Color(0xFF0B5D42)
val InfoFg = SovereignGlow

/**
 * Disabled and loading are separate tokens on purpose.
 *
 * "You cannot do this" and "we do not know yet" are different facts, and this
 * codebase already shipped one bug from collapsing exactly that distinction --
 * diagnostics rendering an unknown value as a confident one. A control that is
 * waiting must never look like a control that is refused.
 */
val DisabledFg = Color(0xFF5A6273)
val DisabledSurface = Slate
val LoadingFg = TextMuted

/**
 * Locked: a real feature that is not open yet.
 *
 * Distinct from Disabled. Disabled means "not now, and it is your state that
 * decides"; Locked means "not yet, and here is the condition". Rendering them
 * the same would make a roadmap look like a fault.
 */
val LockedFg = Color(0xFF6B7488)
val LockedEdge = Color(0xFF1F2531)

// ---- Floating surfaces --------------------------------------------------------
/**
 * The command bar sits ON the map, which is high-contrast and would show through
 * a flat fill as noise. These build a layered stack: a near-opaque core, a lifted
 * edge catching light from above, and a shadow that reads as physical separation
 * rather than as a drop-shadow effect.
 */
val FloatCore = Color(0xFF0B0F16)
val FloatEdge = Color(0xFF232C3A)
val FloatShadow = Color(0xCC000000)
