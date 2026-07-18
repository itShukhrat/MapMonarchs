package com.mapmonarchs.domain.identity

import com.mapmonarchs.domain.model.Identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Identity must be DERIVED and STABLE. These tests are written so that they
 * would fail if identity were fabricated, random, or reset between launches --
 * i.e. they fail if the product is misleading, not merely if the code changes.
 */
class IdentityDerivationTest {

    private val userA = "6f2c1e4a-1111-4b2a-9c3d-000000000001"
    private val userB = "6f2c1e4a-2222-4b2a-9c3d-000000000002"

    @Test
    fun `callsign is deterministic for the same user id`() {
        assertEquals(IdentityDerivation.callsign(userA), IdentityDerivation.callsign(userA))
    }

    @Test
    fun `callsign survives a simulated restart`() {
        // A restart is just a second derivation from the same persisted id.
        val beforeRestart = Identity.from(userA)
        val afterRestart = Identity.from(userA)
        assertEquals(beforeRestart.callsign, afterRestart.callsign)
        assertEquals(beforeRestart.territoryColorArgb, afterRestart.territoryColorArgb)
    }

    @Test
    fun `distinct users do not collapse into the same callsign`() {
        assertNotEquals(IdentityDerivation.callsign(userA), IdentityDerivation.callsign(userB))
    }

    @Test
    fun `callsign is six uppercase hex characters`() {
        val c = IdentityDerivation.callsign(userA)
        assertEquals(6, c.length)
        assertTrue("not uppercase hex: $c", c.matches(Regex("[0-9A-F]{6}")))
    }

    @Test
    fun `callsign never leaks any fragment of the raw user id`() {
        // Derived from SHA-256, so no substring of the uid may appear.
        val callsign = IdentityDerivation.callsign(userA)
        val uidChunks = userA.replace("-", "").windowed(4, 1)
        uidChunks.forEach { chunk ->
            assertTrue(
                "callsign '$callsign' leaked uid fragment '$chunk'",
                !callsign.contains(chunk, ignoreCase = true)
            )
        }
    }

    @Test
    fun `territory colour is deterministic`() {
        assertEquals(
            IdentityDerivation.territoryColorArgb(userA),
            IdentityDerivation.territoryColorArgb(userA)
        )
    }

    @Test
    fun `territory colour always comes from the curated palette`() {
        // Guards against a stray random or computed HSL colour sneaking in.
        repeat(200) { i ->
            val colour = IdentityDerivation.territoryColorArgb("user-seed-$i")
            assertTrue(
                "colour $colour is not in the curated palette",
                IdentityDerivation.TERRITORY_PALETTE.contains(colour)
            )
        }
    }

    @Test
    fun `palette actually spreads across users rather than collapsing to one colour`() {
        val used = (0 until 400).map { IdentityDerivation.territoryColorArgb("seed-$it") }.toSet()
        assertTrue("expected multiple colours in use, got $used", used.size > 1)
    }

    @Test
    fun `blank user id is rejected rather than producing a placeholder identity`() {
        assertThrows(IllegalArgumentException::class.java) { IdentityDerivation.callsign("") }
        assertThrows(IllegalArgumentException::class.java) { IdentityDerivation.territoryColorArgb("  ") }
    }

    @Test
    fun `Identity from always derives both fields together`() {
        val id = Identity.from(userA)
        assertEquals(userA, id.userId)
        assertEquals(IdentityDerivation.callsign(userA), id.callsign)
        assertEquals(IdentityDerivation.territoryColorArgb(userA), id.territoryColorArgb)
    }
}