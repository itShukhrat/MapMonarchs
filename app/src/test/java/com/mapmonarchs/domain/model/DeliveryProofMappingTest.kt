package com.mapmonarchs.domain.model

import com.mapmonarchs.domain.repository.CodeRequest
import com.mapmonarchs.domain.repository.CodeVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule this whole design rests on: an accepted send request is not a
 * delivered email.
 *
 * It used to be enforced by typing the correct variant at three separate call
 * sites. These tests exist so it is enforced by the compiler and this file
 * instead -- a fourth call site cannot get it wrong, because there is only one
 * place left that decides.
 */
class DeliveryProofMappingTest {

    private val at = 1_700_000_000_000
    private val address = "elmurodovshuhrat0808@gmail.com"

    // --- a request is never delivery ----------------------------------------

    @Test
    fun `an accepted send request records Requested, not Delivered`() {
        val proof = deliveryProofFor(CodeRequest.Sent, address, at)
        assertTrue("a 200 means accepted for sending, nothing more", proof is DeliveryProof.Requested)
        assertEquals(at, (proof as DeliveryProof.Requested).atEpochMillis)
    }

    /**
     * The load-bearing assertion. Exhaustive over every CodeRequest variant: no
     * request outcome may ever produce Delivered.
     */
    @Test
    fun `no send-request outcome can ever produce Delivered`() {
        val outcomes = listOf(
            CodeRequest.Sent,
            CodeRequest.Failed(AppError.MailNotDelivered("smtp not configured")),
            CodeRequest.Failed(AppError.RateLimited("wait 51 seconds")),
            CodeRequest.Failed(AppError.InvalidEmail("malformed")),
            CodeRequest.Failed(AppError.Offline("no network"))
        )
        outcomes.forEach { outcome ->
            val proof = deliveryProofFor(outcome, address, at)
            assertTrue(
                "a request outcome must never claim delivery, got $proof for $outcome",
                proof !is DeliveryProof.Delivered
            )
        }
    }

    @Test
    fun `a refused send records Failed and keeps the specific cause`() {
        val error = AppError.MailNotDelivered("smtp not configured")
        val proof = deliveryProofFor(CodeRequest.Failed(error), address, at)
        val failed = proof as? DeliveryProof.Failed ?: error("expected Failed, got $proof")
        assertEquals(
            "the cause must survive so diagnostics can name it",
            error,
            failed.error
        )
    }

    @Test
    fun `a rate-limited send is Failed with the rate-limit cause intact`() {
        val error = AppError.RateLimited("for security purposes, you can only request this after 51 seconds")
        val proof = deliveryProofFor(CodeRequest.Failed(error), address, at)
        assertTrue((proof as DeliveryProof.Failed).error is AppError.RateLimited)
        assertTrue(
            "the player must be told to wait rather than to retry",
            proof.error.playerMessage().contains("Wait", ignoreCase = true)
        )
    }

    // --- only a typed-back code proves delivery ------------------------------

    @Test
    fun `a verified code is the only thing that records Delivered`() {
        val identity = Identity.from("6f2c1e4a-1111-4b2a-9c3d-000000000001")
        val proof = deliveryProofFor(CodeVerification.Succeeded(identity), address, at)
        assertTrue("a human read a code and typed it back", proof is DeliveryProof.Delivered)
        assertEquals(at, (proof as DeliveryProof.Delivered).atEpochMillis)
    }

    /**
     * A rejected code is ambiguous evidence and must not be turned into a claim.
     * Recording Failed would blame the mail service for a mistyped digit;
     * recording Delivered would assert proof we do not have.
     */
    @Test
    fun `a rejected code records nothing and leaves the prior state standing`() {
        assertNull(
            deliveryProofFor(CodeVerification.Failed(AppError.InvalidCode("expired")), address, at)
        )
        assertNull(
            deliveryProofFor(CodeVerification.Failed(AppError.Offline("no network")), address, at)
        )
    }

    // --- redaction cannot be skipped ----------------------------------------

    @Test
    fun `the raw address never reaches the persisted record`() {
        val proofs = listOfNotNull(
            deliveryProofFor(CodeRequest.Sent, address, at),
            deliveryProofFor(CodeRequest.Failed(AppError.Offline("x")), address, at),
            deliveryProofFor(
                CodeVerification.Succeeded(Identity.from("6f2c1e4a-1111-4b2a-9c3d-000000000001")),
                address,
                at
            )
        )
        proofs.forEach { proof ->
            val hint = when (proof) {
                is DeliveryProof.Requested -> proof.recipientHint
                is DeliveryProof.Delivered -> proof.recipientHint
                is DeliveryProof.Failed -> proof.recipientHint
                else -> error("unexpected $proof")
            }
            assertEquals("e***@gmail.com", hint)
            assertTrue("must not contain the mailbox", !hint.contains("shuhrat"))
        }
    }

    @Test
    fun `redaction happens inside the mapping so a caller cannot forget it`() {
        // Callers pass the RAW address. If redaction moved back to the call site,
        // this would return the address verbatim.
        val proof = deliveryProofFor(CodeRequest.Sent, "  Bob@Host.io  ", at)
        assertEquals("B***@Host.io", (proof as DeliveryProof.Requested).recipientHint)
    }
}
