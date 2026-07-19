package com.mapmonarchs.data.remote

import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.AuthCapabilities
import com.mapmonarchs.domain.model.CapabilitiesProbe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses GET /auth/v1/settings.
 *
 * Deliberately free of android.util.Log so it can be unit-tested without
 * `returnDefaultValues`, which globally stubs the Android framework and would
 * quietly neuter every other assertion in the suite.
 *
 * THE RULE HERE: a missing or non-boolean field is MALFORMED, never `false`.
 * Defaulting would let an API change silently report "anonymous sign-in:
 * DISABLED" forever with total confidence -- which is exactly the failure this
 * probe was built to eliminate. Getting it wrong quietly would be worse than the
 * hardcoded string it replaced, because it would look like it had been checked.
 */
object AuthSettingsParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): CapabilitiesProbe = try {
        val root = json.parseToJsonElement(body).jsonObject
        val external = root["external"]?.jsonObject
            ?: error("missing 'external' object")

        CapabilitiesProbe.Observed(
            AuthCapabilities(
                emailEnabled = external.requireBoolean("email"),
                anonymousEnabled = external.requireBoolean("anonymous_users"),
                mailerAutoconfirm = root.requireBoolean("mailer_autoconfirm")
            )
        )
    } catch (t: Throwable) {
        CapabilitiesProbe.Unavailable(
            AppError.Malformed("auth settings unparseable: ${t.message ?: t::class.simpleName}")
        )
    }

    private fun JsonObject.requireBoolean(key: String): Boolean {
        val element = this[key] ?: error("missing '$key'")
        val raw = element.jsonPrimitive.content
        return raw.toBooleanStrictOrNull() ?: error("'$key' is not a boolean: $raw")
    }
}
