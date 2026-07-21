package com.mapmonarchs.domain.model

/**
 * A failure that survived the trip to the UI with its cause intact.
 *
 * Every variant carries [technical]: the raw cause, kept for logs and debugging.
 * It is never rendered to the player. The point is that a failure can always be
 * explained -- there is no path in this codebase where an error becomes silence,
 * an empty list, or a fabricated default.
 */
sealed class AppError(val technical: String) {

    /** No usable network. Recoverable by the player; say so. */
    class Offline(technical: String) : AppError(technical)

    /**
     * The backend responded, but the table/function we need does not exist.
     * Right now this is the EXPECTED state: supabase/schema.sql has not been
     * applied yet. Surfacing it distinctly is what stops "the map is empty" from
     * being confused with "you own nothing".
     */
    class SchemaMissing(technical: String) : AppError(technical)

    /** RLS or auth refused us. Never silently retried -- that would mask a real policy bug. */
    class Unauthorized(technical: String) : AppError(technical)

    /**
     * The server sent something we could not parse.
     *
     * This is deliberately fatal to the read rather than tolerated. A malformed
     * timestamp feeds decay maths; defaulting it would make an owned hex compute
     * as infinitely decayed and render as neutral -- silently handing away
     * someone's territory. A visible failure is strictly better than that.
     */
    class Malformed(technical: String) : AppError(technical)

    /** Reached the backend, it failed for another reason. */
    class BackendFailure(technical: String) : AppError(technical)

    /**
     * Sign-in cannot be established -- most commonly because anonymous sign-in
     * is disabled for the project. Distinct from Unauthorized (we HAVE a session
     * and were refused) and from Unexpected (we have no idea). Kept separate so
     * a config problem never masquerades as a mystery bug.
     */
    class AuthUnavailable(technical: String) : AppError(technical)

    /** The address was rejected as malformed by the backend. */
    class InvalidEmail(technical: String) : AppError(technical)

    /** The code was wrong or has expired. The most common real failure. */
    class InvalidCode(technical: String) : AppError(technical)

    /**
     * Too many code requests. Carries [retryAfterSeconds] when the server told us
     * exactly how long -- Supabase says "you can only request this after N
     * seconds", and answering "wait a minute" threw that number away.
     *
     * Named separately because the remedy is "wait",
     * which is completely different advice from "check the code" -- and because
     * Supabase's default mailer is rate-limited to a handful per hour, so this
     * is an expected condition, not an anomaly.
     */
    class RateLimited(technical: String, val retryAfterSeconds: Int? = null) : AppError(technical)

    /**
     * The backend accepted the request but mail is not configured or did not
     * deliver. Distinct from a rejected request: the player did nothing wrong
     * and retrying will not help.
     */
    class MailNotDelivered(technical: String) : AppError(technical)

    /**
     * Genuinely unclassified. Still carries the cause; still shown.
     *
     * ITS MESSAGE MUST NOT NAME A DOMAIN. It used to read "Something went wrong
     * loading territory", which was true when this class only wrapped territory
     * reads. It is now constructed in three places and NONE of them is a
     * territory read: a persisted email-delivery failure, the generic mapper
     * fallback for any unclassified error including auth, and the location stream
     * dying. A player whose GPS had failed was being told territory would not
     * load. The class was reused and the sentence stayed put.
     */
    class Unexpected(technical: String) : AppError(technical)

    /** Short, calm, non-blaming. No stack traces, no jargon, no dead ends. */
    fun playerMessage(): String = when (this) {
        is Offline -> "No connection. Territory may be out of date."
        is SchemaMissing -> "Territory service is not deployed yet."
        is Unauthorized -> "Access refused. Sign-in may have expired."
        is Malformed -> "Territory data could not be read."
        is BackendFailure -> "Territory service error."
        is AuthUnavailable -> "Sign-in unavailable."
        is InvalidEmail -> "That email address does not look right."
        is InvalidCode -> "That code is not valid. It may have expired."
        is RateLimited -> retryAfterSeconds?.let { s ->
            // The exact interval the server gave, pluralised. Anything vaguer
            // invites the player to retry before the window clears, which just
            // extends it.
            "Too many attempts. Wait $s second" + (if (s == 1) "" else "s") + " and try again."
        } ?: "Too many attempts. Wait a minute and try again."
        is MailNotDelivered -> "The code could not be sent. Email delivery is not configured."
        is Unexpected -> "Something went wrong."
    }
}

/** Thrown when a server timestamp cannot be parsed. Names the offending row. */
class MalformedTimestampException(
    h3Index: String,
    raw: String
) : IllegalStateException("Unparseable timestamp for hex '$h3Index': '$raw'")
