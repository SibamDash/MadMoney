package com.example.myapplication

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<Transaction>()
    private val allTransactions = mutableListOf<Transaction>()
    private val calendar = Calendar.getInstance()
    private var selectedDayCalendar: Calendar? = null
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
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupRecyclerView()
        setupMonthNavigation()
        setupBottomNavigation()
        setupTabLayout()
        setupSearch()
        setupBackup()

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadTransactions()
    }

    private fun setupBackup() {
        findViewById<android.widget.ImageView>(R.id.ivBackup).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Backup & Restore")
                .setMessage("Export saves all transactions to a JSON file.\nImport restores transactions from a JSON file.")
                .setPositiveButton("Export") { _, _ ->
                    val fileName = "backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                    exportLauncher.launch(fileName)
                }
                .setNegativeButton("Import") { _, _ ->
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                }
                .setNeutralButton("Cancel", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(transactions,
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
                        val snackbar = com.google.android.material.snackbar.Snackbar.make(
                            findViewById(R.id.main), "${t.title} deleted", 5000
                        )
                        snackbar.setAction("Undo") {
                            DatabaseHelper(this).addTransaction(t)
                            loadTransactions()
                        }
                        snackbar.show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun loadTransactions() {
        val (start, end) = getMonthRange()
        val data = DatabaseHelper(this).getTransactions(start, end)
        allTransactions.clear()
        allTransactions.addAll(data)
        applyActiveFilter()
        updateSummary(data)
        updateDateLabel()
    }

    private fun applyActiveFilter() {
        val filtered = when (activeFilter) {
            "expense" -> allTransactions.filter { it.type == "expense" }
            "income"  -> allTransactions.filter { it.type == "income" }
            "debts"   -> allTransactions.filter { it.type == "togive" || it.type == "toget" }
            else      -> allTransactions
        }
        transactions.clear()
        transactions.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    private fun updateSummary(data: List<Transaction>) {
        val income = data.filter { it.type == "income" }.sumOf { it.amount }
        val expense = data.filter { it.type == "expense" }.sumOf { it.amount }
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
                transactions.clear()
                transactions.addAll(allTransactions)
                adapter.notifyDataSetChanged()
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(query: String?): Boolean {
                val q = query?.trim()?.lowercase() ?: ""
                val filtered = if (q.isEmpty()) allTransactions else allTransactions.filter {
                    it.title.lowercase().contains(q) ||
                    it.category.lowercase().contains(q) ||
                    it.type.lowercase().contains(q) ||
                    it.note.lowercase().contains(q) ||
                    it.account.lowercase().contains(q) ||
                    it.amount.toString().contains(q)
                }
                transactions.clear()
                transactions.addAll(filtered)
                adapter.notifyDataSetChanged()
                return true
            }
        })

        searchView.setOnCloseListener {
            searchView.visibility = View.GONE
            transactions.clear()
            transactions.addAll(allTransactions)
            adapter.notifyDataSetChanged()
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
        val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
        val ref = selectedDayCalendar ?: calendar
        findViewById<TextView>(R.id.tvDateRange).text = fmt.format(ref.time)
    }

    private fun getMonthRange(): Pair<Long, Long> {
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

    private fun setupTabLayout() {
        findViewById<TabLayout>(R.id.tabLayout).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.text) {
                    "Calendar" -> startActivity(Intent(this@MainActivity, CalendarActivity::class.java))
                    "Graph" -> startActivity(Intent(this@MainActivity, ChartActivity::class.java))
                    else -> Toast.makeText(this@MainActivity, "${tab?.text} view", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            activeFilter = when (item.itemId) {
                R.id.nav_income -> "expense"
                R.id.nav_expense -> "income"
                R.id.nav_debts -> "debts"
                else -> "all"
            }
            applyActiveFilter()
            true
        }
    }
}
