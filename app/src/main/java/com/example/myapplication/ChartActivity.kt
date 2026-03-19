package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChartActivity : AppCompatActivity() {

    private val palette = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#3F51B5",
        "#2196F3", "#009688", "#4CAF50", "#FF9800", "#795548"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val (start, end) = currentMonthRange()
        val transactions = DatabaseHelper(this).getTransactions(start, end)

        val grouped = listOf(
            "Expense" to transactions.filter { it.type == "expense" }.sumOf { it.amount },
            "Income"  to transactions.filter { it.type == "income" }.sumOf { it.amount },
            "Debts"   to transactions.filter { it.type == "togive" || it.type == "toget" }.sumOf { it.amount }
        ).filter { it.second > 0 }

        val total = grouped.sumOf { it.second }.takeIf { it > 0 } ?: 1.0

        val slices = grouped.mapIndexed { i, (_, amount) ->
            Pair((amount / total * 360f).toFloat(), Color.parseColor(palette[i % palette.size]))
        }
        findViewById<PieChartView>(R.id.pieChart).setData(slices)

        val legendItems = grouped.mapIndexed { i, (label, amount) ->
            Pair(label, Pair(amount, Color.parseColor(palette[i % palette.size])))
        }
        findViewById<RecyclerView>(R.id.rvLegend).apply {
            layoutManager = LinearLayoutManager(this@ChartActivity)
            adapter = LegendAdapter(legendItems, total)
        }
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        val start = (cal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
        }
        val end = (cal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
            set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 59)
        }
        return Pair(start.timeInMillis, end.timeInMillis)
    }
}

class LegendAdapter(
    private val items: List<Pair<String, Pair<Double, Int>>>,
    private val total: Double
) : RecyclerView.Adapter<LegendAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.dot)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvPercent: TextView = view.findViewById(R.id.tvPercent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_legend, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (cat, pair) = items[position]
        val (amount, color) = pair
        holder.dot.setBackgroundColor(color)
        holder.tvCategory.text = cat
        holder.tvAmount.text = "₹${amount.toInt()}"
        holder.tvPercent.text = "${"%.1f".format(amount / total * 100)}%"
    }

    override fun getItemCount() = items.size
}
