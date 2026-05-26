package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

sealed class GroupedRow {
    data class Header(val dateLabel: String, val dayTotal: Double, val types: List<String>) : GroupedRow()
    data class Item(val transaction: Transaction) : GroupedRow()
}

class GroupedTransactionAdapter(
    private val onCheckClick: (Transaction) -> Unit,
    private val onCrossClick: (Transaction) -> Unit,
    private val onStarToggle: (Transaction, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<GroupedRow>()
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val headerFmt = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())

    fun submitList(transactions: List<Transaction>) {
        rows.clear()
        val grouped = transactions.sortedByDescending { it.date }
            .groupBy { dayFmt.format(Date(it.date)) }
            .entries.sortedByDescending { it.key }

        for ((_, items) in grouped) {
            val income  = items.filter { it.type == "income" }.sumOf { it.amount }
            val expense = items.filter { it.type == "expense" }.sumOf { it.amount }
            val firstDate = Date(items.first().date)
            val label = headerFmt.format(firstDate)
            rows.add(GroupedRow.Header(label, income - expense, items.map { it.type }))
            items.forEach { rows.add(GroupedRow.Item(it)) }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (rows[position] is GroupedRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0)
            HeaderVH(inflater.inflate(R.layout.item_expense_header, parent, false))
        else
            ItemVH(inflater.inflate(R.layout.item_transaction, parent, false))
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is GroupedRow.Header -> {
                holder as HeaderVH
                holder.tvDayLabel.text = row.dateLabel
                val net = row.dayTotal
                holder.tvDayTotal.text = if (net >= 0) "+₹${net.toInt()}" else "-₹${(-net).toInt()}"
                holder.tvDayTotal.setTextColor(ContextCompat.getColor(holder.itemView.context,
                    if (net >= 0) R.color.color_income else R.color.color_expense))
            }
            is GroupedRow.Item -> {
                holder as ItemVH
                val t = row.transaction
                val ctx = holder.itemView.context
                val settledFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

                holder.tvTitle.text = t.title
                holder.tvAmount.text = "₹${t.amount.toInt()}"
                holder.tvSubtitle.text = timeFmt.format(Date(t.date))

                when (t.type.lowercase()) {
                    "income" -> {
                        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_income))
                        holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_income))
                        holder.actionButtons.visibility = View.GONE
                        holder.tvSettledAt.visibility = View.GONE
                    }
                    "expense" -> {
                        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_expense))
                        holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_expense))
                        holder.actionButtons.visibility = View.GONE
                        holder.tvSettledAt.visibility = View.GONE
                    }
                    "togive" -> {
                        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_give))
                        holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_to_give))
                        holder.actionButtons.visibility = if (t.isCompleted) View.GONE else View.VISIBLE
                        if (t.isCompleted && t.completedAt > 0) {
                            holder.tvSettledAt.text = "Settled on ${settledFmt.format(Date(t.completedAt))}"
                            holder.tvSettledAt.visibility = View.VISIBLE
                        } else holder.tvSettledAt.visibility = View.GONE
                    }
                    "toget" -> {
                        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_get))
                        holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_to_get))
                        holder.actionButtons.visibility = if (t.isCompleted) View.GONE else View.VISIBLE
                        if (t.isCompleted && t.completedAt > 0) {
                            holder.tvSettledAt.text = "Settled on ${settledFmt.format(Date(t.completedAt))}"
                            holder.tvSettledAt.visibility = View.VISIBLE
                        } else holder.tvSettledAt.visibility = View.GONE
                    }
                }

                if (t.isStarred) {
                    holder.ivStar.setImageResource(R.drawable.ic_star_filled)
                    holder.ivStar.setColorFilter(ContextCompat.getColor(ctx, R.color.text_primary))
                    holder.ivStar.visibility = View.VISIBLE
                } else {
                    holder.ivStar.visibility = View.GONE
                }

                holder.cardView.setOnClickListener(object : View.OnClickListener {
                    private var lastClick = 0L
                    override fun onClick(v: View) {
                        val now = System.currentTimeMillis()
                        if (now - lastClick < 300) onStarToggle(t, !t.isStarred)
                        lastClick = now
                    }
                })
                holder.btnCheck.setOnClickListener { onCheckClick(t) }
                holder.btnCross.setOnClickListener { onCrossClick(t) }
            }
        }
    }

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDayLabel: TextView = view.findViewById(R.id.tvDayLabel)
        val tvDayTotal: TextView = view.findViewById(R.id.tvDayTotal)
    }

    class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val tvSettledAt: TextView = view.findViewById(R.id.tvSettledAt)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val iconContainer: View = view.findViewById(R.id.iconContainer)
        val actionButtons: View = view.findViewById(R.id.actionButtons)
        val btnCheck: android.widget.ImageView = view.findViewById(R.id.btnCheck)
        val btnCross: android.widget.ImageView = view.findViewById(R.id.btnCross)
        val ivStar: android.widget.ImageView = view.findViewById(R.id.ivStar)
        val cardView: View = view.findViewById(R.id.cardView)
    }
}
