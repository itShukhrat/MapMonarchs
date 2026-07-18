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

    /** Genuinely unclassified. Still carries the cause; still shown. */
    class Unexpected(technical: String) : AppError(technical)

    /** Short, calm, non-blaming. No stack traces, no jargon, no dead ends. */
    fun playerMessage(): String = when (this) {
        is Offline -> "No connection. Territory may be out of date."
        is SchemaMissing -> "Territory service unavailable."
        is Unauthorized -> "Access refused. Sign-in may have expired."
        is Malformed -> "Territory data could not be read."
        is BackendFailure -> "Territory service error."
        is AuthUnavailable -> "Sign-in unavailable."
        is Unexpected -> "Something went wrong loading territory."
    }
}

/** Thrown when a server timestamp cannot be parsed. Names the offending row. */
class MalformedTimestampException(
    h3Index: String,
    raw: String
) : IllegalStateException("Unparseable timestamp for hex '$h3Index': '$raw'")