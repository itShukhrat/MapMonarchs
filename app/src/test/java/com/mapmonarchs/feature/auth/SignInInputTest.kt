package com.mapmonarchs.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local input checks.
 *
 * These exist to avoid pointless round-trips, NOT to validate. The backend is
 * the authority on whether an address is real; being clever here would only
 * reject addresses that actually work (plus-addressing, new TLDs, unicode).
 */
class SignInInputTest {

    @Test
    fun `ordinary addresses are accepted`() {
        listOf(
            "a@b.co",
            "shukhrat@example.com",
            "first.last@sub.domain.org",
            "user+tag@gmail.com",
            "someone@a-very-new-tld.technology"
        ).forEach { assertTrue("rejected a valid address: $it", it.looksLikeEmail()) }
    }

    @Test
    fun `obvious non-addresses are rejected before a round-trip`() {
        listOf("", "  ", "nope", "@nope.com", "a@b", "a@b.", "two words@x.com", "a b@c.com")
            .forEach { assertFalse("accepted an invalid address: '$it'", it.looksLikeEmail()) }
    }

    @Test
    fun `whitespace around an address does not reject it`() {
        assertTrue("  shukhrat@example.com  ".looksLikeEmail())
    }

    @Test
    fun `a six digit code is accepted`() {
        assertTrue("123456".looksLikeCode())
        assertTrue(" 000000 ".looksLikeCode())
    }

    @Test
    fun `codes of the wrong shape are rejected`() {
        listOf("", "12345", "1234567", "12345a", "abcdef", "12 456")
            .forEach { assertFalse("accepted a bad code: '$it'", it.looksLikeCode()) }
    }
}