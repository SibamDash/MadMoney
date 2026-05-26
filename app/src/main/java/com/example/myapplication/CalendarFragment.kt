package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private val calendar = Calendar.getInstance()
    private lateinit var tvMonthYear: TextView
    private lateinit var rvCalendar: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvMonthYear = view.findViewById(R.id.tvMonthYear)
        rvCalendar  = view.findViewById(R.id.rvCalendar)

        view.findViewById<View>(R.id.ivPrevMonth).setOnClickListener { calendar.add(Calendar.MONTH, -1); loadCalendar() }
        view.findViewById<View>(R.id.ivNextMonth).setOnClickListener { calendar.add(Calendar.MONTH,  1); loadCalendar() }
        view.findViewById<View>(R.id.layoutMonthPicker).setOnClickListener { showMonthYearPicker() }
        loadCalendar()
    }

    private fun showMonthYearPicker() {
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val mp = NumberPicker(requireContext()).apply { minValue=0; maxValue=11; displayedValues=months; value=calendar.get(Calendar.MONTH); wrapSelectorWheel=true }
        val yp = NumberPicker(requireContext()).apply { minValue=2000; maxValue=2100; value=calendar.get(Calendar.YEAR); wrapSelectorWheel=false }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(32,16,32,16)
            addView(mp, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(yp, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        AlertDialog.Builder(requireContext()).setTitle("Select Month").setView(container)
            .setPositiveButton("Go") { _, _ -> calendar.set(Calendar.MONTH, mp.value); calendar.set(Calendar.YEAR, yp.value); loadCalendar() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun loadCalendar() {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvMonthYear.text = fmt.format(calendar.time)

        val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0) }
        val monthEnd   = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59) }

        val transactions = DatabaseHelper(requireContext()).getTransactions(monthStart.timeInMillis, monthEnd.timeInMillis)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayMap = mutableMapOf<String, DaySummary>()
        for (t in transactions) {
            val key = dayFmt.format(Date(t.date))
            val current = dayMap.getOrDefault(key, DaySummary())
            when (t.type) {
                "income"          -> current.income += t.amount
                "expense"         -> current.expense += t.amount
                "togive", "toget" -> current.debt += t.amount
            }
            dayMap[key] = current
        }

        val firstDow = monthStart.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = monthEnd.get(Calendar.DAY_OF_MONTH)
        val cells = ArrayList<Int?>().apply { repeat(firstDow) { add(null) }; for (d in 1..daysInMonth) add(d) }

        val today = Calendar.getInstance()
        val todayDay = if (today.get(Calendar.YEAR)==calendar.get(Calendar.YEAR) && today.get(Calendar.MONTH)==calendar.get(Calendar.MONTH))
            today.get(Calendar.DAY_OF_MONTH) else -1

        val year  = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.adapter = CalendarAdapter(cells, dayMap, year, month, todayDay) { day ->
            // Switch to Daily tab and filter by that day — pass via MainActivity
            (activity as? MainActivity)?.navigateToDailyForDate(year, month - 1, day)
        }
    }
}
