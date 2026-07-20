package com.mapmonarchs.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words a player reads when something has already gone wrong.
 *
 * These are not style assertions. Each one pins a property that was violated by
 * copy which had drifted away from the code it described.
 */
class AppErrorMessageTest {

    private val all: List<AppError> = listOf(
        AppError.Offline("x"),
        AppError.SchemaMissing("x"),
        AppError.Unauthorized("x"),
        AppError.Malformed("x"),
        AppError.BackendFailure("x"),
        AppError.AuthUnavailable("x"),
        AppError.InvalidEmail("x"),
        AppError.InvalidCode("x"),
        AppError.RateLimited("x"),
        AppError.MailNotDelivered("x"),
        AppError.Unexpected("x")
    )

    /**
     * THE REGRESSION THIS FILE EXISTS FOR.
     *
     * Unexpected's message read "Something went wrong loading territory" -- true
     * when the class only wrapped territory reads. It is now constructed in three
     * places and none of them is one: a persisted email-delivery failure, the
     * mapper's fallback for any unclassified error including auth, and the
     * location stream dying. A player whose GPS had failed was told territory
     * would not load.
     *
     * An unclassified error cannot name a domain, because it does not know one.
     */
    @Test
    fun `the unclassified error names no domain it cannot know`() {
        val message = AppError.Unexpected("boom").playerMessage().lowercase()
        listOf("territory", "location", "email", "sign-in", "map", "code").forEach { domain ->
            assertTrue(
                "Unexpected is raised across domains, so it must not claim '$domain'; got: $message",
                !message.contains(domain)
            )
        }
    }

    /**
     * The map and diagnostics describe the SAME state, so they must not use two
     * different words for it. Diagnostics renders SchemaMissing as "NOT DEPLOYED"
     * while the error strip said "unavailable" -- which additionally implies a
     * transient outage, next to a RETRY button that can never succeed.
     */
    @Test
    fun `a missing schema reads as undeployed rather than as an outage`() {
        val message = AppError.SchemaMissing("42P01").playerMessage().lowercase()
        assertTrue("should say it is not deployed; got: $message", message.contains("not deployed"))
        assertTrue(
            "must not imply a transient outage the player can wait out; got: $message",
            !message.contains("unavailable")
        )
    }

    @Test
    fun `no player message leaks technical detail`() {
        val technical = "PGRST202 relation \"hex_ownership\" does not exist at line 42"
        listOf(
            AppError.SchemaMissing(technical),
            AppError.Unexpected(technical),
            AppError.BackendFailure(technical),
            AppError.Malformed(technical)
        ).forEach { error ->
            val message = error.playerMessage()
            assertTrue("leaked code in: $message", !message.contains("PGRST202"))
            assertTrue("leaked identifier in: $message", !message.contains("hex_ownership"))
        }
    }

    @Test
    fun `every message is non-empty, sentence-cased and punctuated`() {
        all.forEach { error ->
            val m = error.playerMessage()
            assertTrue("empty message for $error", m.isNotBlank())
            assertTrue("should not shout at the player: $m", m != m.uppercase())
            assertTrue("should read as a sentence: $m", m.trimEnd().endsWith("."))
        }
    }

    /**
     * Two failures that read identically are two failures a player cannot tell
     * apart, which defeats the point of having separate types at all.
     */
    @Test
    fun `no two errors share a message`() {
        val messages = all.map { it.playerMessage() }
        assertTrue(
            "distinct error types must read distinctly",
            messages.size == messages.toSet().size
        )
    }

    /**
     * Rate limiting must tell the player to WAIT. Anything that reads as a
     * generic failure invites the immediate retry that is guaranteed to fail
     * again and extend the limit.
     */
    @Test
    fun `rate limiting advises waiting rather than retrying`() {
        val m = AppError.RateLimited("for security purposes you can only request this after 51 seconds")
            .playerMessage().lowercase()
        assertTrue("must tell the player to wait; got: $m", m.contains("wait"))
    }
}
