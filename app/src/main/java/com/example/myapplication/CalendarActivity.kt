package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : AppCompatActivity() {

    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<View>(R.id.ivPrevMonth).setOnClickListener {
            calendar.add(Calendar.MONTH, -1); loadCalendar()
        }
        findViewById<View>(R.id.ivNextMonth).setOnClickListener {
            calendar.add(Calendar.MONTH, 1); loadCalendar()
        }
        loadCalendar()
    }

    private fun loadCalendar() {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        findViewById<TextView>(R.id.tvMonthYear).text = fmt.format(calendar.time)

        val monthStart = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val monthEnd = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }

        val transactions = DatabaseHelper(this).getTransactions(monthStart.timeInMillis, monthEnd.timeInMillis)

        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        // Map of dateKey -> (expense, income)
        val dayMap = mutableMapOf<String, Pair<Double, Double>>()
        for (t in transactions) {
            val key = dayFmt.format(Date(t.date))
            val (exp, inc) = dayMap.getOrDefault(key, Pair(0.0, 0.0))
            when (t.type) {
                "expense" -> dayMap[key] = Pair(exp + t.amount, inc)
                "income"  -> dayMap[key] = Pair(exp, inc + t.amount)
            }
        }

        val firstDayOfWeek = monthStart.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
        val daysInMonth = monthEnd.get(Calendar.DAY_OF_MONTH)
        val totalCells = firstDayOfWeek + daysInMonth
        val cells = ArrayList<Int?>(totalCells).apply {
            repeat(firstDayOfWeek) { add(null) }
            for (d in 1..daysInMonth) add(d)
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        findViewById<RecyclerView>(R.id.rvCalendar).apply {
            layoutManager = GridLayoutManager(this@CalendarActivity, 7)
            adapter = CalendarAdapter(cells, dayMap, year, month)
        }
    }
}

class CalendarAdapter(
    private val cells: List<Int?>,
    private val dayMap: Map<String, Pair<Double, Double>>,
    private val year: Int,
    private val month: Int
) : RecyclerView.Adapter<CalendarAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false))

    override fun getItemCount() = cells.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val day = cells[position]
        val tvDay = holder.itemView.findViewById<TextView>(R.id.tvDay)
        val tvExpense = holder.itemView.findViewById<TextView>(R.id.tvExpense)
        val tvBalance = holder.itemView.findViewById<TextView>(R.id.tvBalance)

        if (day == null) {
            tvDay.text = ""; tvExpense.text = ""; tvBalance.text = ""
            return
        }

        tvDay.text = day.toString()
        val key = "%04d-%02d-%02d".format(year, month, day)
        val (exp, inc) = dayMap.getOrDefault(key, Pair(0.0, 0.0))
        tvExpense.text = if (exp > 0) "-₹${exp.toInt()}" else ""
        tvBalance.text = if (inc > 0) "+₹${inc.toInt()}" else ""
    }
}
