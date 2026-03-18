package com.example.myapplication

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val slices = mutableListOf<Pair<Float, Int>>() // (sweepAngle, color)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun setData(data: List<Pair<Float, Int>>) {
        slices.clear()
        slices.addAll(data)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (slices.isEmpty()) return
        val size = minOf(width, height).toFloat()
        val margin = size * 0.05f
        val oval = RectF(margin, margin, size - margin, size - margin)
        val cx = size / 2f
        val cy = size / 2f
        val radius = (size / 2f) - margin
        var startAngle = -90f
        for ((sweep, color) in slices) {
            paint.color = color
            canvas.drawArc(oval, startAngle, sweep, true, paint)
            // divider line between slices
            val rad = Math.toRadians(startAngle.toDouble())
            canvas.drawLine(cx, cy,
                cx + radius * Math.cos(rad).toFloat(),
                cy + radius * Math.sin(rad).toFloat(),
                borderPaint)
            startAngle += sweep
        }
        // outer circle border
        canvas.drawCircle(cx, cy, radius, borderPaint)
    }
}
