package com.mapmonarchs.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * MapMonarchs type scale.
 *
 * Two ideas drive this:
 *  1. Numbers are the emotional payload of a territory game ("47 hexes held").
 *     They get the display treatment, and they use tabular figures ("tnum") so a
 *     ticking count does not jitter as glyph widths change.
 *  2. Labels are small, uppercase, and widely tracked -- they read as instrument
 *     panel, not as body copy. This is what keeps the HUD feeling strategic
 *     rather than chatty.
 */
private val Tabular = "tnum"

val Typography = Typography(
    // Holdings / large counts
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = Tabular
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = Tabular
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // Instrument-panel labels: small, tracked, meant to be set in UPPERCASE
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 2.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    )
)

/**
 * Roles Material's Typography has no slot for.
 *
 * These are not decoration. Each exists because a specific piece of this product
 * reads wrongly without it:
 *
 *  - NavLabel: 9sp tracked, sitting under a 22dp glyph in a floating bar. At
 *    labelSmall (10sp/2.5 tracking) five labels do not fit across a 360dp screen
 *    without truncating, and a truncated navigation label is a broken control.
 *  - Caption: the smallest readable prose. bodyMedium is too loud for a
 *    secondary line under a status row.
 *  - Badge: a count on a glyph. Tabular so 9 -> 10 does not shift the badge.
 *  - Coordinate: monospace, because latitude and longitude are compared
 *    character by character and proportional digits make that hostile. This is
 *    the only monospace in the product and it is reserved for real coordinates.
 */
object MapMonarchsType {

    val navLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        letterSpacing = 1.2.sp
    )

    val caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    )

    val badge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 10.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = Tabular
    )

    val coordinate = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        fontFeatureSettings = Tabular
    )
}
