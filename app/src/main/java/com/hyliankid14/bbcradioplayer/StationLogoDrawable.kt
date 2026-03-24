package com.hyliankid14.bbcradioplayer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * A generic station logo drawable that renders a coloured background with the station's
 * identifier text inside a circle. Used as a placeholder and error fallback so that
 * station artwork is always shown without relying on the BBC CDN or carrying BBC branding.
 */
class StationLogoDrawable(
    backgroundColor: Int,
    private val label: String,
    circleColor: Int = Color.parseColor("#1A1A1A"),
    textColor: Int = Color.WHITE
) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = circleColor
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        // Solid background
        canvas.drawRect(bounds, bgPaint)

        // Circle centred in the drawable
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.35f
        canvas.drawCircle(cx, cy, radius, circlePaint)

        // Label text inside the circle – scale font to fit
        val scaleFactor = when {
            label.length <= 1 -> 0.85f
            label.length == 2 -> 0.62f
            else -> 0.42f
        }
        textPaint.textSize = radius * scaleFactor * 2f
        // Vertical centre adjustment: descend by ~35 % of text size
        val textY = cy + textPaint.textSize * 0.35f
        canvas.drawText(label, cx, textY, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        circlePaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        circlePaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun getIntrinsicWidth(): Int = 600
    override fun getIntrinsicHeight(): Int = 600
}
