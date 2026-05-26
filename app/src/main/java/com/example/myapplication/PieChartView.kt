package com.example.myapplication

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val slices = mutableListOf<Pair<Float, Int>>()
    private var centerLabel = ""

    private val slicePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val holePaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize  = 42f
        isFakeBoldText = true
    }
    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize  = 28f
    }

    fun setData(data: List<Pair<Float, Int>>, center: String = "") {
        slices.clear()
        slices.addAll(data)
        centerLabel = center
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (slices.isEmpty()) return

        holePaint.color   = ContextCompat.getColor(context, R.color.background)
        labelPaint.color  = ContextCompat.getColor(context, R.color.text_primary)
        subLabelPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
        strokePaint.color = ContextCompat.getColor(context, R.color.background)

        val size    = minOf(width, height).toFloat()
        val margin  = size * 0.04f
        val oval    = RectF(margin, margin, size - margin, size - margin)
        val cx      = size / 2f
        val cy      = size / 2f
        val radius  = size / 2f - margin
        val holeR   = radius * 0.52f   // donut hole

        var startAngle = -90f
        for ((sweep, color) in slices) {
            slicePaint.color = color
            canvas.drawArc(oval, startAngle, sweep, true, slicePaint)
            canvas.drawArc(oval, startAngle, sweep, true, strokePaint)
            startAngle += sweep
        }

        // punch out center hole
        canvas.drawCircle(cx, cy, holeR, holePaint)

        // center text
        if (centerLabel.isNotEmpty()) {
            val textY = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(centerLabel, cx, textY - 10f, labelPaint)
            canvas.drawText("total", cx, textY + 30f, subLabelPaint)
        }
    }
}
