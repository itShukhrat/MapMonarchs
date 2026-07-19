package com.mapmonarchs.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.DeliveryProof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Delivery proof must survive a restart.
 *
 * "Has email ever worked on this project?" is a fact about the backend, not
 * about this app session. Losing it on every launch would make it unanswerable
 * in exactly the case that matters -- an install that has never received a code.
 *
 * Each test builds a SECOND store over the same preferences to stand in for the
 * process restart: a store that only returned what it was handed in memory would
 * pass a single-instance test and fail the real thing.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryProofStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun newStore() = DeliveryProofStore(context)

    @Before
    fun clear() {
        context.getSharedPreferences("mapmonarchs.delivery", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun aFreshInstallHasNeverAttemptedDelivery() {
        assertEquals(DeliveryProof.NeverAttempted, newStore().read())
    }

    @Test
    fun aRequestedProofSurvivesRestartAndStaysUnconfirmed() {
        newStore().write(DeliveryProof.Requested("e***@gmail.com", 1_700_000_000_000))

        val restored = newStore().read()
        assertTrue("must not be promoted to Delivered", restored is DeliveryProof.Requested)
        val requested = restored as DeliveryProof.Requested
        assertEquals("e***@gmail.com", requested.recipientHint)
        assertEquals(1_700_000_000_000, requested.atEpochMillis)
    }

    @Test
    fun aDeliveredProofSurvivesRestart() {
        newStore().write(DeliveryProof.Delivered("a***@example.com", 1_700_000_123_000))

        val restored = newStore().read() as? DeliveryProof.Delivered
            ?: error("expected Delivered, got ${newStore().read()}")
        assertEquals("a***@example.com", restored.recipientHint)
        assertEquals(1_700_000_123_000, restored.atEpochMillis)
    }

    @Test
    fun aFailedProofKeepsItsCauseAcrossRestart() {
        newStore().write(
            DeliveryProof.Failed(
                "b***@example.com",
                AppError.MailNotDelivered("smtp not configured"),
                1_700_000_456_000
            )
        )

        val restored = newStore().read() as? DeliveryProof.Failed
            ?: error("expected Failed")
        assertTrue(
            "the cause must survive, not be replaced by a generic message",
            restored.error.technical.contains("smtp not configured")
        )
    }

    /**
     * A record written by a version that knew a state this one does not must read
     * as Unknown, never as NeverAttempted. Those mean opposite things: "nobody has
     * tried" versus "something happened and we lost the record of it".
     */
    @Test
    fun anUnrecognisedRecordReadsAsUnknownNotAsAFreshInstall() {
        context.getSharedPreferences("mapmonarchs.delivery", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("kind", "SOME_FUTURE_STATE")
            .putString("hint", "x***@example.com")
            .putLong("at", 1_700_000_000_000)
            .commit()

        assertEquals(DeliveryProof.Unknown, newStore().read())
    }

    /** An incomplete record is corruption, not a clean slate. */
    @Test
    fun aRecordMissingItsTimestampReadsAsUnknown() {
        context.getSharedPreferences("mapmonarchs.delivery", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("kind", "DELIVERED")
            .putString("hint", "x***@example.com")
            .commit()

        assertEquals(DeliveryProof.Unknown, newStore().read())
    }

    /**
     * Regression guard for a bug shipped once already elsewhere in this codebase:
     * a missing timestamp read as 0 and was rendered as a real date (1970).
     */
    @Test
    fun aMissingTimestampIsNeverRenderedAsEpochZero() {
        context.getSharedPreferences("mapmonarchs.delivery", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("kind", "REQUESTED")
            .putString("hint", "x***@example.com")
            .putLong("at", 0L)
            .commit()

        // 0 is a legal instant, so it is accepted -- but it must never arrive by
        // ABSENCE. Absence is tested above and yields Unknown.
        val restored = newStore().read()
        assertTrue(restored is DeliveryProof.Requested)
    }

    @Test
    fun clearingReturnsToNeverAttempted() {
        val store = newStore()
        store.write(DeliveryProof.Delivered("a***@example.com", 1_700_000_000_000))
        store.write(DeliveryProof.NeverAttempted)
        assertEquals(DeliveryProof.NeverAttempted, newStore().read())
    }
}
