package com.geocamera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

object OverlayRenderer {

    fun applyOverlay(
        photo: Bitmap,
        satelliteTile: Bitmap?,
        locationName: String,
        address: String,
        lat: Double,
        lng: Double,
        plusCode: String,
        dateTimeText: String,
        personName: String
    ): Bitmap {
        val panelHeight = (photo.height * 0.26f).toInt()
        val result = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(photo, 0f, 0f, null)

        val panelTop = photo.height - panelHeight
        val bgPaint = Paint().apply { color = Color.parseColor("#DD000000") }
        canvas.drawRect(0f, panelTop.toFloat(), photo.width.toFloat(), photo.height.toFloat(), bgPaint)

        val thumbSize = (panelHeight - 24).toFloat()
        val thumbLeft = 12f
        val thumbTop = panelTop + 12f

        if (satelliteTile != null) {
            val src = Rect(0, 0, satelliteTile.width, satelliteTile.height)
            val dst = RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize)
            canvas.drawBitmap(satelliteTile, src, dst, null)
        } else {
            val placeholderPaint = Paint().apply { color = Color.parseColor("#333333") }
            canvas.drawRect(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize, placeholderPaint)
        }

        // Pin marker on the thumbnail
        val pinPaint = Paint().apply { color = Color.RED; isAntiAlias = true }
        val cx = thumbLeft + thumbSize / 2
        val cy = thumbTop + thumbSize / 2
        canvas.drawCircle(cx, cy - 10, 12f, pinPaint)
        val strokePaint = Paint().apply { color = Color.RED; strokeWidth = 5f }
        canvas.drawLine(cx, cy, cx, cy + 16f, strokePaint)

        // Text panel on the right
        val textLeft = thumbLeft + thumbSize + 20f
        val maxTextWidth = photo.width - textLeft - 16f
        var textY = panelTop + 42f

        val titlePaint = Paint().apply {
            color = Color.WHITE; textSize = 32f; isFakeBoldText = true; isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.WHITE; textSize = 21f; isAntiAlias = true
        }

        canvas.drawText(truncate(locationName, titlePaint, maxTextWidth), textLeft, textY, titlePaint)
        textY += 30f
        textY = drawWrappedText(canvas, address, textLeft, textY, bodyPaint, maxTextWidth, maxLines = 2)
        textY += 26f
        canvas.drawText("Lat ${"%.6f".format(lat)}\u00B0  Long ${"%.6f".format(lng)}\u00B0", textLeft, textY, bodyPaint)
        textY += 26f
        canvas.drawText("Plus Code: $plusCode", textLeft, textY, bodyPaint)
        textY += 26f
        canvas.drawText(dateTimeText, textLeft, textY, bodyPaint)
        if (personName.isNotBlank()) {
            textY += 26f
            canvas.drawText("Person: $personName", textLeft, textY, bodyPaint)
        }

        return result
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun drawWrappedText(
        canvas: Canvas, text: String, x: Float, startY: Float, paint: Paint, maxWidth: Float, maxLines: Int
    ): Float {
        var y = startY
        var lines = 0
        val words = text.split(" ")
        var line = StringBuilder()
        for (word in words) {
            if (lines >= maxLines) break
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(testLine) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                y += 25f
                lines++
                line = StringBuilder(word)
            } else {
                line = StringBuilder(testLine)
            }
        }
        if (line.isNotEmpty() && lines < maxLines) {
            canvas.drawText(line.toString(), x, y, paint)
            y += 25f
        }
        return y
    }
}
