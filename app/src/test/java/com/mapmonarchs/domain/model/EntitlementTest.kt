package com.mapmonarchs.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests exist to make one specific mistake loud.
 *
 * The tempting shortcut, the day someone wants to see the Pro surfaces, is to
 * grant Pro locally -- a debug flag, a build-type check, a preference. That
 * produces a player who believes they own something no server ever issued, which
 * is the same class of defect as drawing territory the backend never confirmed:
 * the app asserting a fact it cannot support.
 *
 * So the invariant is not "Pro is currently false". It is "this build has no way
 * to become Pro at all", and it must fail the moment someone wires one up
 * without a server that can verify it.
 */
class EntitlementTest {

    @Test
    fun theOnlyShippedResolverCannotProduceAPaidEntitlement() {
        // Called repeatedly on purpose: a resolver that flipped after first use
        // (a cached purchase, a lazily-read flag) would still pass a single call.
        repeat(5) {
            assertSame(
                "NoBillingEntitlements granted a paid tier; this build has no way to verify one",
                Entitlement.Free,
                NoBillingEntitlements.current()
            )
        }
    }

    @Test
    fun purchaseIsAdvertisedAsUnavailableWhileThereIsNoBilling() {
        // The gated UI renders a DIFFERENT sentence depending on this flag:
        // "upgrade to unlock" versus "cannot be bought yet". Flipping it on
        // without a purchase flow turns honest copy into a dead upsell.
        assertFalse(
            "PURCHASE_AVAILABLE is true, but nothing in this build can take money",
            Entitlement.PURCHASE_AVAILABLE
        )
    }

    @Test
    fun freeIsNotMistakenForPro() {
        assertFalse(Entitlement.Free.isPro())
        assertTrue(Entitlement.Pro.isPro())
    }

    @Test
    fun theResolverSeamIsSubstitutableSoBillingIsADropIn() {
        // The whole point of the interface: when Play Billing lands it replaces
        // the resolver and no gated screen changes. If this stops compiling or
        // stops returning Pro, the seam has been narrowed and every gated
        // surface will need editing instead.
        val verifiedByServer = Entitlement.Resolver { Entitlement.Pro }
        assertEquals(Entitlement.Pro, verifiedByServer.current())
        assertTrue(verifiedByServer.current().isPro())
    }
}
