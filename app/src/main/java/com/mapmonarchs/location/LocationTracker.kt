package com.mapmonarchs.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapmonarchs.domain.model.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits raw position readings as a cold Flow. Collection starts updates and
 * cancellation stops them -- location must never outlive the screen that needs
 * it, because background location is the single largest battery/uninstall risk
 * in this genre.
 *
 * Emits evidence only. It performs no validation and makes no territory claim.
 */
class LocationTracker(private val context: Context) {

    @SuppressLint("MissingPermission") // caller must hold ACCESS_FINE_LOCATION; see hasPermission()
    fun fixes(intervalMillis: Long = 3_000L): Flow<LocationFix> = callbackFlow {
        val fused = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(
                        LocationFix(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                            speedMetersPerSecond = if (loc.hasSpeed()) loc.speed else null,
                            // isMock is a cheap first filter only. A rooted device can
                            // hide it, which is exactly why the server re-checks and
                            // why attestation (Play Integrity) is on the roadmap.
                            isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                loc.isMock
                            } else {
                                @Suppress("DEPRECATION")
                                loc.isFromMockProvider
                            },
                            elapsedRealtimeMillis = loc.elapsedRealtimeNanos / 1_000_000L
                        )
                    )
                }
            }
        }

        fused.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}