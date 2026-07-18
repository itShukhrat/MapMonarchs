package com.mapmonarchs.data.remote

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.MalformedTimestampException
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class ErrorMapperTest {

    @Test
    fun `unknown host is offline`() {
        assertTrue(UnknownHostException("no dns").toAppError() is AppError.Offline)
    }

    @Test
    fun `generic io is offline`() {
        assertTrue(IOException("socket closed").toAppError() is AppError.Offline)
    }

    @Test
    fun `missing relation is schema missing`() {
        val t = RuntimeException("relation \"public.hex_ownership\" does not exist")
        assertTrue(t.toAppError() is AppError.SchemaMissing)
    }

    @Test
    fun `postgrest schema cache code is schema missing`() {
        val t = RuntimeException("PGRST205: Could not find the table 'public.hex_ownership'")
        assertTrue(t.toAppError() is AppError.SchemaMissing)
    }

    @Test
    fun `missing function is schema missing`() {
        val t = RuntimeException("Could not find the function public.attempt_capture")
        assertTrue(t.toAppError() is AppError.SchemaMissing)
    }

    @Test
    fun `rls denial is unauthorized`() {
        val t = RuntimeException("new row violates row-level security policy")
        assertTrue(t.toAppError() is AppError.Unauthorized)
    }

    @Test
    fun `malformed timestamp is malformed`() {
        assertTrue(MalformedTimestampException("h", "x").toAppError() is AppError.Malformed)
    }

    @Test
    fun `anonymous sign-in disabled is explicit, not Unexpected`() {
        val t = RuntimeException("Anonymous sign-ins are disabled")
        assertTrue(t.toAppError() is AppError.AuthUnavailable)
    }

    @Test
    fun `anonymous provider not enabled is explicit`() {
        val t = RuntimeException("anonymous_provider_disabled: provider is not enabled")
        assertTrue(t.toAppError() is AppError.AuthUnavailable)
    }

    @Test
    fun `auth unavailable message does not blame the player`() {
        val msg = AppError.AuthUnavailable("anonymous disabled").playerMessage()
        assertTrue(msg.isNotBlank())
        assertTrue(!msg.contains("anonymous"))
    }

    @Test
    fun `unrecognised cause becomes Unexpected rather than a tidy wrong bucket`() {
        assertTrue(RuntimeException("something nobody predicted").toAppError() is AppError.Unexpected)
    }

    @Test
    fun `technical cause is always preserved`() {
        val e = RuntimeException("very specific detail").toAppError()
        assertTrue(e.technical.contains("very specific detail"))
    }

    @Test
    fun `player message never leaks technical detail`() {
        val e = RuntimeException("relation does not exist at line 42 of internal.sql").toAppError()
        assertTrue(!e.playerMessage().contains("internal.sql"))
        assertTrue(!e.playerMessage().contains("42"))
    }
}