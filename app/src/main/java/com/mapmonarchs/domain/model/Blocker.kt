package com.mapmonarchs.domain.model

import com.mapmonarchs.domain.validation.RejectionReason

/**
 * The single answer to "why can't I claim right now?"
 *
 * WHY THIS TYPE EXISTS: this question was previously answered twice -- once by
 * the capture button label and once by the diagnostics screen -- by two
 * hand-maintained `when` chains over the same state. They drifted, exactly as
 * duplicated orderings always do:
 *
 *  - they disagreed on precedence, so a signed-out player with no location
 *    permission read "NO IDENTITY" on the HUD and "Location permission is not
 *    granted" in diagnostics, and fixed the wrong thing first;
 *  - the diagnostics chain never handled [Identity.SignedOut] at all, so it fell
 *    through to a default that said "nothing is blocking" while the capture
 *    button sat disabled. The one screen whose entire job is explaining the
 *    block was confidently denying there was one.
 *
 * Both bugs share a mechanism: a chain of `is` checks ending in `else` looks
 * exhaustive and is not. Returning a sealed type instead of a String moves that
 * from a thing you must remember to a thing the resolver is tested for.
 *
 * ORDERING IS DECIDED HERE, ONCE. Every surface renders this; no surface
 * re-derives it.
 */
sealed interface Blocker {

    /**
     * Nothing stands in the way of the action currently offered.
     *
     * Reachable only in preview, where claiming genuinely works because it is
     * simulated locally. Outside preview [CaptureNotDeployed] is terminal --
     * there is no server function to claim against, so "nothing is blocking you"
     * is never true and is not representable.
     */
    data object None : Blocker

    data object LocationPermissionMissing : Blocker

    data object AcquiringFix : Blocker

    data class FixRejected(val reason: RejectionReason) : Blocker

    data object IdentityResolving : Blocker

    data class IdentityUnavailable(val error: AppError) : Blocker

    /** No session. Previously fell through the diagnostics chain entirely. */
    data object NotSignedIn : Blocker

    /**
     * The schema gate, named specifically rather than as a vague "disabled".
     *
     * NOTE ON WHAT IS NOT HERE: a failed territory READ is not a blocker. Reading
     * ownership and writing a capture are independent axes -- the map being stale
     * does not stop the server arbitrating a claim -- and MapUiState documents
     * that independence. Territory health has its own row in diagnostics.
     */
    data object CaptureNotDeployed : Blocker

    /** Short, upper-case, for the capture button. Never punctuated. */
    fun hudLabel(): String = when (this) {
        is None -> "CLAIM HEX"
        is LocationPermissionMissing -> "LOCATION OFF"
        is AcquiringFix -> "ACQUIRING GPS"
        is FixRejected -> when (reason) {
            RejectionReason.MOCK_LOCATION -> "MOCK LOCATION"
            RejectionReason.LOW_ACCURACY -> "GPS TOO WEAK"
            RejectionReason.TOO_FAST -> "MOVING TOO FAST"
            RejectionReason.TELEPORT -> "SIGNAL JUMPED"
        }
        is IdentityResolving -> "IDENTIFYING"
        is IdentityUnavailable -> "NO IDENTITY"
        is NotSignedIn -> "SIGN IN TO CLAIM"
        is CaptureNotDeployed -> "CLAIMING OFFLINE"
    }

    /**
     * Player-facing prose for diagnostics. States the cause and the remedy --
     * a blocker the player cannot act on is just an apology.
     */
    fun explanation(): String = when (this) {
        is None ->
            "Nothing is blocking the action available to you right now."

        is LocationPermissionMissing ->
            "Location permission is not granted, so the app cannot tell which hex you " +
                "are standing in. Grant it in SETTINGS."

        is AcquiringFix ->
            "Waiting for a position reading accurate enough to act on. This usually " +
                "clears within a few seconds outdoors."

        is FixRejected -> when (reason) {
            RejectionReason.MOCK_LOCATION ->
                "Your device is reporting a simulated location. Turn off mock location " +
                    "in developer options to claim ground."
            RejectionReason.LOW_ACCURACY ->
                "Your last position reading was too imprecise to place you in a hex. " +
                    "Open sky helps; indoors and dense streets hurt."
            RejectionReason.TOO_FAST ->
                "You are moving too quickly for this to count as walking. Territory is " +
                    "earned on foot."
            RejectionReason.TELEPORT ->
                "Your position jumped further than you could have walked. The reading " +
                    "was discarded rather than trusted."
        }

        is IdentityResolving ->
            "Still establishing your session."

        is IdentityUnavailable ->
            "Sign-in is unavailable, so the server cannot attribute territory to you. " +
                error.playerMessage()

        is NotSignedIn ->
            "You are not signed in, so the server has no one to attribute territory to. " +
                "Sign in with an email code to claim ground."


        is CaptureNotDeployed ->
            "The server function that decides ownership has not been deployed yet, so " +
                "no client can claim ground -- including a modified one. This is a " +
                "backend deployment step, not something you can fix from the app."
    }
}
