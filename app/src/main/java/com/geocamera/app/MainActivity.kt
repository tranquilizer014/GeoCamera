package com.geocamera.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.geocamera.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var usingFrontCamera = false
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var favoritesManager: FavoritesManager

    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var locationName: String = ""
    private var address: String = ""
    private var plusCode: String = ""
    private var satelliteBitmap: Bitmap? = null

    // Default timezone is IST, user-changeable via the Date/Time dialog.
    private var timeZoneId: String = "Asia/Kolkata"
    private var calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId))

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
            if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                fetchCurrentLocation()
            }
        }

    private val mapPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val lat = data.getDoubleExtra("lat", Double.NaN)
                val lng = data.getDoubleExtra("lng", Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) applyLocation(lat, lng)
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) applyOverlayToPickedImage(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        favoritesManager = FavoritesManager(this)

        val needCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        val needLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED

        if (!needCamera) startCamera()
        if (!needLocation) fetchCurrentLocation()

        val toRequest = mutableListOf<String>()
        if (needCamera) toRequest.add(Manifest.permission.CAMERA)
        if (needLocation) toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (toRequest.isNotEmpty()) requestPermissionsLauncher.launch(toRequest.toTypedArray())

        binding.btnSwitchCamera.setOnClickListener {
            usingFrontCamera = !usingFrontCamera
            startCamera()
        }

        binding.btnMyLocation.setOnClickListener { fetchCurrentLocation(forceApply = true) }

        binding.btnPickLocation.setOnClickListener {
            mapPickerLauncher.launch(Intent(this, MapPickerActivity::class.java))
        }

        binding.btnFavorites.setOnClickListener { showFavoritesDialog() }
        binding.btnDateTime.setOnClickListener { showDateDialog() }
        binding.btnFromGallery.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnCapture.setOnClickListener { capturePhoto() }

        binding.etPersonName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateOverlayPreview() }
        })

        updateLocationSummary()
        updateOverlayPreview()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val selector = if (usingFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, selector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Uses the device's real GPS/network location as the default. If [forceApply] is false
     * (used at app launch) it only fills the location in when nothing has been chosen yet, so
     * it never overwrites a manual pick from the map or a favorite. If [forceApply] is true
     * (the "My Location" button) it always applies the fresh real location.
     */
    private fun fetchCurrentLocation(forceApply: Boolean = false) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (forceApply) Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            var best: Location? = null
            for (provider in lm.getProviders(true)) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best != null && (forceApply || selectedLat == null)) {
                applyLocation(best.latitude, best.longitude)
            }

            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                lm.requestLocationUpdates(provider, 0L, 0f, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (forceApply || selectedLat == null) applyLocation(location.latitude, location.longitude)
                        lm.removeUpdates(this)
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, mainLooper)
            } else if (best == null && forceApply) {
                Toast.makeText(this, "No location provider available on this device", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            if (forceApply) Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyLocation(lat: Double, lng: Double) {
        selectedLat = lat
        selectedLng = lng
        plusCode = PlusCode.encode(lat, lng)
        locationName = ""
        address = ""
        satelliteBitmap = null
        updateLocationSummary()
        updateOverlayPreview()

        cameraExecutor.execute {
            val geocode = GeoUtils.nativeReverseGeocode(this, lat, lng) ?: GeoUtils.reverseGeocode(lat, lng)
            val tile = SatelliteTile.fetchTileBitmap(lat, lng)
            runOnUiThread {
                if (geocode != null) {
                    locationName = listOf(geocode.city, geocode.state, geocode.country)
                        .filter { it.isNotBlank() }.joinToString(", ")
                    address = geocode.fullAddress
                }
                satelliteBitmap = tile
                updateLocationSummary()
                updateOverlayPreview()
            }
        }
    }

    private fun updateLocationSummary() {
        binding.tvLocationSummary.text = if (selectedLat != null) {
            "${locationName.ifBlank { "Location set" }}\n" +
                "Lat ${"%.5f".format(selectedLat)}  Long ${"%.5f".format(selectedLng)}\n" +
                formattedDateTime()
        } else {
            "No location selected — tap My Location or Pick Location"
        }
    }

    /** Pushes the current state into the live on-screen overlay guide. */
    private fun updateOverlayPreview() {
        binding.overlayPreview.update(
            satelliteBitmap,
            locationName,
            selectedLat,
            selectedLng,
            plusCode,
            formattedDateTime(),
            binding.etPersonName.text.toString()
        )
    }

    private fun showFavoritesDialog() {
        val favorites = favoritesManager.getFavorites()
        if (favorites.isEmpty()) {
            Toast.makeText(this, "No favorites saved yet. Save one from the map screen.", Toast.LENGTH_LONG).show()
            return
        }
        val names = favorites.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose favorite (a fresh random point within 10m is used each time)")
            .setItems(names) { _, which ->
                val fav = favorites[which]
                val (rLat, rLng) = GeoUtils.randomPointWithinRadius(fav.lat, fav.lng, 10.0)
                applyLocation(rLat, rLng)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDateDialog() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        android.app.DatePickerDialog(this, { _: DatePicker, y, m, d ->
            calendar.set(Calendar.YEAR, y)
            calendar.set(Calendar.MONTH, m)
            calendar.set(Calendar.DAY_OF_MONTH, d)
            showTimeDialog()
        }, year, month, day).show()
    }

    private fun showTimeDialog() {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        android.app.TimePickerDialog(this, { _: TimePicker, h, min ->
            calendar.set(Calendar.HOUR_OF_DAY, h)
            calendar.set(Calendar.MINUTE, min)
            showTimezoneDialog()
        }, hour, minute, false).show()
    }

    private fun showTimezoneDialog() {
        val deviceZone = TimeZone.getDefault().id
        val options = arrayOf("IST — Asia/Kolkata (default)", "Device default ($deviceZone)", "UTC")
        AlertDialog.Builder(this)
            .setTitle("Time zone")
            .setItems(options) { _, which ->
                timeZoneId = when (which) {
                    0 -> "Asia/Kolkata"
                    1 -> deviceZone
                    else -> "UTC"
                }
                calendar.timeZone = TimeZone.getTimeZone(timeZoneId)
                updateLocationSummary()
                updateOverlayPreview()
            }
            .show()
    }

    private fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val zone = TimeZone.getTimeZone(timeZoneId)
        sdf.timeZone = zone
        val offsetHours = zone.rawOffset / 3600000.0
        val sign = if (offsetHours >= 0) "+" else "-"
        val absOffset = abs(offsetHours)
        val h = absOffset.toInt()
        val m = ((absOffset - h) * 60).toInt()
        return "${sdf.format(calendar.time)} GMT $sign${"%02d".format(h)}:${"%02d".format(m)}"
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        if (selectedLat == null) {
            Toast.makeText(this, "Pick a location first", Toast.LENGTH_SHORT).show()
            return
        }
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                val finalBitmap = OverlayRenderer.applyOverlay(
                    photo = bitmap,
                    satelliteTile = satelliteBitmap,
                    locationName = locationName.ifBlank { "Location" },
                    lat = selectedLat!!,
                    lng = selectedLng!!,
                    plusCode = plusCode,
                    dateTimeText = formattedDateTime(),
                    personName = binding.etPersonName.text.toString()
                )
                runOnUiThread { saveToGallery(finalBitmap) }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    /** Applies the current overlay info onto a photo picked from the gallery and saves it. */
    private fun applyOverlayToPickedImage(uri: Uri) {
        if (selectedLat == null) {
            Toast.makeText(this, "Pick a location first", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Processing photo…", Toast.LENGTH_SHORT).show()
        cameraExecutor.execute {
            val bitmap = loadBitmapFromUri(uri)
            if (bitmap == null) {
                runOnUiThread { Toast.makeText(this, "Couldn't load that image", Toast.LENGTH_SHORT).show() }
                return@execute
            }
            val finalBitmap = OverlayRenderer.applyOverlay(
                photo = bitmap,
                satelliteTile = satelliteBitmap,
                locationName = locationName.ifBlank { "Location" },
                lat = selectedLat!!,
                lng = selectedLng!!,
                plusCode = plusCode,
                dateTimeText = formattedDateTime(),
                personName = binding.etPersonName.text.toString()
            )
            runOnUiThread { saveToGallery(finalBitmap) }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val rotation = contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0 || usingFrontCamera) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            if (usingFrontCamera) matrix.postScale(-1f, 1f)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        return bmp
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val filename = "GeoCamera_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GeoCamera")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
