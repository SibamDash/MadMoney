package com.example.myapplication

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

data class DayGroup(val dateKey: String, val items: List<Transaction>)

class ExpenseActivity : AppCompatActivity() {

    private val calendar = Calendar.getInstance()
    private var selectedDayCalendar: Calendar? = null
    private lateinit var filterTypes: List<String>
    private lateinit var sectionTitle: String
    private val allTransactions = mutableListOf<Transaction>()
    private val displayRows = mutableListOf<Any>() // Row.Header or Row.Item
    private lateinit var rowAdapter: ExpenseDayAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense)

        filterTypes = intent.getStringArrayListExtra(EXTRA_TYPES) ?: arrayListOf("expense")
        sectionTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Expenses"

        findViewById<TextView>(R.id.tvSectionTitle).text = sectionTitle

        rowAdapter = ExpenseDayAdapter(displayRows)
        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@ExpenseActivity)
            adapter = rowAdapter
        }

        setupMonthNavigation()
        setupSearch()
        setupBottomNavigation()

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        loadTransactions()
    }

    private fun loadTransactions() {
        val (start, end) = getRange()
        val data = DatabaseHelper(this).getTransactions(start, end)
            .filter { it.type.lowercase() in filterTypes }
            .sortedByDescending { it.date }
        allTransactions.clear()
        allTransactions.addAll(data)
        applyFilter(null)
        updateDateLabel()
    }

    private fun applyFilter(query: String?) {
        val q = query?.trim()?.lowercase() ?: ""
        val filtered = if (q.isEmpty()) allTransactions else allTransactions.filter {
            it.title.lowercase().contains(q) ||
            it.category.lowercase().contains(q) ||
            it.type.lowercase().contains(q) ||
            it.note.lowercase().contains(q) ||
            it.account.lowercase().contains(q) ||
            it.amount.toString().contains(q)
        }
        val total = filtered.sumOf { it.amount }
        findViewById<TextView>(R.id.tvTotalExpense).text = "₹%.2f".format(total)

        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val grouped = filtered.groupBy { dayFmt.format(Date(it.date)) }
            .entries.sortedByDescending { it.key }

        displayRows.clear()
        grouped.forEach { (key, items) ->
            displayRows.add(Row.Header(key, items.sumOf { it.amount }))
            items.forEach { displayRows.add(Row.Item(it)) }
        }
        rowAdapter.notifyDataSetChanged()
    }

    private fun setupSearch() {
        val searchView = findViewById<SearchView>(R.id.searchView)
        val ivSearch = findViewById<android.widget.ImageView>(R.id.ivSearch)

        ivSearch.setOnClickListener {
            if (searchView.visibility == View.GONE) {
                searchView.visibility = View.VISIBLE
                searchView.isIconified = false
                searchView.requestFocus()
            } else {
                searchView.visibility = View.GONE
                searchView.setQuery("", false)
                applyFilter(null)
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean { applyFilter(q); return true }
        })

        searchView.setOnCloseListener {
            searchView.visibility = View.GONE
            applyFilter(null)
            false
        }
    }

    private fun setupMonthNavigation() {
        findViewById<android.widget.ImageView>(R.id.ivBack).setOnClickListener {
            selectedDayCalendar = null
            calendar.add(Calendar.MONTH, -1)
            loadTransactions()
        }
        findViewById<android.widget.ImageView>(R.id.ivForward).setOnClickListener {
            selectedDayCalendar = null
            calendar.add(Calendar.MONTH, 1)
            loadTransactions()
        }
        findViewById<TextView>(R.id.tvDateRange).setOnClickListener {
            val ref = selectedDayCalendar ?: calendar
            DatePickerDialog(this, { _, year, month, day ->
                selectedDayCalendar = Calendar.getInstance().apply { set(year, month, day) }
                calendar.set(year, month, day)
                loadTransactions()
            }, ref.get(Calendar.YEAR), ref.get(Calendar.MONTH), ref.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun updateDateLabel() {
        val fmt = if (selectedDayCalendar != null) SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                  else SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val ref = selectedDayCalendar ?: calendar
        findViewById<TextView>(R.id.tvDateRange).text = fmt.format(ref.time)
    }

    private fun getRange(): Pair<Long, Long> {
        val day = selectedDayCalendar
        return if (day != null) {
            val s = (day.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            val e = (day.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }
            Pair(s.timeInMillis, e.timeInMillis)
        } else {
            val s = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            val e = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }
            Pair(s.timeInMillis, e.timeInMillis)
        }
    }

    private fun setupBottomNavigation() {
        val navView = findViewById<BottomNavigationView>(R.id.bottomNav)
        navView.selectedItemId = when (filterTypes.firstOrNull()) {
            "income" -> R.id.nav_expense
            "togive", "toget" -> R.id.nav_debts
            else -> R.id.nav_income
        }
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_all -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_income -> {
                    if ("expense" !in filterTypes) {
                        startActivity(intentFor("expense", "Expenses", R.id.nav_income)); finish()
                    }
                    true
                }
                R.id.nav_expense -> {
                    if ("income" !in filterTypes) {
                        startActivity(intentFor("income", "Income", R.id.nav_expense)); finish()
                    }
                    true
                }
                R.id.nav_debts -> {
                    if ("togive" !in filterTypes) {
                        startActivity(intentForDebts()); finish()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun intentFor(type: String, title: String, @Suppress("UNUSED_PARAMETER") navId: Int) =
        Intent(this, ExpenseActivity::class.java)
            .putStringArrayListExtra(EXTRA_TYPES, arrayListOf(type))
            .putExtra(EXTRA_TITLE, title)

    private fun intentForDebts() =
        Intent(this, ExpenseActivity::class.java)
            .putStringArrayListExtra(EXTRA_TYPES, arrayListOf("togive", "toget"))
            .putExtra(EXTRA_TITLE, "Debts")

    companion object {
        const val EXTRA_TYPES = "extra_types"
        const val EXTRA_TITLE = "extra_title"
    }
}

sealed class Row {
    data class Header(val dateKey: String, val total: Double) : Row()
    data class Item(val t: Transaction) : Row()
}

class ExpenseDayAdapter(private val rows: MutableList<Any>) : RecyclerView.Adapter<ExpenseDayAdapter.VH>() {

    private val dayLabelFmt = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class VH(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 0) R.layout.item_expense_header else R.layout.item_expense_row
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(row.dateKey)!!
                holder.itemView.findViewById<TextView>(R.id.tvDayLabel).text = dayLabelFmt.format(date)
                holder.itemView.findViewById<TextView>(R.id.tvDayTotal).text = "₹${row.total.toInt()}"
            }
            is Row.Item -> {
                holder.itemView.findViewById<TextView>(R.id.tvTitle).text = row.t.title
                holder.itemView.findViewById<TextView>(R.id.tvCategory).text = row.t.category
                holder.itemView.findViewById<TextView>(R.id.tvTime).text = timeFmt.format(Date(row.t.date))
                holder.itemView.findViewById<TextView>(R.id.tvAmount).text = "₹${row.t.amount.toInt()}"
            }
        }
    }

    override fun getItemCount() = rows.size
}
