package com.geocamera.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class GeocodeResult(val fullAddress: String, val city: String, val state: String, val country: String)

object GeoUtils {

    /**
     * Returns a uniformly-random point within [radiusMeters] of (lat, lng).
     * Call this fresh every time a favorite is selected so the exact favorite
     * coordinates are never reused directly.
     */
    fun randomPointWithinRadius(lat: Double, lng: Double, radiusMeters: Double = 10.0): Pair<Double, Double> {
        val radiusInDegrees = radiusMeters / 111320.0
        val u = Random.nextDouble()
        val v = Random.nextDouble()
        val w = radiusInDegrees * sqrt(u)
        val t = 2 * PI * v
        val deltaLat = w * cos(t)
        val deltaLng = w * sin(t) / cos(Math.toRadians(lat))
        return Pair(lat + deltaLat, lng + deltaLng)
    }

    /** Blocking network call — must be invoked off the main thread. */
    fun reverseGeocode(lat: Double, lng: Double): GeocodeResult? {
        return try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=18&addressdetails=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "GeoCameraApp/1.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            val displayName = obj.optString("display_name", "")
            val address = obj.optJSONObject("address")
            val city = (address?.optString("city", "")?.ifEmpty { address.optString("town", "") }
                ?.ifEmpty { address.optString("village", "") }) ?: ""
            val state = address?.optString("state", "") ?: ""
            val country = address?.optString("country", "") ?: ""
            GeocodeResult(displayName, city, state, country)
        } catch (e: Exception) {
            null
        }
    }
}
