package com.mapmonarchs.domain.preview

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A hexagonal tiling used ONLY by preview mode.
 *
 * THIS IS NOT H3 AND MUST NEVER BE TREATED AS H3.
 *
 * Real territory is identified by an H3 cell that the SERVER derives from raw
 * coordinates -- that is the anti-cheat contract, and nothing here participates
 * in it. This grid exists so a player can understand the shape of the loop
 * before the live backend is reachable. Two reasons it is hand-rolled rather
 * than the real thing:
 *
 *  1. Pulling in the H3 native library would re-open the 16 KB page-size problem
 *     that already cost a debugging cycle, for a feature that is explicitly not
 *     the real geometry.
 *  2. If preview used real H3 ids, a future bug could let a preview cell be
 *     mistaken for a live one. Different id space makes that impossible.
 *
 * Cell ids are "q,r" axial coordinates -- visibly unlike an H3 index such as
 * 8928308280fffff, so the two can never be confused in a log or a database.
 *
 * Geometry: pointy-top hexes on a local equirectangular projection. Accurate
 * enough at city scale; deliberately not accurate at continental scale, because
 * preview is a local teaching tool.
 */
class PreviewHexGrid(
    private val originLat: Double,
    private val originLng: Double,
    /** Hex circumradius in metres. ~120 m gives a walkable cell at city scale. */
    private val cellRadiusMeters: Double = 120.0
) {

    /** Axial cell coordinate. */
    data class Cell(val q: Int, val r: Int) {
        /** Deliberately unlike an H3 index. */
        val id: String get() = "$q,$r"
    }

    data class LatLng(val lat: Double, val lng: Double)

    /** Which preview cell contains this position. */
    fun cellAt(lat: Double, lng: Double): Cell {
        val (x, y) = toMeters(lat, lng)
        val qF = (sqrt(3.0) / 3.0 * x - y / 3.0) / cellRadiusMeters
        val rF = (2.0 / 3.0 * y) / cellRadiusMeters
        return roundAxial(qF, rF)
    }

    /** Centre of a cell, back in geographic coordinates. */
    fun centerOf(cell: Cell): LatLng {
        val x = cellRadiusMeters * (sqrt(3.0) * cell.q + sqrt(3.0) / 2.0 * cell.r)
        val y = cellRadiusMeters * (3.0 / 2.0 * cell.r)
        return toLatLng(x, y)
    }

    /** The six corners of a cell, ordered, ready to draw as a closed polygon. */
    fun boundaryOf(cell: Cell): List<LatLng> {
        val x = cellRadiusMeters * (sqrt(3.0) * cell.q + sqrt(3.0) / 2.0 * cell.r)
        val y = cellRadiusMeters * (3.0 / 2.0 * cell.r)
        return (0 until 6).map { i ->
            val angle = Math.toRadians(60.0 * i - 30.0)
            toLatLng(x + cellRadiusMeters * cos(angle), y + cellRadiusMeters * kotlin.math.sin(angle))
        }
    }

    /** All cells within [radius] steps of a cell -- used to draw the visible field. */
    fun disk(center: Cell, radius: Int): List<Cell> {
        require(radius >= 0) { "radius must not be negative" }
        val cells = mutableListOf<Cell>()
        for (dq in -radius..radius) {
            val lower = maxOf(-radius, -dq - radius)
            val upper = minOf(radius, -dq + radius)
            for (dr in lower..upper) cells.add(Cell(center.q + dq, center.r + dr))
        }
        return cells
    }

    /** Step distance between two cells. */
    fun distance(a: Cell, b: Cell): Int {
        val dq = a.q - b.q
        val dr = a.r - b.r
        return (abs(dq) + abs(dq + dr) + abs(dr)) / 2
    }

    // --- projection -------------------------------------------------------

    private fun toMeters(lat: Double, lng: Double): Pair<Double, Double> {
        val x = (lng - originLng) * METERS_PER_DEGREE_LNG_AT_EQUATOR * cos(Math.toRadians(originLat))
        val y = (lat - originLat) * METERS_PER_DEGREE_LAT
        return x to y
    }

    private fun toLatLng(x: Double, y: Double): LatLng {
        val lng = originLng + x / (METERS_PER_DEGREE_LNG_AT_EQUATOR * cos(Math.toRadians(originLat)))
        val lat = originLat + y / METERS_PER_DEGREE_LAT
        return LatLng(lat, lng)
    }

    /** Cube rounding -- the standard way to snap fractional axial coords to a cell. */
    private fun roundAxial(qF: Double, rF: Double): Cell {
        val sF = -qF - rF
        var q = qF.roundToInt()
        var r = rF.roundToInt()
        val s = sF.roundToInt()

        val dq = abs(q - qF)
        val dr = abs(r - rF)
        val ds = abs(s - sF)

        if (dq > dr && dq > ds) q = -r - s else if (dr > ds) r = -q - s
        return Cell(q, r)
    }

    private companion object {
        const val METERS_PER_DEGREE_LAT = 110_540.0
        const val METERS_PER_DEGREE_LNG_AT_EQUATOR = 111_320.0
    }
}