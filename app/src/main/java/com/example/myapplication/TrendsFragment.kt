package com.example.myapplication

import android.app.DatePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class TrendsFragment : Fragment() {

    private enum class Mode { CONSECUTIVE_MONTHS, SIX_MONTHS, CONSECUTIVE_YEARS, CUSTOM }

    private var mode = Mode.SIX_MONTHS
    private var customFrom: Calendar? = null
    private var customTo: Calendar? = null
    // For consecutive modes: how many units back from now
    private var consecutiveCount = 6  // months or years

    private val displayFmt = SimpleDateFormat("MMM yy", Locale.getDefault())
    private val yearFmt    = SimpleDateFormat("yyyy", Locale.getDefault())
    private val pickFmt    = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_trends, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spinner = view.findViewById<Spinner>(R.id.spinnerRange)
        val options = listOf("3 Months", "6 Months", "12 Months", "3 Years", "5 Years", "Year-over-Year", "Custom")
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        spinner.setSelection(1) // default: 6 months

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val customLayout = view.findViewById<LinearLayout>(R.id.layoutCustomRange)
                when (pos) {
                    0 -> { mode = Mode.CONSECUTIVE_MONTHS; consecutiveCount = 3;  customLayout.visibility = View.GONE; load(view) }
                    1 -> { mode = Mode.CONSECUTIVE_MONTHS; consecutiveCount = 6;  customLayout.visibility = View.GONE; load(view) }
                    2 -> { mode = Mode.CONSECUTIVE_MONTHS; consecutiveCount = 12; customLayout.visibility = View.GONE; load(view) }
                    3 -> { mode = Mode.CONSECUTIVE_YEARS;  consecutiveCount = 3;  customLayout.visibility = View.GONE; load(view) }
                    4 -> { mode = Mode.CONSECUTIVE_YEARS;  consecutiveCount = 5;  customLayout.visibility = View.GONE; load(view) }
                    5 -> { mode = Mode.SIX_MONTHS;         customLayout.visibility = View.GONE; load(view) }
                    6 -> { mode = Mode.CUSTOM; customLayout.visibility = View.VISIBLE }
                }
            }
        }

        // Custom date pickers
        view.findViewById<TextView>(R.id.tvCustomFrom).setOnClickListener { pickDate(view, isFrom = true) }
        view.findViewById<TextView>(R.id.tvCustomTo).setOnClickListener   { pickDate(view, isFrom = false) }
        view.findViewById<Button>(R.id.btnApplyCustom).setOnClickListener {
            if (customFrom != null && customTo != null) load(view)
            else Toast.makeText(requireContext(), "Select both dates", Toast.LENGTH_SHORT).show()
        }

        load(view)
    }

    override fun onResume() { super.onResume(); view?.let { load(it) } }

    private fun pickDate(view: View, isFrom: Boolean) {
        if (!isVisible) return
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val picked = Calendar.getInstance().apply { set(y, m, d) }
            if (isFrom) {
                customFrom = picked
                view.findViewById<TextView>(R.id.tvCustomFrom).text = "From: ${pickFmt.format(picked.time)}"
            } else {
                customTo = picked
                view.findViewById<TextView>(R.id.tvCustomTo).text = "To: ${pickFmt.format(picked.time)}"
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun load(view: View) {
        val db = DatabaseHelper(requireContext())
        val months: List<BarChartView.MonthData>
        val title: String

        when (mode) {
            Mode.CONSECUTIVE_MONTHS -> {
                title = "$consecutiveCount-Month Trends"
                val cal = Calendar.getInstance()
                months = (consecutiveCount - 1 downTo 0).map { offset ->
                    val c = (cal.clone() as Calendar).apply {
                        add(Calendar.MONTH, -offset)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                    }
                    val start = c.timeInMillis
                    c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
                    c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59)
                    val txns = db.getTransactions(start, c.timeInMillis)
                    BarChartView.MonthData(
                        displayFmt.format(Date(start)),
                        txns.filter { it.type == "income" }.sumOf { it.amount }.toFloat(),
                        txns.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()
                    )
                }
            }
            Mode.CONSECUTIVE_YEARS -> {
                title = "$consecutiveCount-Year Trends"
                val cal = Calendar.getInstance()
                months = (consecutiveCount - 1 downTo 0).map { offset ->
                    val c = (cal.clone() as Calendar).apply {
                        add(Calendar.YEAR, -offset)
                        set(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                    }
                    val start = c.timeInMillis
                    c.set(Calendar.MONTH, 11); c.set(Calendar.DAY_OF_MONTH, 31)
                    c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59)
                    val txns = db.getTransactions(start, c.timeInMillis)
                    BarChartView.MonthData(
                        yearFmt.format(Date(start)),
                        txns.filter { it.type == "income" }.sumOf { it.amount }.toFloat(),
                        txns.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()
                    )
                }
            }
            Mode.SIX_MONTHS -> {
                // Year-over-year: same 6 months this year vs last year
                title = "Year-over-Year (6 mo)"
                val cal = Calendar.getInstance()
                val thisYear = cal.get(Calendar.YEAR)
                months = (5 downTo 0).flatMap { offset ->
                    listOf(thisYear - 1, thisYear).map { yr ->
                        val c = (cal.clone() as Calendar).apply {
                            set(Calendar.YEAR, yr)
                            add(Calendar.MONTH, -offset)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                        }
                        val start = c.timeInMillis
                        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
                        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59)
                        val txns = db.getTransactions(start, c.timeInMillis)
                        val label = SimpleDateFormat("MMM", Locale.getDefault()).format(Date(start)) +
                                " '${yr.toString().takeLast(2)}"
                        BarChartView.MonthData(
                            label,
                            txns.filter { it.type == "income" }.sumOf { it.amount }.toFloat(),
                            txns.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()
                        )
                    }
                }
            }
            Mode.CUSTOM -> {
                val from = customFrom ?: return
                val to   = customTo   ?: return
                title = "${pickFmt.format(from.time)} – ${pickFmt.format(to.time)}"
                // Build month-by-month between from and to
                val result = mutableListOf<BarChartView.MonthData>()
                val cur = (from.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }
                val toMonth = (to.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }
                while (!cur.after(toMonth)) {
                    val start = cur.timeInMillis
                    val end = (cur.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                    }.timeInMillis
                    val txns = db.getTransactions(start, end)
                    result.add(BarChartView.MonthData(
                        displayFmt.format(Date(start)),
                        txns.filter { it.type == "income" }.sumOf { it.amount }.toFloat(),
                        txns.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()
                    ))
                    cur.add(Calendar.MONTH, 1)
                }
                months = result
            }
        }

        view.findViewById<TextView>(R.id.tvTrendsTitle).text = title
        view.findViewById<BarChartView>(R.id.barChart).setData(months)

        val ll = view.findViewById<LinearLayout>(R.id.llMonthRows)
        ll.removeAllViews()
        ll.addView(makeRow("Period", "Income", "Expense", "Net", isHeader = true))
        ll.addView(divider())
        months.forEach { m ->
            val net = m.income - m.expense
            ll.addView(makeRow(
                m.label,
                "₹${m.income.toInt()}",
                "₹${m.expense.toInt()}",
                (if (net >= 0) "+" else "") + "₹${net.toInt()}",
                isHeader = false, net = net
            ))
        }
    }

    private fun makeRow(c1: String, c2: String, c3: String, c4: String,
                        isHeader: Boolean, net: Float = 0f): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
            fun tv(text: String, weight: Float, align: Int = Gravity.START, color: Int? = null) =
                TextView(ctx).apply {
                    this.text = text
                    textSize = 13f
                    gravity = align
                    setTextColor(color ?: ContextCompat.getColor(ctx, if (isHeader) R.color.text_primary else R.color.text_secondary))
                    if (isHeader) setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                }
            addView(tv(c1, 1.6f))
            addView(tv(c2, 1f, Gravity.END, if (!isHeader) ContextCompat.getColor(ctx, R.color.color_income) else null))
            addView(tv(c3, 1f, Gravity.END, if (!isHeader) ContextCompat.getColor(ctx, R.color.color_expense) else null))
            addView(tv(c4, 1f, Gravity.END, if (!isHeader) ContextCompat.getColor(ctx,
                if (net >= 0) R.color.color_income else R.color.color_expense) else null))
        }
    }

    private fun divider() = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
    }
}
