package com.mapmonarchs.data.remote

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.MalformedTimestampException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Classifies a throwable into an explainable AppError.
 *
 * Nothing is swallowed: the original message is always carried through as
 * `technical`. Unrecognised causes become Unexpected rather than being coerced
 * into a convenient-looking bucket -- a wrong-but-tidy category would hide a
 * real bug just as effectively as a silent catch.
 */
fun Throwable.toAppError(): AppError {
    val detail = "${this::class.simpleName}: ${message ?: "no message"}"
    val text = (message ?: "").lowercase()

    return when {
        this is MalformedTimestampException -> AppError.Malformed(detail)
        this is SerializationException -> AppError.Malformed(detail)

        this is UnknownHostException ||
            this is ConnectException ||
            this is SocketTimeoutException -> AppError.Offline(detail)

        // Postgrest surfaces a missing relation as 42P01, and PostgREST's schema
        // cache as PGRST205. Both mean: schema.sql has not been applied.
        text.contains("42p01") ||
            text.contains("pgrst205") ||
            text.contains("does not exist") ||
            text.contains("could not find the table") ||
            text.contains("could not find the function") -> AppError.SchemaMissing(detail)

        // Supabase reports this as anonymous_provider_disabled / "Anonymous
        // sign-ins are disabled". A project-config problem, not a mystery.
        text.contains("anonymous") &&
            (text.contains("disabled") || text.contains("not enabled")) -> AppError.AuthUnavailable(detail)

        text.contains("signup") && text.contains("disabled") -> AppError.AuthUnavailable(detail)

        // Ordered before the generic auth bucket: each of these has a DIFFERENT
        // remedy, and collapsing them would tell the player to check a code when
        // the real problem is that no mail was ever sent.
        text.contains("rate limit") ||
            text.contains("too many requests") ||
            text.contains("429") ||
            text.contains("over_email_send_rate_limit") ||
            // Supabase's real-world phrasing carries none of the tokens above.
            // A test caught this: without it, a rate-limited player would be told
            // "something went wrong" instead of "wait a minute".
            text.contains("for security purposes") ||
            text.contains("you can only request this after") -> AppError.RateLimited(detail)

        text.contains("error sending") ||
            text.contains("smtp") ||
            text.contains("mailer") -> AppError.MailNotDelivered(detail)

        text.contains("invalid_credentials") ||
            text.contains("token has expired") ||
            text.contains("otp_expired") ||
            text.contains("invalid otp") ||
            text.contains("invalid token") -> AppError.InvalidCode(detail)

        text.contains("invalid_email") ||
            text.contains("unable to validate email") ||
            text.contains("email address") && text.contains("invalid") -> AppError.InvalidEmail(detail)

        text.contains("jwt") ||
            text.contains("401") ||
            text.contains("permission denied") ||
            text.contains("row-level security") -> AppError.Unauthorized(detail)

        this is IOException -> AppError.Offline(detail)
        text.contains("50") && text.contains("server") -> AppError.BackendFailure(detail)

        else -> AppError.Unexpected(detail)
    }
}