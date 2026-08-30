package com.geocamera.app

import android.content.Context
import android.location.Geocoder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
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

    /**
     * Reverse geocode using Android's built-in Geocoder — on devices with Google Play
     * Services this is backed by Google's own geocoding data, entirely free and with
     * no API key or billing account required. Returns null if unavailable (rare, mostly
     * custom ROMs without Play Services), in which case callers should fall back to
     * [reverseGeocode] (Nominatim).
     */
    fun nativeReverseGeocode(context: Context, lat: Double, lng: Double): GeocodeResult? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull() ?: return null
            val city = addr.locality ?: addr.subAdminArea ?: addr.subLocality ?: ""
            val state = addr.adminArea ?: ""
            val country = addr.countryName ?: ""
            val fullAddress = if (addr.maxAddressLineIndex >= 0) {
                (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) ?: "" }
            } else ""
            GeocodeResult(fullAddress, city, state, country)
        } catch (e: Exception) {
            null
        }
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
