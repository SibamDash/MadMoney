package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var groupedAdapter: GroupedTransactionAdapter
    private val allTransactions = mutableListOf<Transaction>()
    private var activeFilter: String = "all"

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        BackupManager.exportToJson(this, uri).fold(
            onSuccess = { count -> Toast.makeText(this, "Exported $count transactions", Toast.LENGTH_SHORT).show() },
            onFailure = { Toast.makeText(this, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show() }
        )
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        BackupManager.importFromJson(this, uri).fold(
            onSuccess = { count ->
                Toast.makeText(this, "Imported $count transactions", Toast.LENGTH_SHORT).show()
                loadTransactions()
            },
            onFailure = { Toast.makeText(this, "Import failed: ${it.message}", Toast.LENGTH_SHORT).show() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupRecyclerView()
        setupBottomNavigation()
        setupTabLayout()
        setupSearch()
        setupSettings()

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadTransactions()
    }

    private var backPressedTime = 0L

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val searchView = findViewById<SearchView>(R.id.searchView)
        when {
            drawer.isDrawerOpen(Gravity.END) -> drawer.closeDrawer(Gravity.END)
            searchView.visibility == View.VISIBLE -> {
                searchView.visibility = View.GONE
                searchView.setQuery("", false)
                applyFilter(null)
            }
            System.currentTimeMillis() - backPressedTime < 2000 -> super.onBackPressed()
            else -> {
                backPressedTime = System.currentTimeMillis()
                Toast.makeText(this, "Click again to close the app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        groupedAdapter = GroupedTransactionAdapter(
            onCheckClick = { t ->
                DatabaseHelper(this).markCompleted(t.id)
                Toast.makeText(this, "${t.title} marked as settled", Toast.LENGTH_SHORT).show()
                loadTransactions()
            },
            onCrossClick = { t ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Transaction")
                    .setMessage("Are you sure you want to delete \"${t.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        DatabaseHelper(this).deleteTransaction(t.id)
                        loadTransactions()
                        com.google.android.material.snackbar.Snackbar.make(
                            findViewById(R.id.main), "${t.title} deleted", 5000
                        ).setAction("Undo") {
                            DatabaseHelper(this).addTransaction(t)
                            loadTransactions()
                        }.show()
                    }
                    .setNegativeButton("Cancel", null).show()
            },
            onStarToggle = { t, starred ->
                DatabaseHelper(this).toggleStar(t.id, starred)
                loadTransactions()
            }
        )
        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = groupedAdapter
        }
    }

    private fun loadTransactions() {
        // Load all transactions (no date filter) — full history
        val cal = Calendar.getInstance()
        val start = Calendar.getInstance().apply { set(2000, 0, 1, 0, 0, 0) }.timeInMillis
        val end = cal.apply { set(Calendar.YEAR, 2100) }.timeInMillis
        val data = DatabaseHelper(this).getTransactions(start, end)
        allTransactions.clear()
        allTransactions.addAll(data)
        applyFilter(null)
        updateSummary(data)
    }

    private fun applyFilter(query: String?) {
        val q = query?.trim()?.lowercase() ?: ""
        var filtered = when (activeFilter) {
            "expense" -> allTransactions.filter { it.type == "expense" }
            "income"  -> allTransactions.filter { it.type == "income" }
            "debts"   -> allTransactions.filter { it.type == "togive" || it.type == "toget" }
            else      -> allTransactions.toList()
        }
        if (q.isNotEmpty()) {
            filtered = filtered.filter {
                it.title.lowercase().contains(q) || it.category.lowercase().contains(q) ||
                it.note.lowercase().contains(q) || it.amount.toString().contains(q)
            }
        }
        groupedAdapter.submitList(filtered)
    }

    private fun updateSummary(data: List<Transaction>) {
        // Summary shows current month only
        val cal = Calendar.getInstance()
        val monthStart = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val monthEnd   = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }
        val thisMonth  = data.filter { it.date in monthStart.timeInMillis..monthEnd.timeInMillis }
        val income  = thisMonth.filter { it.type == "income" }.sumOf { it.amount }
        val expense = thisMonth.filter { it.type == "expense" }.sumOf { it.amount }
        findViewById<TextView>(R.id.tvIncomeAmount).text = "₹%.2f".format(income)
        findViewById<TextView>(R.id.tvExpenseAmount).text = "₹%.2f".format(expense)
        findViewById<TextView>(R.id.tvTotalAmount).text = "₹%.2f".format(income - expense)
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

    private fun setupSettings() {
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val sidebar = drawer.getChildAt(1)
        val screenWidth = resources.displayMetrics.widthPixels
        val collapsedWidth = (280 * resources.displayMetrics.density).toInt()
        var isExpanded = false

        findViewById<android.widget.ImageView>(R.id.ivSettings).setOnClickListener {
            drawer.openDrawer(Gravity.END)
        }

        findViewById<TextView>(R.id.tvSettingsHeader).setOnClickListener(object : View.OnClickListener {
            var lastClick = 0L
            override fun onClick(v: View) {
                val now = System.currentTimeMillis()
                if (now - lastClick < 300) {
                    val toWidth = if (isExpanded) collapsedWidth else screenWidth
                    android.animation.ValueAnimator.ofInt(sidebar.layoutParams.width, toWidth).apply {
                        duration = 250
                        addUpdateListener { sidebar.layoutParams.width = it.animatedValue as Int; sidebar.requestLayout() }
                        start()
                    }
                    isExpanded = !isExpanded
                }
                lastClick = now
            }
        })

        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                if (isExpanded) { sidebar.layoutParams.width = collapsedWidth; sidebar.requestLayout(); isExpanded = false }
            }
        })

        fun toggleSection(content: LinearLayout, arrow: TextView) {
            content.visibility = if (content.visibility == View.GONE) View.VISIBLE.also { arrow.text = "▼" }
                                  else View.GONE.also { arrow.text = "▶" }
        }

        findViewById<TextView>(R.id.headerSavedLogs).setOnClickListener {
            drawer.closeDrawer(Gravity.END)
            startActivity(Intent(this, SavedLogsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.headerCustomize).setOnClickListener {
            toggleSection(findViewById(R.id.contentCustomize), findViewById(R.id.arrowCustomize))
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val switchDarkMode = findViewById<SwitchCompat>(R.id.switchDarkMode)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        findViewById<TextView>(R.id.optionTransitions).setOnClickListener { Toast.makeText(this, "Transitions", Toast.LENGTH_SHORT).show(); drawer.closeDrawer(Gravity.END) }
        findViewById<TextView>(R.id.optionPosition).setOnClickListener { Toast.makeText(this, "Position", Toast.LENGTH_SHORT).show(); drawer.closeDrawer(Gravity.END) }

        findViewById<LinearLayout>(R.id.headerBackup).setOnClickListener {
            toggleSection(findViewById(R.id.contentBackup), findViewById(R.id.arrowBackup))
        }
        findViewById<TextView>(R.id.optionExport).setOnClickListener {
            drawer.closeDrawer(Gravity.END)
            exportLauncher.launch("backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json")
        }
        findViewById<TextView>(R.id.optionImport).setOnClickListener {
            drawer.closeDrawer(Gravity.END)
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    private fun setupTabLayout() {
        findViewById<TabLayout>(R.id.tabLayout).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.text) {
                    "Calendar" -> startActivity(Intent(this@MainActivity, CalendarActivity::class.java))
                    "Graph"    -> startActivity(Intent(this@MainActivity, ChartActivity::class.java))
                    else -> Toast.makeText(this@MainActivity, "${tab?.text} view", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        fun updateIconScale(selectedId: Int) {
            for (i in 0 until nav.menu.size()) {
                val item = nav.menu.getItem(i)
                val scale = if (item.itemId == selectedId) 1.0f else 20f / 24f
                nav.findViewById<View>(item.itemId)?.animate()?.scaleX(scale)?.scaleY(scale)?.setDuration(150)?.start()
            }
        }
        nav.setOnItemSelectedListener { item ->
            activeFilter = when (item.itemId) {
                R.id.nav_income -> "expense"
                R.id.nav_expense -> "income"
                R.id.nav_debts -> "debts"
                else -> "all"
            }
            applyFilter(null)
            updateIconScale(item.itemId)
            true
        }
        nav.post { updateIconScale(nav.selectedItemId) }
    }
}

// Sealed row type for grouped list
private sealed class GroupedRow {
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
            val label = headerFmt.format(dayFmt.parse(dayFmt.format(Date(items.first().date)))!!)
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
