package com.geocamera.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * Sits directly on top of the CameraX PreviewView (same bounds) and draws the same
 * info-card overlay that will be baked into the final photo, so the user can see and
 * adjust framing before capturing. Draws nothing until a location has been picked.
 */
class OverlayPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var satelliteTile: Bitmap? = null
    private var locationName: String = ""
    private var lat: Double? = null
    private var lng: Double? = null
    private var plusCode: String = ""
    private var dateTimeText: String = ""
    private var personName: String = ""

    init {
        isClickable = false
        isFocusable = false
    }

    fun update(
        satelliteTile: Bitmap?,
        locationName: String,
        lat: Double?,
        lng: Double?,
        plusCode: String,
        dateTimeText: String,
        personName: String
    ) {
        this.satelliteTile = satelliteTile
        this.locationName = locationName
        this.lat = lat
        this.lng = lng
        this.plusCode = plusCode
        this.dateTimeText = dateTimeText
        this.personName = personName
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0 || lat == null || lng == null) return
        OverlayRenderer.drawOverlay(
            canvas, width, height, satelliteTile, locationName, lat, lng, plusCode, dateTimeText, personName
        )
    }
}
