package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val transactions: List<Transaction>,
    private val onCheckClick: (Transaction) -> Unit,
    private val onCrossClick: (Transaction) -> Unit,
    private val onStarToggle: (Transaction, Boolean) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val settledFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: View = view.findViewById(R.id.iconContainer)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val tvSettledAt: TextView = view.findViewById(R.id.tvSettledAt)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val actionButtons: View = view.findViewById(R.id.actionButtons)
        val btnCheck: ImageView = view.findViewById(R.id.btnCheck)
        val btnCross: ImageView = view.findViewById(R.id.btnCross)
        val ivStar: ImageView = view.findViewById(R.id.ivStar)
        val cardView: View = view.findViewById(R.id.cardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))

    override fun getItemCount() = transactions.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = transactions[position]
        val ctx = holder.itemView.context
        holder.tvTitle.text = item.title
        holder.tvAmount.text = "₹${item.amount.toInt()}"

        when (item.type.lowercase()) {
            "income" -> {
                holder.tvSubtitle.text = "Income • Received"
                holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_income))
                holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_income))
                holder.actionButtons.visibility = View.GONE
                holder.tvSettledAt.visibility = View.GONE
            }
            "expense" -> {
                holder.tvSubtitle.text = "Expense • Paid"
                holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_expense))
                holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_expense))
                holder.actionButtons.visibility = View.GONE
                holder.tvSettledAt.visibility = View.GONE
            }
            "togive" -> {
                holder.tvSubtitle.text = "To Give • Debt"
                holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_give))
                holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_to_give))
                holder.actionButtons.visibility = if (item.isCompleted) View.GONE else View.VISIBLE
                if (item.isCompleted && item.completedAt > 0) {
                    holder.tvSettledAt.text = "Settled on ${settledFmt.format(Date(item.completedAt))}"
                    holder.tvSettledAt.visibility = View.VISIBLE
                } else holder.tvSettledAt.visibility = View.GONE
            }
            "toget" -> {
                holder.tvSubtitle.text = "To Get • Credit"
                holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_get))
                holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_to_get))
                holder.actionButtons.visibility = if (item.isCompleted) View.GONE else View.VISIBLE
                if (item.isCompleted && item.completedAt > 0) {
                    holder.tvSettledAt.text = "Settled on ${settledFmt.format(Date(item.completedAt))}"
                    holder.tvSettledAt.visibility = View.VISIBLE
                } else holder.tvSettledAt.visibility = View.GONE
            }
        }

        holder.ivStar.visibility = if (item.isStarred) View.VISIBLE else View.GONE

        holder.cardView.setOnClickListener(object : View.OnClickListener {
            private var lastClick = 0L
            override fun onClick(v: View) {
                val now = System.currentTimeMillis()
                if (now - lastClick < 300) onStarToggle(item, !item.isStarred)
                lastClick = now
            }
        })
        holder.btnCheck.setOnClickListener { onCheckClick(item) }
        holder.btnCross.setOnClickListener { onCrossClick(item) }
    }
}
