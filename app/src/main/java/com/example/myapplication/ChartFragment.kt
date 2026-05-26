package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class ChartFragment : Fragment() {

    private val palette = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#3F51B5",
        "#2196F3", "#009688", "#4CAF50", "#FF9800", "#795548"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_chart, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val cal = Calendar.getInstance()
        val start = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0) }
        val end   = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59) }
        val transactions = DatabaseHelper(requireContext()).getTransactions(start.timeInMillis, end.timeInMillis)

        val grouped = listOf(
            "Expense" to transactions.filter { it.type == "expense" }.sumOf { it.amount },
            "Income"  to transactions.filter { it.type == "income" }.sumOf { it.amount },
            "Debts"   to transactions.filter { it.type == "togive" || it.type == "toget" }.sumOf { it.amount }
        ).filter { it.second > 0 }

        val total = grouped.sumOf { it.second }.takeIf { it > 0 } ?: 1.0
        val slices = grouped.mapIndexed { i, (_, amount) ->
            Pair((amount / total * 360f).toFloat(), Color.parseColor(palette[i % palette.size]))
        }
        view.findViewById<PieChartView>(R.id.pieChart).setData(slices)

        view.findViewById<RecyclerView>(R.id.rvLegend).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = LegendAdapter(grouped.mapIndexed { i, (label, amount) ->
                Pair(label, Pair(amount, Color.parseColor(palette[i % palette.size])))
            }, total)
        }
    }
}
