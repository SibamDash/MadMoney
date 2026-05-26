package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class ChartFragment : Fragment() {

    private val expensePalette = listOf(
        "#F44336", "#FF6D00", "#FFD600", "#00C853",
        "#00B0FF", "#651FFF", "#F50057", "#00BFA5",
        "#FF6F00", "#1565C0", "#6A1B9A", "#2E7D32"
    )
    private val incomePalette = listOf(
        "#00C853", "#00B0FF", "#651FFF", "#FFD600",
        "#F44336", "#FF6D00", "#00BFA5", "#F50057",
        "#1565C0", "#FF6F00", "#6A1B9A", "#2E7D32"
    )

    private var selectedType = "expense"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_chart, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val btnExpense = view.findViewById<TextView>(R.id.btnChartExpense)
        val btnIncome  = view.findViewById<TextView>(R.id.btnChartIncome)

        fun selectType(type: String) {
            selectedType = type
            val selColor   = ContextCompat.getColor(requireContext(), R.color.color_expense)
                .takeIf { type == "expense" }
                ?: ContextCompat.getColor(requireContext(), R.color.color_income)
            val unselText  = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            val white      = ContextCompat.getColor(requireContext(), android.R.color.white)

            btnExpense.setBackgroundColor(if (type == "expense") selColor else Color.TRANSPARENT)
            btnIncome.setBackgroundColor(if (type == "income")
                ContextCompat.getColor(requireContext(), R.color.color_income) else Color.TRANSPARENT)
            btnExpense.setTextColor(if (type == "expense") white else unselText)
            btnIncome.setTextColor(if (type == "income") white else unselText)
            load(view)
        }

        btnExpense.setOnClickListener { selectType("expense") }
        btnIncome.setOnClickListener  { selectType("income") }
        selectType("expense")
    }

    override fun onResume() {
        super.onResume()
        view?.let { load(it) }
    }

    private fun load(view: View) {
        val cal   = Calendar.getInstance()
        val start = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val end = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }

        val palette = if (selectedType == "income") incomePalette else expensePalette
        val txns = DatabaseHelper(requireContext())
            .getTransactions(start.timeInMillis, end.timeInMillis)
            .filter { it.type == selectedType }

        val grouped = txns
            .groupBy { it.category }
            .map { (cat, list) -> cat to list.sumOf { it.amount } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        val total = grouped.sumOf { it.second }.takeIf { it > 0 } ?: 1.0

        val slices = grouped.mapIndexed { i, (_, amount) ->
            Pair((amount / total * 360f).toFloat(), Color.parseColor(palette[i % palette.size]))
        }

        view.findViewById<PieChartView>(R.id.pieChart).apply {
            setData(slices, "₹${total.toInt()}")
        }

        view.findViewById<RecyclerView>(R.id.rvLegend).apply {
            if (layoutManager == null) layoutManager = LinearLayoutManager(requireContext())
            adapter = LegendAdapter(
                grouped.mapIndexed { i, (label, amount) ->
                    LegendItem(label, amount, Color.parseColor(palette[i % palette.size]))
                },
                total
            )
        }
    }
}
