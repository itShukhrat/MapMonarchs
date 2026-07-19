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
     * Whether signups are auto-confirmed. Informational only: it does NOT remove
     * the need for mail in an OTP flow, because the player must still receive and
     * type a code. Reported because it is a useful signal about how the project
     * is configured, not because it makes delivery optional.
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
 * Whether sign-in can actually COMPLETE in this environment.
 *
 * The distinction this enum exists to make: an enabled provider is not a working
 * provider. Diagnostics briefly answered this question with "YES" purely because
 * `external.email` was true -- while, three lines lower on the same screen, it
 * explained that no code had ever been delivered and delivery was untested.
 * Those two statements cannot both be the top-line answer.
 *
 * Email OTP requires a code to physically arrive, every time. So an email-only
 * project with unproven delivery is [UNPROVEN], never [POSSIBLE].
 */
enum class SignInReadiness(val label: String) {
    /** The backend was not reachable, so nothing is known. */
    UNKNOWN("UNKNOWN"),

    /** Observed, and no provider is enabled. Sign-in cannot happen at all. */
    NO_PROVIDER("NO"),

    /** A provider exists but depends on mail that has never been seen to arrive. */
    UNPROVEN("UNPROVEN"),

    /** Sign-in has been demonstrated to work, or needs no mail at all. */
    POSSIBLE("YES")
}

/**
 * Combines what the backend permits with what this device has actually seen.
 * Neither input alone answers the question.
 */
fun signInReadiness(probe: CapabilitiesProbe, delivery: DeliveryProof): SignInReadiness {
    val capabilities = (probe as? CapabilitiesProbe.Observed)?.capabilities
        ?: return SignInReadiness.UNKNOWN

    // Anonymous needs no mail, so enabling it makes sign-in possible outright.
    if (capabilities.anonymousEnabled) return SignInReadiness.POSSIBLE

    if (!capabilities.emailEnabled) return SignInReadiness.NO_PROVIDER

    // Email is the only route, so the question collapses to: has a code ever
    // actually arrived here?
    return if (delivery is DeliveryProof.Delivered) SignInReadiness.POSSIBLE
    else SignInReadiness.UNPROVEN
}
