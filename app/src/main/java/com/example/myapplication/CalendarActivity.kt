package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : AppCompatActivity() {

    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_calendar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<View>(R.id.ivPrevMonth).setOnClickListener {
            calendar.add(Calendar.MONTH, -1); loadCalendar()
        }
        findViewById<View>(R.id.ivNextMonth).setOnClickListener {
            calendar.add(Calendar.MONTH, 1); loadCalendar()
        }
        findViewById<TextView>(R.id.tvMonthYear).setOnClickListener { showMonthYearPicker() }
        findViewById<View>(R.id.layoutMonthPicker).setOnClickListener { showMonthYearPicker() }
        loadCalendar()
    }

    private fun showMonthYearPicker() {
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val monthPicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 11
            displayedValues = months
            value = calendar.get(Calendar.MONTH)
            wrapSelectorWheel = true
        }
        val yearPicker = NumberPicker(this).apply {
            minValue = 2000; maxValue = 2100
            value = calendar.get(Calendar.YEAR)
            wrapSelectorWheel = false
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
            addView(monthPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(yearPicker,  LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        AlertDialog.Builder(this)
            .setTitle("Select Month")
            .setView(container)
            .setPositiveButton("Go") { _, _ ->
                calendar.set(Calendar.MONTH, monthPicker.value)
                calendar.set(Calendar.YEAR, yearPicker.value)
                loadCalendar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCalendar() {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        findViewById<TextView>(R.id.tvMonthYear).text = fmt.format(calendar.time)

        val monthStart = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val monthEnd = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }

        val transactions = DatabaseHelper(this).getTransactions(monthStart.timeInMillis, monthEnd.timeInMillis)

        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayMap = mutableMapOf<String, Triple<Double, Double, Double>>()
        for (t in transactions) {
            val key = dayFmt.format(Date(t.date))
            val (inc, exp, debt) = dayMap.getOrDefault(key, Triple(0.0, 0.0, 0.0))
            dayMap[key] = when (t.type) {
                "income"          -> Triple(inc + t.amount, exp, debt)
                "expense"         -> Triple(inc, exp + t.amount, debt)
                "togive", "toget" -> Triple(inc, exp, debt + t.amount)
                else              -> Triple(inc, exp, debt)
            }
        }

        val firstDayOfWeek = monthStart.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = monthEnd.get(Calendar.DAY_OF_MONTH)
        val cells = ArrayList<Int?>().apply {
            repeat(firstDayOfWeek) { add(null) }
            for (d in 1..daysInMonth) add(d)
        }

        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                             today.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
        val todayDay = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else -1

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        findViewById<RecyclerView>(R.id.rvCalendar).apply {
            layoutManager = GridLayoutManager(this@CalendarActivity, 7)
            adapter = CalendarAdapter(cells, dayMap, year, month, todayDay) { day ->
                val intent = Intent(this@CalendarActivity, MainActivity::class.java)
                intent.putExtra("selected_date_millis",
                    Calendar.getInstance().apply { set(year, month - 1, day) }.timeInMillis)
                startActivity(intent)
            }
        }
    }
}

class CalendarAdapter(
    private val cells: List<Int?>,
    private val dayMap: Map<String, Triple<Double, Double, Double>>,
    private val year: Int,
    private val month: Int,
    private val todayDay: Int,
    private val onDayClick: (Int) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false))

    override fun getItemCount() = cells.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val day = cells[position]
        val tvDay     = holder.itemView.findViewById<TextView>(R.id.tvDay)
        val tvIncome  = holder.itemView.findViewById<TextView>(R.id.tvIncome)
        val tvExpense = holder.itemView.findViewById<TextView>(R.id.tvExpense)
        val tvDebt    = holder.itemView.findViewById<TextView>(R.id.tvDebt)

        if (day == null) {
            tvDay.text = ""; tvIncome.text = ""; tvExpense.text = ""; tvDebt.text = ""
            holder.itemView.background = null
            holder.itemView.setOnClickListener(null)
            return
        }

        tvDay.text = day.toString()

        if (day == todayDay) {
            tvDay.setBackgroundResource(R.drawable.bg_toggle_selected)
            tvDay.setTextColor(android.graphics.Color.WHITE)
        } else {
            tvDay.background = null
            tvDay.setTextColor(holder.itemView.context.getColor(R.color.text_primary))
        }

        val key = "%04d-%02d-%02d".format(year, month, day)
        val (inc, exp, debt) = dayMap.getOrDefault(key, Triple(0.0, 0.0, 0.0))

        tvIncome.text  = if (inc  > 0) "+${inc.toInt()}"  else ""
        tvExpense.text = if (exp  > 0) "-${exp.toInt()}"  else ""
        tvDebt.text    = if (debt > 0) "~${debt.toInt()}" else ""

        holder.itemView.setOnClickListener { onDayClick(day) }
    }
}
