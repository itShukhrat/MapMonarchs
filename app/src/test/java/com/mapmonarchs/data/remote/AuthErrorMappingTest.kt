package com.mapmonarchs.data.remote

import com.mapmonarchs.domain.model.AppError
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Auth failures must stay DISTINGUISHABLE.
 *
 * Each of these has a different remedy. Collapsing them into one bucket would
 * tell a player to check a code when the real problem is that no mail was ever
 * sent -- advice that wastes their time and hides a configuration fault from us.
 */
class AuthErrorMappingTest {

    @Test
    fun `rate limiting is named, because the remedy is to wait`() {
        listOf(
            "For security purposes, you can only request this after 51 seconds",
            "over_email_send_rate_limit",
            "HTTP 429 Too Many Requests"
        ).forEach {
            val mapped = RuntimeException(it).toAppError()
            assertTrue("not RateLimited: $it -> $mapped", mapped is AppError.RateLimited)
        }
    }

    @Test
    fun `mail delivery failure is distinct from a rejected request`() {
        listOf(
            "Error sending confirmation email",
            "SMTP connection failed",
            "mailer error"
        ).forEach {
            val mapped = RuntimeException(it).toAppError()
            assertTrue("not MailNotDelivered: $it -> $mapped", mapped is AppError.MailNotDelivered)
        }
    }

    @Test
    fun `a wrong or expired code is named as such`() {
        listOf(
            "Token has expired or is invalid",
            "otp_expired",
            "invalid_credentials"
        ).forEach {
            val mapped = RuntimeException(it).toAppError()
            assertTrue("not InvalidCode: $it -> $mapped", mapped is AppError.InvalidCode)
        }
    }

    @Test
    fun `a malformed address is named as such`() {
        val mapped = RuntimeException("Unable to validate email address: invalid format").toAppError()
        assertTrue("got $mapped", mapped is AppError.InvalidEmail)
    }

    @Test
    fun `anonymous disabled remains distinct from every email failure`() {
        val mapped = RuntimeException("Anonymous sign-ins are disabled").toAppError()
        assertTrue(mapped is AppError.AuthUnavailable)
    }

    @Test
    fun `every auth error gives the player different advice`() {
        // If two remedies read identically, the taxonomy is decorative.
        val messages = listOf(
            AppError.RateLimited("x"),
            AppError.MailNotDelivered("x"),
            AppError.InvalidCode("x"),
            AppError.InvalidEmail("x"),
            AppError.AuthUnavailable("x")
        ).map { it.playerMessage() }
        assertTrue("duplicate advice: $messages", messages.size == messages.toSet().size)
    }

    @Test
    fun `technical detail is preserved but never shown to the player`() {
        val e = RuntimeException("otp_expired at internal/auth/otp.go:412").toAppError()
        assertTrue(e.technical.contains("otp.go"))
        assertTrue(!e.playerMessage().contains("otp.go"))
        assertTrue(!e.playerMessage().contains("412"))
    }

    @Test
    fun `a rate limit message threads the exact interval end to end`() {
        val e = RuntimeException(
            "For security purposes, you can only request this after 47 seconds."
        ).toAppError()
        assertTrue("classified as rate limit", e is AppError.RateLimited)
        val seconds = (e as AppError.RateLimited).retryAfterSeconds
        assertTrue("the 47 must survive from raw message to typed field; got $seconds", seconds == 47)
        assertTrue("and reach the player: ${e.playerMessage()}", e.playerMessage().contains("47 seconds"))
    }

    @Test
    fun `a tokenless rate limit carries no invented interval`() {
        val e = RuntimeException("HTTP 429 Too Many Requests").toAppError()
        assertTrue("still classified as rate limit", e is AppError.RateLimited)
        assertTrue(
            "no number in the message means no countdown fabricated",
            (e as AppError.RateLimited).retryAfterSeconds == null
        )
    }

    @Test
    fun `the parser ignores digits that are not a seconds count`() {
        // An error code or byte count must never be shown as a wait time.
        assertEquals(null, parseRetryAfterSeconds("error 42901: over_email_send_rate_limit"))
        assertEquals(30, parseRetryAfterSeconds("try again in 30 seconds"))
    }
}
