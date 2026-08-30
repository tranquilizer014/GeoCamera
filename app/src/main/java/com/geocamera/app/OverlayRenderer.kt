package com.geocamera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

object OverlayRenderer {

    /** Fraction of the photo/preview height used by the overlay panel — half of the old 0.26. */
    const val PANEL_HEIGHT_FRACTION = 0.13f

    /** Bakes the overlay permanently onto a copy of [photo] for the final saved image. */
    fun applyOverlay(
        photo: Bitmap,
        satelliteTile: Bitmap?,
        locationName: String,
        lat: Double,
        lng: Double,
        plusCode: String,
        dateTimeText: String,
        personName: String
    ): Bitmap {
        val result = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(photo, 0f, 0f, null)
        drawOverlay(canvas, photo.width, photo.height, satelliteTile, locationName, lat, lng, plusCode, dateTimeText, personName)
        return result
    }

    /**
     * Draws the info-card overlay onto [canvas], sized for a [width]x[height] surface.
     * Shared by the live on-screen framing guide and the final captured photo so what
     * you see while shooting matches what gets saved.
     */
    fun drawOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        satelliteTile: Bitmap?,
        locationName: String,
        lat: Double?,
        lng: Double?,
        plusCode: String,
        dateTimeText: String,
        personName: String
    ) {
        val panelHeight = height * PANEL_HEIGHT_FRACTION
        val panelTop = height - panelHeight

        val bgPaint = Paint().apply { color = Color.parseColor("#DD000000") }
        canvas.drawRect(0f, panelTop, width.toFloat(), height.toFloat(), bgPaint)

        val pad = panelHeight * 0.10f
        val thumbSize = panelHeight - pad * 2
        val thumbLeft = pad
        val thumbTop = panelTop + pad

        if (satelliteTile != null) {
            val src = Rect(0, 0, satelliteTile.width, satelliteTile.height)
            val dst = RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize)
            canvas.drawBitmap(satelliteTile, src, dst, null)
        } else {
            val placeholderPaint = Paint().apply { color = Color.parseColor("#333333") }
            canvas.drawRect(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize, placeholderPaint)
        }

        val pinPaint = Paint().apply { color = Color.RED; isAntiAlias = true }
        val cx = thumbLeft + thumbSize / 2
        val cy = thumbTop + thumbSize / 2
        canvas.drawCircle(cx, cy - thumbSize * 0.08f, thumbSize * 0.09f, pinPaint)
        val strokePaint = Paint().apply { color = Color.RED; strokeWidth = thumbSize * 0.035f }
        canvas.drawLine(cx, cy, cx, cy + thumbSize * 0.12f, strokePaint)

        val textLeft = thumbLeft + thumbSize + pad
        val maxTextWidth = width - textLeft - pad
        var textY = panelTop + panelHeight * 0.30f

        val titleSize = panelHeight * 0.26f
        val bodySize = panelHeight * 0.19f
        val lineGap = panelHeight * 0.22f

        val titlePaint = Paint().apply {
            color = Color.WHITE; textSize = titleSize; isFakeBoldText = true; isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.WHITE; textSize = bodySize; isAntiAlias = true
        }

        canvas.drawText(truncate(locationName.ifBlank { "Location" }, titlePaint, maxTextWidth), textLeft, textY, titlePaint)
        textY += lineGap

        val coordLine = if (lat != null && lng != null) {
            "Lat ${"%.5f".format(lat)}\u00B0 Long ${"%.5f".format(lng)}\u00B0"
        } else "Lat --  Long --"
        canvas.drawText(truncate(coordLine, bodyPaint, maxTextWidth), textLeft, textY, bodyPaint)
        textY += lineGap

        if (plusCode.isNotBlank()) {
            canvas.drawText(truncate("Plus Code: $plusCode", bodyPaint, maxTextWidth), textLeft, textY, bodyPaint)
            textY += lineGap
        }

        canvas.drawText(truncate(dateTimeText, bodyPaint, maxTextWidth), textLeft, textY, bodyPaint)

        if (personName.isNotBlank() && textY + lineGap <= height - pad) {
            textY += lineGap
            canvas.drawText(truncate("Person: $personName", bodyPaint, maxTextWidth), textLeft, textY, bodyPaint)
        }
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
