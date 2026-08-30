package com.geocamera.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Esri World Imagery XYZ tile source, used as the satellite layer inside the osmdroid MapView. */
object EsriSatelliteTileSource : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return baseUrl + "$zoom/$y/$x"
    }
}

/** Fetches a single satellite tile bitmap for the overlay thumbnail (no MapView needed). */
object SatelliteTile {
    fun fetchTileBitmap(lat: Double, lon: Double, zoom: Int = 17): Bitmap? {
        return try {
            val latRad = Math.toRadians(lat)
            val n = 2.0.pow(zoom)
            val xtile = ((lon + 180.0) / 360.0 * n).toInt()
            val ytile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
            val url = URL("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$ytile/$xtile")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "GeoCameraApp/1.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            BitmapFactory.decodeStream(conn.inputStream)
        } catch (e: Exception) {
            null
        }
    }
}
