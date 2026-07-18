package com.mapmonarchs.domain.validation

import com.mapmonarchs.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure JVM tests -- no device, no emulator, milliseconds to run.
 *
 * These assert the client-side plausibility filter only. They are NOT a security
 * guarantee: the same rules are enforced independently in attempt_capture(), and
 * that server-side enforcement needs its own pgTAP tests.
 */
class MovementValidatorTest {

    private val validator = MovementValidator()

    private fun fix(
        lat: Double = 41.2995,
        lng: Double = 69.2401,
        accuracy: Float? = 10f,
        speed: Float? = 1.2f,
        isMock: Boolean = false,
        elapsed: Long = 0L
    ) = LocationFix(lat, lng, accuracy, speed, isMock, elapsed)

    @Test
    fun `a normal walking fix is plausible`() {
        assertEquals(MovementVerdict.Plausible, validator.validate(fix(), null))
    }

    @Test
    fun `mock location is rejected`() {
        val verdict = validator.validate(fix(isMock = true), null)
        assertEquals(MovementVerdict.Implausible(RejectionReason.MOCK_LOCATION), verdict)
    }

    @Test
    fun `mock location is rejected even when everything else looks perfect`() {
        val verdict = validator.validate(fix(accuracy = 1f, speed = 1f, isMock = true), null)
        assertEquals(MovementVerdict.Implausible(RejectionReason.MOCK_LOCATION), verdict)
    }

    @Test
    fun `poor accuracy is rejected`() {
        val verdict = validator.validate(fix(accuracy = 45f), null)
        assertEquals(MovementVerdict.Implausible(RejectionReason.LOW_ACCURACY), verdict)
    }

    @Test
    fun `accuracy exactly at the limit is accepted`() {
        val verdict = validator.validate(fix(accuracy = MovementRules.MAX_ACCURACY_METERS), null)
        assertEquals(MovementVerdict.Plausible, verdict)
    }

    @Test
    fun `null accuracy does not reject - absence of data is not evidence of cheating`() {
        assertEquals(MovementVerdict.Plausible, validator.validate(fix(accuracy = null), null))
    }

    @Test
    fun `driving speed is rejected`() {
        val verdict = validator.validate(fix(speed = 20f), null)
        assertEquals(MovementVerdict.Implausible(RejectionReason.TOO_FAST), verdict)
    }

    @Test
    fun `teleport between two fixes is rejected`() {
        val previous = fix(lat = 41.2995, lng = 69.2401, elapsed = 0L)
        // ~1.5 km away, one second later.
        val current = fix(lat = 41.3130, lng = 69.2401, elapsed = 1_000L)
        val verdict = validator.validate(current, previous)
        assertEquals(MovementVerdict.Implausible(RejectionReason.TELEPORT), verdict)
    }

    @Test
    fun `a realistic walk between two fixes is plausible`() {
        val previous = fix(lat = 41.29950, lng = 69.24010, elapsed = 0L)
        // ~11 m in 10 s => ~1.1 m/s, ordinary walking pace.
        val current = fix(lat = 41.29960, lng = 69.24010, elapsed = 10_000L)
        assertEquals(MovementVerdict.Plausible, validator.validate(current, previous))
    }

    @Test
    fun `identical timestamps cannot imply speed and must not divide by zero`() {
        val previous = fix(lat = 41.2995, lng = 69.2401, elapsed = 5_000L)
        val current = fix(lat = 41.4000, lng = 69.2401, elapsed = 5_000L)
        // No time has passed, so no speed judgement is possible. Must not crash
        // and must not fabricate an infinite speed.
        assertEquals(MovementVerdict.Plausible, validator.validate(current, previous))
    }

    @Test
    fun `non-monotonic timestamps are ignored rather than trusted`() {
        val previous = fix(elapsed = 10_000L)
        val current = fix(lat = 41.4000, elapsed = 1_000L) // clock went backwards
        assertEquals(MovementVerdict.Plausible, validator.validate(current, previous))
    }

    @Test
    fun `haversine matches a known distance within one percent`() {
        // 0.01 degrees of latitude is ~1111 m anywhere on the globe.
        val a = fix(lat = 41.0000, lng = 69.0000)
        val b = fix(lat = 41.0100, lng = 69.0000)
        val meters = validator.distanceMeters(a, b)
        assertTrue("expected ~1111 m, got $meters", abs(meters - 1111.0) < 11.0)
    }

    @Test
    fun `haversine is symmetric`() {
        val a = fix(lat = 41.0, lng = 69.0)
        val b = fix(lat = 41.01, lng = 69.01)
        assertEquals(validator.distanceMeters(a, b), validator.distanceMeters(b, a), 0.0001)
    }

    @Test
    fun `zero distance is zero`() {
        val a = fix()
        assertEquals(0.0, validator.distanceMeters(a, a), 0.0001)
    }
}