package com.college.bustracker.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Data class representing a resolved geographic place from OpenStreetMap Nominatim.
 */
data class NominatimPlace(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "",
    val importance: Double = 0.0
)

sealed class GeocodingResultState {
    data class Success(val places: List<NominatimPlace>) : GeocodingResultState()
    object NoResults : GeocodingResultState()
    object RateLimited : GeocodingResultState()
    data class NetworkError(val message: String) : GeocodingResultState()
    data class Error(val message: String) : GeocodingResultState()
}

/**
 * Lightweight geocoding utility for OpenStreetMap Nominatim.
 * Strictly adheres to OSM usage policy:
 * 1. Identifiable User-Agent string.
 * 2. Absolute rate-limit throttling (minimum 1,000ms between calls).
 * 3. 5-second socket/connect timeouts.
 * 4. Error and empty-result handling.
 */
object NominatimGeocoder {

    private const val TAG = "NominatimGeocoder"
    private const val USER_AGENT = "CollegeBusTracker/1.0.0 (contact: transport@college.edu; Android)"
    private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
    private const val MIN_REQUEST_INTERVAL_MS = 1000L

    @Volatile
    private var lastRequestTimestamp = 0L

    suspend fun search(query: String, context: android.content.Context? = null): GeocodingResultState = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return@withContext GeocodingResultState.NoResults
        }

        // 1. Try Android Native Geocoder (backed by Google Maps Services on Android)
        if (context != null && android.location.Geocoder.isPresent()) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                val addresses = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocationName(trimmed, 6, object : android.location.Geocoder.GeocodeListener {
                            override fun onGeocode(results: MutableList<android.location.Address>) {
                                continuation.resumeWith(Result.success(results))
                            }
                            override fun onError(errorMessage: String?) {
                                continuation.resumeWith(Result.success(emptyList()))
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(trimmed, 6) ?: emptyList()
                }

                if (!addresses.isNullOrEmpty()) {
                    val places = addresses.mapNotNull { addr ->
                        val line = addr.getAddressLine(0) ?: addr.featureName
                        if (line.isNullOrBlank() || addr.latitude == 0.0 && addr.longitude == 0.0) null
                        else NominatimPlace(
                            displayName = line,
                            latitude = addr.latitude,
                            longitude = addr.longitude,
                            type = "google_location",
                            importance = 1.0
                        )
                    }
                    if (places.isNotEmpty()) {
                        Log.d(TAG, "Google Geocoder resolved ${places.size} places for '$trimmed'")
                        return@withContext GeocodingResultState.Success(places)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Native Google Geocoder failed, falling back to OSM Nominatim: ${e.message}")
            }
        }

        // 2. Throttle to respect the 1 request/sec policy for OSM fallback
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastRequestTimestamp
        if (timeSinceLast < MIN_REQUEST_INTERVAL_MS) {
            val waitTime = MIN_REQUEST_INTERVAL_MS - timeSinceLast
            kotlinx.coroutines.delay(waitTime)
        }
        lastRequestTimestamp = System.currentTimeMillis()

        var connection: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "$BASE_URL?q=$encodedQuery&format=json&limit=6&addressdetails=1"
            val url = URL(urlString)

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode == 429) {
                Log.w(TAG, "Nominatim HTTP 429: Rate limited")
                return@withContext GeocodingResultState.RateLimited
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Nominatim HTTP $responseCode: ${connection.responseMessage}")
                return@withContext GeocodingResultState.NetworkError("Server responded with HTTP $responseCode")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
            val responseText = reader.use { it.readText() }

            val jsonArray = try {
                JSONArray(responseText)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Nominatim JSON response: ${e.message}")
                return@withContext GeocodingResultState.Error("Invalid server response")
            }

            if (jsonArray.length() == 0) {
                return@withContext GeocodingResultState.NoResults
            }

            val places = mutableListOf<NominatimPlace>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val lat = item.optDouble("lat", 0.0)
                val lon = item.optDouble("lon", 0.0)
                val name = item.optString("display_name", "")
                val type = item.optString("type", "")
                val importance = item.optDouble("importance", 0.0)

                if (lat != 0.0 && lon != 0.0 && name.isNotBlank()) {
                    places.add(
                        NominatimPlace(
                            displayName = name,
                            latitude = lat,
                            longitude = lon,
                            type = type,
                            importance = importance
                        )
                    )
                }
            }

            if (places.isEmpty()) {
                GeocodingResultState.NoResults
            } else {
                GeocodingResultState.Success(places)
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Nominatim request timed out: ${e.message}")
            GeocodingResultState.NetworkError("Search timed out. Please try again.")
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Nominatim network IO failure: ${e.message}")
            GeocodingResultState.NetworkError("Network error. Please check your internet connection.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected geocoding failure: ${e.message}", e)
            GeocodingResultState.Error(e.message ?: "Failed to search location")
        } finally {
            connection?.disconnect()
        }
    }
}
