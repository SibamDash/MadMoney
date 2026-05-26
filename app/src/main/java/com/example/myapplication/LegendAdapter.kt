package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class LegendItem(val label: String, val amount: Double, val color: Int)

class LegendAdapter(
    private val items: List<LegendItem>,
    private val total: Double
) : RecyclerView.Adapter<LegendAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View        = view.findViewById(R.id.dot)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvAmount: TextView   = view.findViewById(R.id.tvAmount)
        val tvPercent: TextView  = view.findViewById(R.id.tvPercent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_legend, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.dot.setBackgroundColor(item.color)
        holder.tvCategory.text = item.label
        holder.tvAmount.text   = "₹${item.amount.toInt()}"
        holder.tvPercent.text  = "${"%.1f".format(item.amount / total * 100)}%"
    }
}
