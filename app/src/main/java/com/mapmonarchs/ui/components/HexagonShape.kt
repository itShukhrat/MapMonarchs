package com.mapmonarchs.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

/**
 * The hexagon, as a clip shape.
 *
 * Extracted so the identity glyph, selection marks, and command button all cut
 * from the same geometry. One shape used consistently is what makes a visual
 * language; six near-identical hand-rolled paths is what makes a mess.
 */
object HexagonShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = minOf(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        for (i in 0..5) {
            val angle = Math.toRadians(60.0 * i - 30.0)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}