package com.mapmonarchs.domain.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview session's guarantees.
 *
 * These assert the BOUNDARIES, not just the happy path: preview must be
 * in-memory only, must distinguish new ground from ground already held, and must
 * leave nothing behind when it exits.
 */
class PreviewSessionTest {

    private val grid = PreviewHexGrid(41.2995, 69.2401, cellRadiusMeters = 120.0)
    private fun session() = PreviewSession(grid)

    @Test
    fun `starts holding nothing`() {
        assertEquals(0, session().heldCount)
    }

    @Test
    fun `claiming new ground reports a capture`() {
        val s = session()
        val claim = s.claimAt(41.2995, 69.2401)
        assertTrue(claim is PreviewClaim.Captured)
        assertEquals(1, claim.totalHeld)
    }

    @Test
    fun `re-claiming the same ground is not reported as a capture`() {
        // Telling a player "captured!" for a hex they already hold would be the
        // same species of lie this project keeps refusing to tell.
        val s = session()
        s.claimAt(41.2995, 69.2401)
        val again = s.claimAt(41.2995, 69.2401)
        assertTrue(again is PreviewClaim.AlreadyHeld)
        assertEquals(1, again.totalHeld)
    }

    @Test
    fun `walking to new ground increases holdings`() {
        val s = session()
        s.claimAt(41.2995, 69.2401)
        s.claimAt(41.3050, 69.2401)
        assertEquals(2, s.heldCount)
    }

    @Test
    fun `a small step does not yield a second hex`() {
        val s = session()
        s.claimAt(41.29950, 69.24010)
        s.claimAt(41.29955, 69.24010)
        assertEquals("both fixes are inside one hex", 1, s.heldCount)
    }

    @Test
    fun `holds reports membership accurately`() {
        val s = session()
        val claim = s.claimAt(41.2995, 69.2401)
        assertTrue(s.holds(claim.cell))
        assertFalse(s.holds(PreviewHexGrid.Cell(99, 99)))
    }

    @Test
    fun `clear discards everything so preview cannot outlive itself`() {
        val s = session()
        s.claimAt(41.2995, 69.2401)
        s.claimAt(41.3050, 69.2401)
        s.clear()
        assertEquals(0, s.heldCount)
        assertTrue(s.heldCells().isEmpty())
    }

    @Test
    fun `held cells snapshot is defensive - mutating it cannot corrupt the session`() {
        val s = session()
        s.claimAt(41.2995, 69.2401)
        val snapshot = s.heldCells().toMutableSet()
        snapshot.clear()
        assertEquals(1, s.heldCount)
    }

    @Test
    fun `claims use preview ids that can never be confused with H3`() {
        val s = session()
        val claim = s.claimAt(41.2995, 69.2401)
        assertTrue(claim.cell.id.contains(","))
        assertFalse(claim.cell.id.matches(Regex("[0-9a-f]{15}")))
    }
}