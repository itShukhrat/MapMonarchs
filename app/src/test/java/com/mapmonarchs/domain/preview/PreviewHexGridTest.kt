package com.mapmonarchs.domain.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PreviewHexGridTest {

    private val originLat = 41.2995
    private val originLng = 69.2401
    private val grid = PreviewHexGrid(originLat, originLng, cellRadiusMeters = 120.0)

    @Test
    fun `origin falls in the centre cell`() {
        assertEquals(PreviewHexGrid.Cell(0, 0), grid.cellAt(originLat, originLng))
    }

    @Test
    fun `cell assignment is deterministic`() {
        assertEquals(grid.cellAt(41.3010, 69.2420), grid.cellAt(41.3010, 69.2420))
    }

    @Test
    fun `a small step stays in the same cell`() {
        val here = grid.cellAt(originLat, originLng)
        val nudged = grid.cellAt(originLat + 0.00009, originLng)
        assertEquals(here, nudged)
    }

    @Test
    fun `walking far enough changes cell`() {
        val here = grid.cellAt(originLat, originLng)
        val away = grid.cellAt(originLat + 0.0045, originLng)
        assertNotEquals(here, away)
    }

    @Test
    fun `cell centre round-trips back to the same cell`() {
        listOf(
            PreviewHexGrid.Cell(0, 0),
            PreviewHexGrid.Cell(3, -2),
            PreviewHexGrid.Cell(-5, 4),
            PreviewHexGrid.Cell(12, 7)
        ).forEach { cell ->
            val c = grid.centerOf(cell)
            assertEquals("round trip failed for $cell", cell, grid.cellAt(c.lat, c.lng))
        }
    }

    @Test
    fun `boundary has six corners`() {
        assertEquals(6, grid.boundaryOf(PreviewHexGrid.Cell(0, 0)).size)
    }

    @Test
    fun `boundary corners surround the centre`() {
        val cell = PreviewHexGrid.Cell(2, 1)
        val centre = grid.centerOf(cell)
        val corners = grid.boundaryOf(cell)
        assertTrue(corners.any { it.lat > centre.lat })
        assertTrue(corners.any { it.lat < centre.lat })
        assertTrue(corners.any { it.lng > centre.lng })
        assertTrue(corners.any { it.lng < centre.lng })
    }

    @Test
    fun `cells tile without gaps near a boundary`() {
        val cell = PreviewHexGrid.Cell(0, 0)
        val neighbours = grid.disk(cell, 1).toSet()
        val c = grid.centerOf(cell)
        grid.boundaryOf(cell).forEach { corner ->
            val inwardLat = corner.lat + (c.lat - corner.lat) * 0.1
            val inwardLng = corner.lng + (c.lng - corner.lng) * 0.1
            val owner = grid.cellAt(inwardLat, inwardLng)
            assertTrue("corner leaked to a non-neighbour cell: $owner", neighbours.contains(owner))
        }
    }

    @Test
    fun `disk of radius zero is just the centre`() {
        assertEquals(listOf(PreviewHexGrid.Cell(4, -1)), grid.disk(PreviewHexGrid.Cell(4, -1), 0))
    }

    @Test
    fun `disk sizes follow the centred hexagonal numbers`() {
        assertEquals(1, grid.disk(PreviewHexGrid.Cell(0, 0), 0).size)
        assertEquals(7, grid.disk(PreviewHexGrid.Cell(0, 0), 1).size)
        assertEquals(19, grid.disk(PreviewHexGrid.Cell(0, 0), 2).size)
        assertEquals(37, grid.disk(PreviewHexGrid.Cell(0, 0), 3).size)
    }

    @Test
    fun `disk contains no duplicates`() {
        val d = grid.disk(PreviewHexGrid.Cell(0, 0), 3)
        assertEquals(d.size, d.toSet().size)
    }

    @Test
    fun `negative disk radius is rejected rather than silently returning nothing`() {
        try {
            grid.disk(PreviewHexGrid.Cell(0, 0), -1)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("negative"))
        }
    }

    @Test
    fun `distance is zero to itself and symmetric`() {
        val a = PreviewHexGrid.Cell(1, 2)
        val b = PreviewHexGrid.Cell(-3, 5)
        assertEquals(0, grid.distance(a, a))
        assertEquals(grid.distance(a, b), grid.distance(b, a))
    }

    @Test
    fun `immediate neighbours are distance one`() {
        val c = PreviewHexGrid.Cell(0, 0)
        grid.disk(c, 1).filter { it != c }.forEach {
            assertEquals("$it should be adjacent", 1, grid.distance(c, it))
        }
    }

    @Test
    fun `cell id is visibly not an H3 index`() {
        val id = PreviewHexGrid.Cell(3, -7).id
        assertEquals("3,-7", id)
        assertTrue("preview ids must contain a comma", id.contains(","))
        assertTrue("preview ids must not look like an H3 hex", !id.matches(Regex("[0-9a-f]{15}")))
    }

    @Test
    fun `cell size is roughly the requested radius`() {
        val c = PreviewHexGrid.Cell(0, 0)
        val centre = grid.centerOf(c)
        val corner = grid.boundaryOf(c).first()
        val dLat = (corner.lat - centre.lat) * 110_540.0
        val dLng = (corner.lng - centre.lng) * 111_320.0 * kotlin.math.cos(Math.toRadians(originLat))
        val metres = kotlin.math.sqrt(dLat * dLat + dLng * dLng)
        assertTrue("expected ~120 m, got $metres", abs(metres - 120.0) < 6.0)
    }
}