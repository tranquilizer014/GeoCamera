package com.geocamera.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.geocamera.app.databinding.ActivityMapPickerBinding
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint

class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private lateinit var favoritesManager: FavoritesManager
    private var currentLat = 20.5937
    private var currentLng = 78.9629

    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().userAgentValue = packageName
        super.onCreate(savedInstanceState)
        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        favoritesManager = FavoritesManager(this)

        binding.mapView.setTileSource(EsriSatelliteTileSource)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(16.0)
        binding.mapView.controller.setCenter(GeoPoint(currentLat, currentLng))

        binding.mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                updateCenterCoords()
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                updateCenterCoords()
                return true
            }
        })

        updateCenterCoords()

        binding.btnConfirmLocation.setOnClickListener {
            val result = Intent()
            result.putExtra("lat", currentLat)
            result.putExtra("lng", currentLng)
            setResult(RESULT_OK, result)
            finish()
        }

        binding.btnSaveFavorite.setOnClickListener {
            val name = binding.etFavoriteName.text.toString().ifBlank { "Favorite ${System.currentTimeMillis()}" }
            favoritesManager.addFavorite(FavoriteLocation(name, currentLat, currentLng))
            Toast.makeText(this, "Saved favorite: $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCenterCoords() {
        val center = binding.mapView.mapCenter
        currentLat = center.latitude
        currentLng = center.longitude
        binding.tvCoords.text = "Lat: %.6f  Long: %.6f".format(currentLat, currentLng)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
