package com.mapmonarchs.feature.map

import com.mapmonarchs.domain.model.CaptureOutcome
import com.mapmonarchs.domain.validation.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server-outcome -> client-state mapping, the one genuinely fiddly part of
 * the capture wiring, pinned on its own. This is why the ViewModel wiring can be
 * trivial: the mapping is proven here, exhaustively, without a ViewModel.
 */
class CaptureOutcomeMappingTest {

    @Test
    fun `applied becomes Succeeded carrying the server action`() {
        val s = CaptureOutcome.Applied("891e0d4a1abffff", "captured", "uid").toCaptureState()
        assertEquals(CaptureState.Succeeded("captured"), s)
    }

    @Test
    fun `every movement rejection maps to the matching RejectionReason`() {
        // The server reason strings are the lower-case of the enum names, so all
        // four must map straight across. If a new RejectionReason is added, this
        // loop fails until the server string and the mapping agree.
        RejectionReason.entries.forEach { reason ->
            val serverString = reason.name.lowercase()
            val s = CaptureOutcome.Rejected(serverString).toCaptureState()
            assertEquals("$serverString should map to Blocked($reason)", CaptureState.Blocked(reason), s)
        }
    }

    @Test
    fun `a rejection that is not a movement verdict becomes Failed, not a forced RejectionReason`() {
        // not_authenticated and no_cell are session or server faults; forcing them
        // into a RejectionReason would misreport them to the player.
        listOf("not_authenticated", "no_cell", "something_new").forEach {
            val s = CaptureOutcome.Rejected(it).toCaptureState()
            assertTrue("$it should be Failed, was $s", s is CaptureState.Failed)
        }
    }

    @Test
    fun `an outright failure becomes Failed`() {
        assertTrue(CaptureOutcome.Failed("network").toCaptureState() is CaptureState.Failed)
    }
}
