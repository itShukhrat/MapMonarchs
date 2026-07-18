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

        text.contains("jwt") ||
            text.contains("401") ||
            text.contains("permission denied") ||
            text.contains("row-level security") -> AppError.Unauthorized(detail)

        this is IOException -> AppError.Offline(detail)
        text.contains("50") && text.contains("server") -> AppError.BackendFailure(detail)

        else -> AppError.Unexpected(detail)
    }
}