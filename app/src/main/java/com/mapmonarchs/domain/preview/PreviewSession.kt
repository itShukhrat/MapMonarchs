package com.mapmonarchs.domain.preview

/**
 * An in-memory simulation of walk -> claim -> hold.
 *
 * HARD BOUNDARIES, and they are the whole point:
 *  - No network. Not one call, not even a read.
 *  - No persistence. Held entirely in memory and discarded when preview exits,
 *    so nothing can survive to be mistaken for real state later.
 *  - No H3. Cells come from PreviewHexGrid, whose id space cannot collide with a
 *    real cell index.
 *  - No server contract. Nothing here is ever submitted as a claim.
 *
 * This exists so a player can understand the loop before the live backend is
 * reachable. It is a teaching tool, and the UI is required to say so at all times.
 */
class PreviewSession(private val grid: PreviewHexGrid) {

    private val held = LinkedHashSet<PreviewHexGrid.Cell>()

    val heldCount: Int get() = held.size
    fun heldCells(): Set<PreviewHexGrid.Cell> = held.toSet()
    fun holds(cell: PreviewHexGrid.Cell): Boolean = held.contains(cell)

    /**
     * Claims the cell containing this position.
     *
     * Returns the outcome so the UI can react honestly -- re-claiming ground you
     * already hold is a distinct result from taking new ground, and telling the
     * player "captured!" for a hex they already had would be the same species of
     * lie this project keeps refusing to tell.
     */
    fun claimAt(lat: Double, lng: Double): PreviewClaim {
        val cell = grid.cellAt(lat, lng)
        return if (held.add(cell)) {
            PreviewClaim.Captured(cell, held.size)
        } else {
            PreviewClaim.AlreadyHeld(cell, held.size)
        }
    }

    /** Discards everything. Called on exit so preview state cannot outlive preview. */
    fun clear() = held.clear()
}

sealed interface PreviewClaim {
    val cell: PreviewHexGrid.Cell
    val totalHeld: Int

    data class Captured(
        override val cell: PreviewHexGrid.Cell,
        override val totalHeld: Int
    ) : PreviewClaim

    data class AlreadyHeld(
        override val cell: PreviewHexGrid.Cell,
        override val totalHeld: Int
    ) : PreviewClaim
}