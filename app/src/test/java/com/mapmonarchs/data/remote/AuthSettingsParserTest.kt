package com.mapmonarchs.data.remote

import com.mapmonarchs.domain.model.CapabilitiesProbe
import com.mapmonarchs.domain.model.signInPossible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSettingsParserTest {

    /**
     * The verbatim body returned by this project's /auth/v1/settings endpoint,
     * captured from a live request. Kept whole rather than trimmed to the three
     * fields we read: a hand-minimised fixture would not have caught the shape of
     * the real payload, where `email` sits inside `external` while
     * `mailer_autoconfirm` sits at the root.
     */
    private val liveBody = """
        {"external":{"anonymous_users":false,"apple":false,"azure":false,"bitbucket":false,
        "discord":false,"facebook":false,"snapchat":false,"figma":false,"fly":false,
        "github":false,"gitlab":false,"google":false,"keycloak":false,"kakao":false,
        "linkedin":false,"linkedin_oidc":false,"notion":false,"spotify":false,"slack":false,
        "slack_oidc":false,"workos":false,"twitch":false,"twitter":false,"email":true,
        "phone":false,"zoom":false},"disable_signup":false,"mailer_autoconfirm":false,
        "phone_autoconfirm":false,"sms_provider":"twilio","saml_enabled":false,
        "saml_private_key_next_configured":true,"passkeys_enabled":false}
    """.trimIndent().replace("\n", "")

    @Test
    fun `parses the real payload this project returns`() {
        val probe = AuthSettingsParser.parse(liveBody)
        val observed = probe as? CapabilitiesProbe.Observed
            ?: error("expected Observed, got $probe")

        assertTrue("email auth is enabled on this project", observed.capabilities.emailEnabled)
        assertTrue("anonymous auth is disabled", !observed.capabilities.anonymousEnabled)
        assertTrue(
            "accounts are not auto-confirmed, so delivery is load-bearing",
            !observed.capabilities.mailerAutoconfirm
        )
    }

    @Test
    fun `sign-in is possible when email is enabled`() {
        assertEquals(true, AuthSettingsParser.parse(liveBody).signInPossible())
    }

    @Test
    fun `unknown capabilities never claim sign-in is possible or impossible`() {
        assertNull(CapabilitiesProbe.NotProbed.signInPossible())
        assertNull(CapabilitiesProbe.Probing.signInPossible())
        assertNull(AuthSettingsParser.parse("not json at all").signInPossible())
    }

    @Test
    fun `sign-in is impossible when every provider is off`() {
        val body = """{"external":{"email":false,"anonymous_users":false},"mailer_autoconfirm":false}"""
        assertEquals(false, AuthSettingsParser.parse(body).signInPossible())
    }

    // --- the whole point: absence must never become `false` ------------------

    @Test
    fun `a missing provider field is malformed, not disabled`() {
        val body = """{"external":{"anonymous_users":false},"mailer_autoconfirm":false}"""
        val probe = AuthSettingsParser.parse(body)
        assertTrue(
            "a missing 'email' key must not silently read as DISABLED, got $probe",
            probe is CapabilitiesProbe.Unavailable
        )
    }

    @Test
    fun `a missing external object is malformed`() {
        assertTrue(
            AuthSettingsParser.parse("""{"mailer_autoconfirm":false}""")
                is CapabilitiesProbe.Unavailable
        )
    }

    @Test
    fun `a non-boolean provider value is malformed`() {
        val body = """{"external":{"email":"yes","anonymous_users":false},"mailer_autoconfirm":false}"""
        assertTrue(AuthSettingsParser.parse(body) is CapabilitiesProbe.Unavailable)
    }

    @Test
    fun `unparseable input reports why rather than throwing`() {
        val probe = AuthSettingsParser.parse("<html>502 Bad Gateway</html>")
        val unavailable = probe as? CapabilitiesProbe.Unavailable
            ?: error("expected Unavailable, got $probe")
        assertTrue(unavailable.error.technical.isNotBlank())
    }

    @Test
    fun `unrelated fields changing does not break the parse`() {
        val body = """{"external":{"email":true,"anonymous_users":false,"brand_new_provider":true},
            "mailer_autoconfirm":true,"some_future_field":{"nested":1}}""".trimIndent()
            .replace("\n", "")
        val observed = AuthSettingsParser.parse(body) as? CapabilitiesProbe.Observed
            ?: error("expected Observed")
        assertTrue(observed.capabilities.emailEnabled)
        assertTrue(observed.capabilities.mailerAutoconfirm)
    }
}
