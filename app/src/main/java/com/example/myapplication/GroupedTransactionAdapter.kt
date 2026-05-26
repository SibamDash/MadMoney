package com.example.myapplication

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class GroupedRow {
    data class Header(val dateLabel: String, val dayTotal: Double, val types: List<String>) : GroupedRow()
    data class Item(val transaction: Transaction) : GroupedRow()
}

class GroupedTransactionAdapter(
    private val onCheckClick: (Transaction) -> Unit,
    private val onCrossClick: (Transaction) -> Unit,
    private val onStarToggle: (Transaction, Boolean) -> Unit,
    private val onLongClick: (Transaction, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<GroupedRow>()
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val headerFmt = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())

    private val defaultEmojis = mapOf(
        "Food" to "🍔", "Social Life" to "🎉", "Pets" to "🐾", "Transport" to "🚗",
        "Health" to "💊", "Education" to "📚", "Gift" to "🎁", "Apparel" to "👗"
    )
    private val defaultIncomeEmojis = mapOf(
        "Allowance" to "💰", "Salary" to "💼", "Cash" to "💵", "Bonus" to "🎯"
    )

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

    private fun loadCategoryIcon(ctx: Context, holder: ItemVH, categoryName: String, isIncome: Boolean) {
        val prefsKey = if (isIncome) "income_cat_icons" else "cat_icons"
        val catPrefsKey = if (isIncome) "income_categories" else "categories"
        val defaultEmojiMap = if (isIncome) defaultIncomeEmojis else defaultEmojis

        val catPrefs = ctx.getSharedPreferences(catPrefsKey, Context.MODE_PRIVATE)
        val iconPrefs = ctx.getSharedPreferences(prefsKey, Context.MODE_PRIVATE)

        // Find the index of this category by name
        val count = catPrefs.getStringSet("list", null)?.size ?: 0
        val index = (0 until count).firstOrNull { catPrefs.getString("cat_$it", null) == categoryName }

        if (index != null) {
            val imagePath = iconPrefs.getString("icon_$index", null)
            if (imagePath != null && File(imagePath).exists()) {
                holder.ivCategoryIcon.setImageBitmap(BitmapFactory.decodeFile(imagePath))
                holder.ivCategoryIcon.clearColorFilter()
                holder.tvCategoryEmoji.visibility = View.GONE
                holder.ivCategoryIcon.visibility = View.VISIBLE
                return
            }
            val emoji = iconPrefs.getString("emoji_$index", defaultEmojiMap[categoryName])
            if (emoji != null) {
                holder.tvCategoryEmoji.text = emoji
                holder.tvCategoryEmoji.visibility = View.VISIBLE
                holder.ivCategoryIcon.visibility = View.GONE
                return
            }
        }

        // Fallback: default emoji or first letter
        val emoji = defaultEmojiMap[categoryName]
        if (emoji != null) {
            holder.tvCategoryEmoji.text = emoji
            holder.tvCategoryEmoji.visibility = View.VISIBLE
            holder.ivCategoryIcon.visibility = View.GONE
        } else {
            holder.tvCategoryEmoji.visibility = View.GONE
            holder.ivCategoryIcon.setImageResource(R.drawable.ic_list)
            holder.ivCategoryIcon.visibility = View.VISIBLE
        }
    }

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

                // Show note as title if available, else fall back to category/title
                holder.tvTitle.text = if (t.note.isNotBlank()) t.note else t.title
                holder.tvAmount.text = "₹${t.amount.toInt()}"
                holder.tvSubtitle.text = timeFmt.format(Date(t.date))

                when (t.type.lowercase()) {
                    "income" -> {
                        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_income))
                        holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_income))
                        holder.actionButtons.visibility = View.GONE
                        holder.tvSettledAt.visibility = View.GONE
                        loadCategoryIcon(ctx, holder, t.category, isIncome = true)
                    }
                    "expense" -> {
                        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_expense))
                        holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_expense))
                        holder.actionButtons.visibility = View.GONE
                        holder.tvSettledAt.visibility = View.GONE
                        loadCategoryIcon(ctx, holder, t.category, isIncome = false)
                    }
                    "togive" -> {
                        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_give))
                        holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_to_give))
                        holder.actionButtons.visibility = if (t.isCompleted) View.GONE else View.VISIBLE
                        if (t.isCompleted && t.completedAt > 0) {
                            holder.tvSettledAt.text = "Settled on ${settledFmt.format(Date(t.completedAt))}"
                            holder.tvSettledAt.visibility = View.VISIBLE
                        } else holder.tvSettledAt.visibility = View.GONE
                        holder.tvCategoryEmoji.visibility = View.GONE
                        holder.ivCategoryIcon.setImageResource(R.drawable.ic_list)
                        holder.ivCategoryIcon.visibility = View.VISIBLE
                    }
                    "toget" -> {
                        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, R.color.color_to_get))
                        holder.iconContainer.background.setTint(ContextCompat.getColor(ctx, R.color.color_to_get))
                        holder.actionButtons.visibility = if (t.isCompleted) View.GONE else View.VISIBLE
                        if (t.isCompleted && t.completedAt > 0) {
                            holder.tvSettledAt.text = "Settled on ${settledFmt.format(Date(t.completedAt))}"
                            holder.tvSettledAt.visibility = View.VISIBLE
                        } else holder.tvSettledAt.visibility = View.GONE
                        holder.tvCategoryEmoji.visibility = View.GONE
                        holder.ivCategoryIcon.setImageResource(R.drawable.ic_list)
                        holder.ivCategoryIcon.visibility = View.VISIBLE
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
                holder.cardView.setOnLongClickListener {
                    val popup = android.widget.PopupMenu(it.context, it)
                    popup.menu.add(0, 0, 0, "✏️ Edit")
                    popup.menu.add(0, 1, 1, "🗑️ Delete")
                    popup.setOnMenuItemClickListener { item ->
                        onLongClick(t, item.itemId)
                        true
                    }
                    popup.show()
                    true
                }
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
        val ivCategoryIcon: ImageView = view.findViewById(R.id.ivCategoryIcon)
        val tvCategoryEmoji: TextView = view.findViewById(R.id.tvCategoryEmoji)
        val actionButtons: View = view.findViewById(R.id.actionButtons)
        val btnCheck: ImageView = view.findViewById(R.id.btnCheck)
        val btnCross: ImageView = view.findViewById(R.id.btnCross)
        val ivStar: ImageView = view.findViewById(R.id.ivStar)
        val cardView: View = view.findViewById(R.id.cardView)
    }
}
