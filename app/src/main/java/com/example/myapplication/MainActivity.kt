package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var dailyFragment: DailyFragment
    private lateinit var viewPager: ViewPager2

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
                dailyFragment.load()
            },
            onFailure = { Toast.makeText(this, "Import failed: ${it.message}", Toast.LENGTH_SHORT).show() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean("dark_mode", false)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        dailyFragment = DailyFragment()

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            val fragments: List<Fragment> = listOf(
                dailyFragment,
                CalendarFragment(),
                ChartFragment(),
                PlaceholderFragment("Budget"),
                PlaceholderFragment("Total")
            )
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Clear day filter when user manually navigates away from Daily and back
                if (position == 0) { /* keep filter — user may have just come from calendar */ }
                else dailyFragment.clearDateFilter()
            }
        })

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = listOf("Daily", "Calendar", "Graph", "Budget", "Total")[pos]
        }.attach()

        setupSearch()
        setupBottomNavigation()
        setupSettings()

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
    }

    /** Called from CalendarFragment when a day is tapped */
    fun navigateToDailyForDate(year: Int, month: Int, day: Int) {
        dailyFragment.setDateFilter(year, month, day)
        viewPager.currentItem = 0
    }

    fun updateSummary(data: List<Transaction>) {
        val cal = Calendar.getInstance()
        val s = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0) }
        val e = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59) }
        val month = data.filter { it.date in s.timeInMillis..e.timeInMillis }
        val income  = month.filter { it.type == "income" }.sumOf { it.amount }
        val expense = month.filter { it.type == "expense" }.sumOf { it.amount }
        findViewById<TextView>(R.id.tvIncomeAmount).text  = "₹%.2f".format(income)
        findViewById<TextView>(R.id.tvExpenseAmount).text = "₹%.2f".format(expense)
        findViewById<TextView>(R.id.tvTotalAmount).text   = "₹%.2f".format(income - expense)
    }

    private var backPressedTime = 0L
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val searchContainer = findViewById<View>(R.id.searchView)
        when {
            drawer.isDrawerOpen(Gravity.END) -> drawer.closeDrawer(Gravity.END)
            searchContainer.visibility == View.VISIBLE -> {
                searchContainer.visibility = View.GONE
                findViewById<android.widget.SearchView>(R.id.searchInput).setQuery("", false)
                dailyFragment.setSearch("")
            }
            System.currentTimeMillis() - backPressedTime < 2000 -> super.onBackPressed()
            else -> { backPressedTime = System.currentTimeMillis(); Toast.makeText(this, "Click again to close the app", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun setupSearch() {
        val searchContainer = findViewById<View>(R.id.searchView)
        val searchInput     = findViewById<android.widget.SearchView>(R.id.searchInput)
        val spinner         = findViewById<android.widget.Spinner>(R.id.spinnerSearchBy)
        val ivSearch        = findViewById<android.widget.ImageView>(R.id.ivSearch)

        val fields = listOf("All", "Name", "Category", "Amount", "Date")
        spinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fields)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                dailyFragment.setSearchField(fields[pos].lowercase())
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        fun openSearch() {
            searchContainer.visibility = View.VISIBLE
            searchInput.isIconified = false
            searchInput.requestFocus()
        }
        fun closeSearch() {
            searchContainer.visibility = View.GONE
            searchInput.setQuery("", false)
            spinner.setSelection(0)
            dailyFragment.setSearch("")
            dailyFragment.setSearchField("all")
        }

        ivSearch.setOnClickListener { if (searchContainer.visibility == View.GONE) openSearch() else closeSearch() }
        searchInput.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean { dailyFragment.setSearch(q ?: ""); return true }
        })
        searchInput.setOnCloseListener { closeSearch(); false }
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        fun scale(selectedId: Int) {
            for (i in 0 until nav.menu.size()) {
                val item = nav.menu.getItem(i)
                val s = if (item.itemId == selectedId) 1.0f else 20f/24f
                nav.findViewById<View>(item.itemId)?.animate()?.scaleX(s)?.scaleY(s)?.setDuration(150)?.start()
            }
        }
        nav.setOnItemSelectedListener { item ->
            dailyFragment.setFilter(when (item.itemId) {
                R.id.nav_income  -> "expense"
                R.id.nav_expense -> "income"
                R.id.nav_debts   -> "debts"
                else             -> "all"
            })
            scale(item.itemId); true
        }
        nav.post { scale(nav.selectedItemId) }
    }

    private fun setupSettings() {
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val sidebar = drawer.getChildAt(1)
        val screenWidth = resources.displayMetrics.widthPixels
        val collapsedWidth = (280 * resources.displayMetrics.density).toInt()
        var isExpanded = false

        findViewById<android.widget.ImageView>(R.id.ivSettings).setOnClickListener { drawer.openDrawer(Gravity.END) }

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
            drawer.closeDrawer(Gravity.END); startActivity(Intent(this, SavedLogsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.headerCustomize).setOnClickListener {
            toggleSection(findViewById(R.id.contentCustomize), findViewById(R.id.arrowCustomize))
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val switchDarkMode = findViewById<SwitchCompat>(R.id.switchDarkMode)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
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
            drawer.closeDrawer(Gravity.END); importLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }
}

/** Placeholder for Budget / Total tabs */
class PlaceholderFragment(private val label: String) : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return TextView(requireContext()).apply {
            text = "$label — coming soon"
            gravity = android.view.Gravity.CENTER
            textSize = 18f
        }
    }
}
