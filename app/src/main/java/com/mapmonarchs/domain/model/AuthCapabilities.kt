package com.mapmonarchs.domain.model

/**
 * What the backend actually allows, as reported by the backend.
 *
 * WHY THIS EXISTS: diagnostics previously printed "Sign-in method: EMAIL CODE"
 * and "Anonymous sign-in: DISABLED" as string literals. Those were true when a
 * human ran a probe once. They would have gone on being printed with total
 * confidence the moment anyone flipped a toggle in the Supabase dashboard -- on
 * the one screen whose entire purpose is telling the truth about the backend.
 *
 * A fact that can change is not a constant. It is either observed or unknown.
 */
data class AuthCapabilities(
    val emailEnabled: Boolean,
    val anonymousEnabled: Boolean,
    /**
     * When false, a signup is not auto-confirmed, which means a code must
     * genuinely arrive in an inbox before anyone can sign in. That makes mail
     * delivery load-bearing rather than incidental, so it is worth surfacing.
     */
    val mailerAutoconfirm: Boolean
)

/**
 * The probe result. [Unavailable] is a first-class outcome, not an excuse to
 * fall back to a hardcoded guess -- an unreachable backend tells us nothing
 * about its configuration, and saying "UNKNOWN" is the only honest report.
 */
sealed interface CapabilitiesProbe {
    data object NotProbed : CapabilitiesProbe
    data object Probing : CapabilitiesProbe
    data class Observed(val capabilities: AuthCapabilities) : CapabilitiesProbe
    data class Unavailable(val error: AppError) : CapabilitiesProbe
}

/**
 * Can a sign-in even complete in this environment? Derived strictly from what
 * was observed; never optimistic.
 */
fun CapabilitiesProbe.signInPossible(): Boolean? = when (this) {
    is CapabilitiesProbe.Observed -> capabilities.emailEnabled || capabilities.anonymousEnabled
    // Not "false". We do not know, and guessing either way would be a claim we
    // cannot support.
    else -> null
}
