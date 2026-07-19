package com.mapmonarchs.data.local

import android.content.Context
import android.util.Log
import com.mapmonarchs.domain.model.AppError
import com.mapmonarchs.domain.model.DeliveryProof

private const val TAG = "DeliveryProofStore"

/**
 * Persists whether email delivery has ever been proven on this device.
 *
 * Survives restarts on purpose: "has mail ever worked here?" is a question about
 * the project's configuration, not about this app session. Losing the answer on
 * every launch would make it unanswerable in exactly the situation where it
 * matters -- an install that has never received a code.
 *
 * NOTE ON WHAT IS STORED: only a redacted recipient hint. The full address is
 * never written to preferences; diagnostics needs to show WHICH address was
 * tried, not the address.
 */
class DeliveryProofStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mapmonarchs.delivery", Context.MODE_PRIVATE)

    fun read(): DeliveryProof {
        val kind = prefs.getString(KEY_KIND, null) ?: return DeliveryProof.NeverAttempted
        val hint = prefs.getString(KEY_HINT, null)
        // -1 is a sentinel for "absent", checked explicitly below. It is never
        // treated as a timestamp: epoch 0 or -1 rendered as a date would be a
        // fabricated fact, and this class exists to stop exactly that.
        val at = prefs.getLong(KEY_AT, -1L)

        if (hint == null || at < 0L) {
            Log.w(TAG, "delivery record incomplete (kind=$kind, hint=${hint != null}, at=$at)")
            return DeliveryProof.Unknown
        }

        return when (kind) {
            KIND_REQUESTED -> DeliveryProof.Requested(hint, at)
            KIND_DELIVERED -> DeliveryProof.Delivered(hint, at)
            KIND_FAILED -> {
                val technical = prefs.getString(KEY_ERROR, null)
                if (technical == null) {
                    // A failure with no cause is not a failure we can report, and
                    // inventing "Unexpected" here would manufacture a reason.
                    Log.w(TAG, "failed record has no stored cause")
                    DeliveryProof.Unknown
                } else {
                    DeliveryProof.Failed(hint, AppError.Unexpected(technical), at)
                }
            }
            // Written by a version that knew a state this one does not.
            else -> {
                Log.w(TAG, "unrecognised delivery state: $kind")
                DeliveryProof.Unknown
            }
        }
    }

    fun write(proof: DeliveryProof) {
        val editor = prefs.edit()
        when (proof) {
            is DeliveryProof.NeverAttempted, is DeliveryProof.Unknown -> editor.clear()

            is DeliveryProof.Requested -> editor
                .putString(KEY_KIND, KIND_REQUESTED)
                .putString(KEY_HINT, proof.recipientHint)
                .putLong(KEY_AT, proof.atEpochMillis)
                .remove(KEY_ERROR)

            is DeliveryProof.Delivered -> editor
                .putString(KEY_KIND, KIND_DELIVERED)
                .putString(KEY_HINT, proof.recipientHint)
                .putLong(KEY_AT, proof.atEpochMillis)
                .remove(KEY_ERROR)

            is DeliveryProof.Failed -> editor
                .putString(KEY_KIND, KIND_FAILED)
                .putString(KEY_HINT, proof.recipientHint)
                .putLong(KEY_AT, proof.atEpochMillis)
                .putString(KEY_ERROR, proof.error.technical)
        }
        editor.apply()
    }

    private companion object {
        const val KEY_KIND = "kind"
        const val KEY_HINT = "hint"
        const val KEY_AT = "at"
        const val KEY_ERROR = "error"

        const val KIND_REQUESTED = "REQUESTED"
        const val KIND_DELIVERED = "DELIVERED"
        const val KIND_FAILED = "FAILED"
    }
}
