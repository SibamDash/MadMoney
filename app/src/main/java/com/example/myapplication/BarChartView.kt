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
    private val paintText    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 28f }
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
        paintGrid.color    = ContextCompat.getColor(context, R.color.divider)

        val paddingLeft   = 20f
        val paddingRight  = 20f
        val paddingTop    = 16f
        val labelHeight   = 40f
        val chartBottom   = height - labelHeight
        val chartHeight   = chartBottom - paddingTop

        val maxVal = data.maxOf { max(it.income, it.expense) }.takeIf { it > 0 } ?: 1f

        // horizontal grid lines (3)
        for (i in 1..3) {
            val y = paddingTop + chartHeight * (1f - i / 3f)
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, paintGrid)
        }

        val totalWidth = width - paddingLeft - paddingRight
        val groupWidth = totalWidth / data.size
        val barWidth   = groupWidth * 0.3f
        val gap        = groupWidth * 0.05f

        data.forEachIndexed { i, month ->
            val groupLeft = paddingLeft + i * groupWidth
            val centerX   = groupLeft + groupWidth / 2f

            // income bar
            val incomeH = (month.income / maxVal) * chartHeight
            val incomeLeft = centerX - barWidth - gap / 2f
            canvas.drawRoundRect(
                RectF(incomeLeft, chartBottom - incomeH, incomeLeft + barWidth, chartBottom),
                6f, 6f, paintIncome
            )

            // expense bar
            val expenseH = (month.expense / maxVal) * chartHeight
            val expenseLeft = centerX + gap / 2f
            canvas.drawRoundRect(
                RectF(expenseLeft, chartBottom - expenseH, expenseLeft + barWidth, chartBottom),
                6f, 6f, paintExpense
            )

            // month label
            canvas.drawText(month.label, centerX, height - 8f, paintText)
        }
    }
}
