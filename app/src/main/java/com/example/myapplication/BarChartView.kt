package com.example.myapplication

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class MonthData(val label: String, val income: Float, val expense: Float)

    private var data: List<MonthData> = emptyList()

    private val paintIncome  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintExpense = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintText    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 26f }
    private val paintYLabel  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT;  textSize = 24f }
    private val paintGrid    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }

    fun setData(months: List<MonthData>) {
        data = months
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (data.isEmpty()) return

        paintIncome.color  = ContextCompat.getColor(context, R.color.color_income)
        paintExpense.color = ContextCompat.getColor(context, R.color.color_expense)
        paintText.color    = ContextCompat.getColor(context, R.color.text_secondary)
        paintYLabel.color  = ContextCompat.getColor(context, R.color.text_secondary)
        paintGrid.color    = ContextCompat.getColor(context, R.color.divider)

        val yAxisWidth  = 72f
        val paddingRight = 16f
        val paddingTop   = 16f
        val labelHeight  = 40f
        val chartBottom  = height - labelHeight
        val chartHeight  = chartBottom - paddingTop

        val maxVal = data.maxOf { max(it.income, it.expense) }.takeIf { it > 0 } ?: 1f

        // Y-axis grid lines + labels (4 lines)
        val steps = 4
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps
            val y = paddingTop + chartHeight * (1f - fraction)
            canvas.drawLine(yAxisWidth, y, width - paddingRight, y, paintGrid)
            val value = (maxVal * fraction).toInt()
            val label = if (value >= 1000) "₹${value / 1000}k" else "₹$value"
            canvas.drawText(label, yAxisWidth - 6f, y + paintYLabel.textSize / 3f, paintYLabel)
        }

        val totalWidth = width - yAxisWidth - paddingRight
        val groupWidth = totalWidth / data.size
        val barWidth   = groupWidth * 0.3f
        val gap        = groupWidth * 0.05f

        data.forEachIndexed { i, month ->
            val groupLeft = yAxisWidth + i * groupWidth
            val centerX   = groupLeft + groupWidth / 2f

            val incomeH = (month.income / maxVal) * chartHeight
            val incomeLeft = centerX - barWidth - gap / 2f
            canvas.drawRoundRect(
                RectF(incomeLeft, chartBottom - incomeH, incomeLeft + barWidth, chartBottom),
                6f, 6f, paintIncome
            )

            val expenseH = (month.expense / maxVal) * chartHeight
            val expenseLeft = centerX + gap / 2f
            canvas.drawRoundRect(
                RectF(expenseLeft, chartBottom - expenseH, expenseLeft + barWidth, chartBottom),
                6f, 6f, paintExpense
            )

            canvas.drawText(month.label, centerX, height - 8f, paintText)
        }
    }
}
