package com.geocamera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

object OverlayRenderer {

    /** Fraction of the photo/preview height used by the overlay card. */
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
     * Draws the rounded-corner info-card overlay onto [canvas], sized for a [width]x[height]
     * surface. Shared by the live on-screen framing guide and the final captured/edited photo
     * so what you see while shooting matches what gets saved.
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
        val marginH = width * 0.03f
        val marginBottom = height * 0.02f

        val cardLeft = marginH
        val cardRight = width - marginH
        val cardBottom = height - marginBottom
        val cardTop = cardBottom - panelHeight
        val cornerRadius = panelHeight * 0.18f

        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        val bgPaint = Paint().apply { color = Color.parseColor("#DD000000"); isAntiAlias = true }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

        val pad = panelHeight * 0.10f
        val thumbSize = panelHeight - pad * 2
        val thumbLeft = cardLeft + pad
        val thumbTop = cardTop + pad

        // Rounded-corner map thumbnail
        canvas.save()
        val thumbPath = Path().apply {
            addRoundRect(
                RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize),
                thumbSize * 0.16f, thumbSize * 0.16f, Path.Direction.CW
            )
        }
        canvas.clipPath(thumbPath)
        if (satelliteTile != null) {
            val src = Rect(0, 0, satelliteTile.width, satelliteTile.height)
            val dst = RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize)
            canvas.drawBitmap(satelliteTile, src, dst, null)
        } else {
            val placeholderPaint = Paint().apply { color = Color.parseColor("#333333") }
            canvas.drawRect(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize, placeholderPaint)
        }
        canvas.restore()

        // Map-pin marker: red teardrop with a white center dot
        val pinR = thumbSize * 0.10f
        val pinCx = thumbLeft + thumbSize / 2
        val pinCy = thumbTop + thumbSize / 2 - pinR * 0.4f
        val pinPaint = Paint().apply { color = Color.parseColor("#EA4335"); isAntiAlias = true }
        val pinPath = Path().apply {
            addCircle(pinCx, pinCy, pinR, Path.Direction.CW)
            moveTo(pinCx - pinR * 0.65f, pinCy + pinR * 0.65f)
            lineTo(pinCx + pinR * 0.65f, pinCy + pinR * 0.65f)
            lineTo(pinCx, pinCy + pinR * 2.3f)
            close()
        }
        canvas.drawPath(pinPath, pinPaint)
        val dotPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        canvas.drawCircle(pinCx, pinCy, pinR * 0.42f, dotPaint)

        val textLeft = thumbLeft + thumbSize + pad
        val maxTextWidth = cardRight - pad - textLeft
        var textY = cardTop + panelHeight * 0.30f

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
            "Lat : ${"%.5f".format(lat)}\u00B0   Long : ${"%.5f".format(lng)}\u00B0"
        } else "Lat : --   Long : --"
        canvas.drawText(truncate(coordLine, bodyPaint, maxTextWidth), textLeft, textY, bodyPaint)
        textY += lineGap

        if (plusCode.isNotBlank()) {
            canvas.drawText(truncate("Plus Code : $plusCode", bodyPaint, maxTextWidth), textLeft, textY, bodyPaint)
            textY += lineGap
        }

        canvas.drawText(truncate(dateTimeText, bodyPaint, maxTextWidth), textLeft, textY, bodyPaint)

        if (personName.isNotBlank() && textY + lineGap <= cardBottom - pad) {
            textY += lineGap
            canvas.drawText(truncate("Person : $personName", bodyPaint, maxTextWidth), textLeft, textY, bodyPaint)
        }
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
