package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class CalendarAdapter(
    private val cells: List<Int?>,
    private val dayMap: Map<String, DaySummary>,
    private val year: Int,
    private val month: Int,
    private val todayDay: Int,
    private val onDayClick: (Int) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tvDay)
        val tvIncome: TextView = view.findViewById(R.id.tvIncome)
        val tvExpense: TextView = view.findViewById(R.id.tvExpense)
        val tvDebt: TextView = view.findViewById(R.id.tvDebt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false))

    override fun getItemCount() = cells.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val day = cells[position]

        if (day == null) {
            holder.tvDay.text = ""
            holder.tvIncome.text = ""
            holder.tvExpense.text = ""
            holder.tvDebt.text = ""
            holder.itemView.background = null
            holder.itemView.setOnClickListener(null)
            return
        }

        holder.tvDay.text = day.toString()

        val context = holder.itemView.context
        if (day == todayDay) {
            holder.tvDay.setBackgroundResource(R.drawable.bg_toggle_selected)
            holder.tvDay.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            holder.tvDay.background = null
            holder.tvDay.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }

        val key = "%04d-%02d-%02d".format(Locale.US, year, month, day)
        val summary = dayMap[key]

        holder.tvIncome.text  = if (summary != null && summary.income > 0) "+${summary.income.toInt()}" else ""
        holder.tvExpense.text = if (summary != null && summary.expense > 0) "-${summary.expense.toInt()}" else ""
        holder.tvDebt.text    = if (summary != null && summary.debt > 0) "~${summary.debt.toInt()}" else ""

        holder.itemView.setOnClickListener { onDayClick(day) }
    }
}
