package com.mapmonarchs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryProofTest {

    @Test
    fun `never attempted and unknown are different facts`() {
        // "nobody has tried" and "something happened and we lost the record" are
        // opposite claims. Collapsing them would let a corrupted record read as a
        // clean slate.
        val neverAttempted: DeliveryProof = DeliveryProof.NeverAttempted
        val unknown: DeliveryProof = DeliveryProof.Unknown
        assertNotEquals(neverAttempted, unknown)
    }

    @Test
    fun `an accepted send request is not proof of delivery`() {
        val requested: DeliveryProof = DeliveryProof.Requested("e***@example.com", 1_700_000_000_000)
        assertTrue(
            "Requested must never be treated as Delivered -- a 200 from the mail " +
                "API says the request was accepted, not that anything arrived",
            requested !is DeliveryProof.Delivered
        )
    }

    @Test
    fun `redaction keeps the domain and hides the mailbox`() {
        val redacted = redactEmail("elmurodovshuhrat0808@gmail.com")
        assertEquals("e***@gmail.com", redacted)
        assertTrue("must not contain the full local part", !redacted.contains("shuhrat"))
    }

    @Test
    fun `redaction handles a single-character mailbox`() {
        assertEquals("*@example.com", redactEmail("a@example.com"))
    }

    @Test
    fun `redaction of a malformed address does not leak it`() {
        assertEquals("***", redactEmail("not-an-address"))
        assertEquals("***", redactEmail("@leading.com"))
    }

    @Test
    fun `redaction trims surrounding whitespace before redacting`() {
        assertEquals("b***@host.io", redactEmail("  bob@host.io  "))
    }
}
