package com.mapmonarchs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The navigation icon language.
 *
 * ONE IDEA, APPLIED WITHOUT EXCEPTION: every glyph is the same hexagon carrying a
 * different mark inside it. The atom of this game is a hex, so every destination
 * is a kind of cell -- the map is a cell you stand in, claims are cells you hold,
 * missions are a cell with an objective, social is cells that overlap, command is
 * a cell you read instruments from.
 *
 * This is what makes the set feel drawn rather than collected. A pin, a trophy, a
 * pair of silhouettes and a hamburger would each be individually fine and would
 * look like five icons borrowed from five different products.
 *
 * SHARED CONSTRAINTS so nothing drifts: one optical grid, one stroke weight
 * derived from that grid, one cap and join, and an outer hex identical in every
 * glyph. Inner marks live inside a shared inset and never crowd the wall.
 *
 * Drawn as geometry rather than shipped as vector assets because these are a few
 * primitives that must stay in lockstep. Five XML files drift the moment one is
 * edited alone.
 */

/** The optical grid every glyph is designed against. */
val NavIconSize = 24.dp

enum class NavGlyph { MAP, CLAIMS, MISSIONS, SOCIAL, PROFILE, COMMAND }

/** Flat-top hexagon, matching the territory geometry drawn on the map. */
private fun hexPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = PI.toFloat() / 3f * i
        val x = cx + r * cos(a)
        val y = cy + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * A navigation glyph.
 *
 * [filled] is the selected state and is a SHAPE change, not only a colour change:
 * the inner mark solidifies or thickens. Selection carried by colour alone is
 * invisible to a large minority of players, and this bar is the primary way to
 * move through the app.
 */
@Composable
fun NavIconGlyph(
    glyph: NavGlyph,
    color: Color,
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.42f
        val ir = size.minDimension * 0.20f
        val sw = size.minDimension * 0.073f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun heavy(f: Float) = Stroke(sw * f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // The shared shell, identical in every glyph.
        drawPath(hexPath(cx, cy, r), color, style = st)

        when (glyph) {
            // A cell you stand in: a point at its centre.
            NavGlyph.MAP ->
                if (filled) drawCircle(color, ir * 0.55f, Offset(cx, cy))
                else drawCircle(color, ir * 0.5f, Offset(cx, cy), style = st)

            // Cells you hold: a nested cell, solid once held.
            NavGlyph.CLAIMS ->
                if (filled) drawPath(hexPath(cx, cy, ir), color)
                else drawPath(hexPath(cx, cy, ir), color, style = st)

            // An objective: an upward chevron, progress through the cell.
            NavGlyph.MISSIONS -> drawPath(
                Path().apply {
                    moveTo(cx - ir, cy + ir * 0.45f)
                    lineTo(cx, cy - ir * 0.5f)
                    lineTo(cx + ir, cy + ir * 0.45f)
                },
                color,
                style = if (filled) heavy(1.5f) else st
            )

            // Two cells that overlap: other players adjacent to you.
            NavGlyph.SOCIAL -> {
                val o = ir * 0.62f
                val rr = ir * 0.78f
                if (filled) {
                    drawPath(hexPath(cx - o, cy, rr), color)
                    drawPath(hexPath(cx + o, cy, rr), color)
                } else {
                    drawPath(hexPath(cx - o, cy, rr), color, style = st)
                    drawPath(hexPath(cx + o, cy, rr), color, style = st)
                }
            }

            // The player's own cell, seated: one mark held above a base.
            //
            // Deliberately NOT a person silhouette. A head-and-shoulders icon is
            // the single most generic mark in mobile software and would be the
            // one glyph in this bar that stopped speaking the game's language.
            // This is the same cell every other glyph is built from, raised to
            // the position the crest reserves for whoever holds it.
            NavGlyph.PROFILE -> {
                val hr = ir * 0.46f
                val hy = cy - ir * 0.34f
                if (filled) drawPath(hexPath(cx, hy, hr), color)
                else drawPath(hexPath(cx, hy, hr), color, style = st)
                drawLine(
                    color,
                    Offset(cx - ir * 0.86f, cy + ir * 0.62f),
                    Offset(cx + ir * 0.86f, cy + ir * 0.62f),
                    strokeWidth = if (filled) sw * 1.5f else sw,
                    cap = StrokeCap.Round
                )
            }

            // Instruments: readout lines inside the cell.
            NavGlyph.COMMAND -> {
                val w = ir * 0.95f
                val gap = ir * 0.62f
                listOf(-gap, 0f, gap).forEachIndexed { i, dy ->
                    val half = if (i == 1) w else w * 0.66f
                    drawLine(
                        color,
                        Offset(cx - half, cy + dy),
                        Offset(cx + half, cy + dy),
                        strokeWidth = sw * if (filled) 1.45f else 1f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * The lock mark, drawn in the same language rather than borrowed from Material.
 *
 * A padlock from an icon font would be the one glyph in the set that came from
 * somewhere else, and it would show.
 */
@Composable
fun NavLockMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = size.minDimension * 0.16f
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.28f, h * 0.16f),
            size = Size(w * 0.44f, h * 0.52f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.18f, h * 0.46f),
            size = Size(w * 0.64f, h * 0.42f),
            cornerRadius = CornerRadius(sw, sw)
        )
    }
}
