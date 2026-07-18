package com.mapmonarchs.data.dto

import com.mapmonarchs.domain.model.MalformedTimestampException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Regression guard for the epoch-0 defect.
 *
 * The old mapper used getOrDefault(0L): an unparseable timestamp silently became
 * 1970, which decay maths reads as infinitely decayed -- so an owned hex would
 * render as neutral. These tests exist so that failure can never again be
 * disguised as data.
 */
class ServerTimestampParsingTest {

    @Test
    fun `parses iso8601 with Z`() {
        assertEquals(1752832800L, parseServerTimestamp("h", "2025-07-18T10:00:00Z"))
    }

    @Test
    fun `parses postgres style with space and short offset`() {
        val withSpace = parseServerTimestamp("h", "2025-07-18 10:00:00+00")
        val withT = parseServerTimestamp("h", "2025-07-18T10:00:00Z")
        assertEquals(withT, withSpace)
    }

    @Test
    fun `parses fractional seconds`() {
        assertEquals(1752832800L, parseServerTimestamp("h", "2025-07-18T10:00:00.123456Z"))
    }

    @Test
    fun `parses full offset form`() {
        assertEquals(1752832800L, parseServerTimestamp("h", "2025-07-18T10:00:00+00:00"))
    }

    @Test
    fun `garbage throws instead of silently becoming epoch zero`() {
        val e = assertThrows(MalformedTimestampException::class.java) {
            parseServerTimestamp("8928308280fffff", "not-a-timestamp")
        }
        assertEquals(true, e.message!!.contains("8928308280fffff"))
    }

    @Test
    fun `empty string throws rather than defaulting`() {
        assertThrows(MalformedTimestampException::class.java) {
            parseServerTimestamp("h", "")
        }
    }

    @Test
    fun `unparseable value never yields epoch zero`() {
        val result = runCatching { parseServerTimestamp("h", "???") }
        assertEquals(true, result.isFailure)
        assertEquals(null, result.getOrNull())
    }
}