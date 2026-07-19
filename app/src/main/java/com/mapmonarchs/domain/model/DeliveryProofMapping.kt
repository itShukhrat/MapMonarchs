package com.mapmonarchs.domain.model

import com.mapmonarchs.domain.repository.CodeRequest
import com.mapmonarchs.domain.repository.CodeVerification

/**
 * The single place a [DeliveryProof] is derived from a backend result.
 *
 * WHY THIS EXISTS: the rule "an accepted send request is not a delivered email"
 * was previously enforced by three hand-written call sites in MapViewModel --
 * request, resend, and verify -- each independently constructing the right
 * variant. That is a convention repeated three times, not an enforced property.
 * Add a fourth path later and write DeliveryProof.Delivered in it, and nothing
 * anywhere fails.
 *
 * This is the same shape as the bug that made the capture button and the
 * diagnostics screen disagree about which blocker was active: one rule, several
 * hand-maintained copies, no single owner. Here the stakes are higher, because
 * the wrong answer is the app claiming an email arrived when nobody ever
 * received one.
 *
 * Redaction happens INSIDE these functions rather than at the call site, so
 * forgetting it is not possible. A skipped redaction writes a real address into
 * SharedPreferences.
 */

/**
 * A send request result.
 *
 * [CodeRequest.Sent] means the backend accepted the request for sending. It says
 * nothing about whether mail left the server, reached a provider, or landed in
 * an inbox -- so it maps to [DeliveryProof.Requested] and never to
 * [DeliveryProof.Delivered]. There is deliberately no branch here that can
 * produce Delivered; see the overload below for the only thing that can.
 */
fun deliveryProofFor(
    result: CodeRequest,
    recipient: String,
    atEpochMillis: Long
): DeliveryProof = when (result) {
    is CodeRequest.Sent ->
        DeliveryProof.Requested(redactEmail(recipient), atEpochMillis)

    is CodeRequest.Failed ->
        DeliveryProof.Failed(redactEmail(recipient), result.error, atEpochMillis)
}

/**
 * A code verification result.
 *
 * [CodeVerification.Succeeded] is the ONLY evidence that mail arrived: a human
 * read a code and typed it back. This is the sole path to
 * [DeliveryProof.Delivered] in the codebase.
 *
 * Returns null for a failure, meaning "record nothing, leave the prior state
 * standing". That is a deliberate decision rather than an oversight: a rejected
 * code is most often a typo or an expiry, which proves neither that mail arrived
 * nor that it did not. Writing Failed here would blame the mail service for a
 * mistyped digit; writing Delivered would claim proof we do not have. The honest
 * response to ambiguous evidence is to leave the record alone.
 */
fun deliveryProofFor(
    result: CodeVerification,
    recipient: String,
    atEpochMillis: Long
): DeliveryProof? = when (result) {
    is CodeVerification.Succeeded ->
        DeliveryProof.Delivered(redactEmail(recipient), atEpochMillis)

    is CodeVerification.Failed -> null
}
