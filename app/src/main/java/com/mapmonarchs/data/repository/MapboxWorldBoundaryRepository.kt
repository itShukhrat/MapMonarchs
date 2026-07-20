package com.mapmonarchs.data.repository

import android.util.Log
import com.mapmonarchs.BuildConfig
import com.mapmonarchs.data.remote.BoundaryResponseParser
import com.mapmonarchs.domain.model.WorldPosition
import com.mapmonarchs.domain.repository.WorldBoundaryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "WorldBoundary"

/** A local read would be instant; anything slower than this is a stall. */
private const val BOUNDARY_TIMEOUT_MS = 8_000L

/**
 * Point-in-country using Mapbox's published country boundaries.
 *
 * NO GEOGRAPHY IS INVENTED HERE. mapbox.country-boundaries-v1 is a real tileset
 * licensed under the token this app already ships with, and it was verified
 * against live coordinates before any of this was written: Tashkent and Samarkand
 * return UZ, Almaty returns KZ, Ashgabat returns TM.
 *
 * HttpURLConnection rather than the Mapbox SDK because Tilequery is a plain REST
 * endpoint outside the SDK's typed surface -- the same reasoning as the Supabase
 * auth settings probe.
 */
class MapboxWorldBoundaryRepository(
    private val accessToken: String = BuildConfig.MAPBOX_PUBLIC_TOKEN
) : WorldBoundaryRepository {

    override suspend fun positionOf(latitude: Double, longitude: Double): WorldPosition =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                withTimeout(BOUNDARY_TIMEOUT_MS) {
                    val url = URL(
                        "https://api.mapbox.com/v4/mapbox.country-boundaries-v1/" +
                            "tilequery/$longitude,$latitude.json?access_token=$accessToken"
                    )
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8_000
                        readTimeout = 8_000
                    }

                    val status = connection!!.responseCode
                    if (status !in 200..299) {
                        val detail = connection!!.errorStream?.bufferedReader()?.use { it.readText() }
                            ?: "no response body"
                        Log.w(TAG, "boundary lookup HTTP $status: $detail")
                        // Unknown, not Outside. An unreachable service says nothing
                        // about where the player is standing.
                        return@withTimeout WorldPosition.Unknown("boundary lookup returned HTTP $status")
                    }

                    val body = connection!!.inputStream.bufferedReader().use { it.readText() }
                    BoundaryResponseParser.parse(body).also {
                        Log.i(TAG, "position resolved: $it")
                    }
                }
            } catch (t: TimeoutCancellationException) {
                Log.w(TAG, "boundary lookup timed out after ${BOUNDARY_TIMEOUT_MS}ms", t)
                WorldPosition.Unknown("boundary lookup timed out")
            } catch (t: CancellationException) {
                // The caller going away is not our failure to report.
                throw t
            } catch (t: Throwable) {
                Log.w(TAG, "boundary lookup failed", t)
                WorldPosition.Unknown(
                    "boundary lookup failed: ${t.message ?: t::class.simpleName}"
                )
            } finally {
                connection?.disconnect()
            }
        }
}
