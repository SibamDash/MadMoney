package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var dailyFragment: DailyFragment
    private lateinit var viewPager: ViewPager2
    private var backPressedTime = 0L
    private var budgetMode = "weekly" // "weekly", "monthly", "custom"
    private var lastSummaryData: List<Transaction> = emptyList()

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
                BudgetFragment(),
                TrendsFragment()
            )
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        viewPager.offscreenPageLimit = 4
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position != 0) dailyFragment.clearDateFilter()
            }
        })

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = listOf("Daily", "Calendar", "Graph", "Budget", "Trends")[pos]
        }.attach()

        setupSearch()
        setupSettings()
        setupBudgetModeSelector()
        applyPositionSettings(prefs)

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
            applyLaunchTransition()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
                val searchContainer = findViewById<View>(R.id.searchView)
                when {
                    drawer.isDrawerOpen(GravityCompat.END) -> drawer.closeDrawer(GravityCompat.END)
                    searchContainer.visibility == View.VISIBLE -> {
                        searchContainer.visibility = View.GONE
                        findViewById<android.widget.SearchView>(R.id.searchInput).setQuery("", false)
                        dailyFragment.setSearch("")
                    }
                    System.currentTimeMillis() - backPressedTime < 2000 -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    else -> {
                        backPressedTime = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, "Click again to close the app", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    /** Called from CalendarFragment when a day is tapped */
    fun navigateToDailyForDate(year: Int, month: Int, day: Int) {
        dailyFragment.setDateFilter(year, month, day)
        viewPager.currentItem = 0
    }

    fun updateSummary(data: List<Transaction>) {
        lastSummaryData = data
        val cal = Calendar.getInstance()

        // Determine time window based on budgetMode
        val (start, end, limitKey) = when (budgetMode) {
            "weekly" -> {
                val s = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }
                val e = (s.clone() as Calendar).apply { add(Calendar.DAY_OF_WEEK, 7) }
                Triple(s.timeInMillis, e.timeInMillis, "weekly_limit")
            }
            "custom" -> {
                val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)
                val e = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                }
                val s = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                    add(Calendar.YEAR,  -settingsPrefs.getInt("budget_custom_years", 0))
                    add(Calendar.MONTH, -settingsPrefs.getInt("budget_custom_months", 0))
                    add(Calendar.WEEK_OF_YEAR, -settingsPrefs.getInt("budget_custom_weeks", 0))
                    add(Calendar.DAY_OF_YEAR, -settingsPrefs.getInt("budget_custom_days", 0))
                }
                Triple(s.timeInMillis, e.timeInMillis, "custom_limit")
            }
            else -> { // monthly
                val s = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }
                val e = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                }
                Triple(s.timeInMillis, e.timeInMillis, "monthly_limit")
            }
        }

        val period = data.filter { it.date in start..end }
        val income  = period.filter { it.type == "income" }.sumOf { it.amount }
        val expense = period.filter { it.type == "expense" }.sumOf { it.amount }
        findViewById<TextView>(R.id.tvIncomeAmount).text  = "₹%.2f".format(income)
        findViewById<TextView>(R.id.tvExpenseAmount).text = "₹%.2f".format(expense)

        val limit = getSharedPreferences("budget", MODE_PRIVATE).getFloat(limitKey, 0f)
        val tvTotal = findViewById<TextView>(R.id.tvTotalAmount)
        if (limit > 0f) {
            val remaining = limit - expense
            tvTotal.text = "₹%.2f".format(remaining)
            tvTotal.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                if (remaining < 0) R.color.color_expense else R.color.color_income))
        } else {
            tvTotal.text = "No limit"
            tvTotal.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun setupBudgetModeSelector() {
        val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val customName = settingsPrefs.getString("budget_custom_name", "")?.takeIf { it.isNotEmpty() } ?: "Custom"
        val tvLabel = findViewById<TextView>(R.id.tvBudgetLabel)

        fun updateLabel() {
            tvLabel.text = when (budgetMode) {
                "weekly" -> "Weekly ▾"
                "custom" -> "$customName ▾"
                else     -> "Monthly ▾"
            }
        }
        updateLabel()

        findViewById<View>(R.id.llBudgetLeft).setOnClickListener { anchor ->
            val popup = android.widget.PopupMenu(this, anchor)
            popup.menu.add(0, 0, 0, "Weekly")
            if (settingsPrefs.getBoolean("budget_monthly_enabled", true))
                popup.menu.add(0, 1, 1, "Monthly")
            if (settingsPrefs.getBoolean("budget_custom_enabled", false))
                popup.menu.add(0, 2, 2, customName)
            popup.setOnMenuItemClickListener { item ->
                budgetMode = when (item.itemId) { 0 -> "weekly"; 2 -> "custom"; else -> "monthly" }
                updateLabel()
                updateSummary(lastSummaryData)
                true
            }
            popup.show()
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


    private fun setupSettings() {
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val sidebar = drawer.getChildAt(1)
        val screenWidth = resources.displayMetrics.widthPixels
        val collapsedWidth = (280 * resources.displayMetrics.density).toInt()
        var isExpanded = false

        findViewById<android.widget.ImageView>(R.id.ivSettings).setOnClickListener { drawer.openDrawer(GravityCompat.END) }

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
            drawer.closeDrawer(GravityCompat.END); startActivity(Intent(this, SavedLogsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.headerCustomize).setOnClickListener {
            toggleSection(findViewById(R.id.contentCustomize), findViewById(R.id.arrowCustomize))
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val switchDarkMode = findViewById<SwitchCompat>(R.id.switchDarkMode)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)

        val transitionStyles = listOf("Slide", "Fade", "Zoom", "Flip")
        val spinnerStyle = findViewById<android.widget.Spinner>(R.id.spinnerTransitionStyle)
        spinnerStyle.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, transitionStyles)
        spinnerStyle.setSelection(prefs.getInt("transition_style", 0))

        val rowSlideDir = findViewById<android.view.View>(R.id.rowSlideDirection)
        val spinnerSlideDir = findViewById<android.widget.Spinner>(R.id.spinnerSlideDirection)
        spinnerSlideDir.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Left to right", "Right to left", "Bottom to top", "Top to bottom"))
        spinnerSlideDir.setSelection(prefs.getInt("slide_direction", 0))
        rowSlideDir.visibility = if (prefs.getInt("transition_style", 0) == 0) android.view.View.VISIBLE else android.view.View.GONE

        val fabPositions = listOf("Right side", "Left side")
        val spinnerFab = findViewById<android.widget.Spinner>(R.id.spinnerFabPosition)
        spinnerFab.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fabPositions)
        spinnerFab.setSelection(prefs.getInt("fab_position", 0))

        val etFabMarginSide = findViewById<android.widget.EditText>(R.id.etFabMarginSide)
        val etFabMarginBottom = findViewById<android.widget.EditText>(R.id.etFabMarginBottom)
        etFabMarginSide.setText(prefs.getInt("fab_margin_side", 16).toString())
        etFabMarginBottom.setText(prefs.getInt("fab_margin_bottom", 16).toString())

        val tabPositions = listOf("Below search bar", "Above search bar")
        val spinnerTab = findViewById<android.widget.Spinner>(R.id.spinnerTabPosition)
        spinnerTab.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tabPositions)
        spinnerTab.setSelection(prefs.getInt("tab_position", 0))

        val switchSummaryBar = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSummaryBar)
        val switchTabBar = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchTabBar)
        switchSummaryBar.isChecked = prefs.getBoolean("show_summary_bar", true)
        switchTabBar.isChecked = prefs.getBoolean("show_tab_bar", true)

        findViewById<android.widget.Button>(R.id.btnResetCustomize).setOnClickListener {
            spinnerStyle.setSelection(0)
            spinnerSlideDir.setSelection(0)
            spinnerFab.setSelection(0)
            etFabMarginSide.setText("16")
            etFabMarginBottom.setText("16")
            spinnerTab.setSelection(0)
            switchSummaryBar.isChecked = true
            switchTabBar.isChecked = true
            switchDarkMode.isChecked = false
        }

        findViewById<LinearLayout>(R.id.headerBackup).setOnClickListener {
            toggleSection(findViewById(R.id.contentBackup), findViewById(R.id.arrowBackup))
        }
        findViewById<TextView>(R.id.optionExport).setOnClickListener {
            drawer.closeDrawer(GravityCompat.END)
            exportLauncher.launch("backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json")
        }
        findViewById<TextView>(R.id.optionImport).setOnClickListener {
            drawer.closeDrawer(GravityCompat.END); importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        findViewById<LinearLayout>(R.id.headerBudget).setOnClickListener {
            toggleSection(findViewById(R.id.contentBudget), findViewById(R.id.arrowBudget))
        }

        val switchMonthly = findViewById<SwitchCompat>(R.id.switchBudgetMonthly)
        val switchCustom = findViewById<SwitchCompat>(R.id.switchBudgetCustom)
        val layoutCustomPeriod = findViewById<LinearLayout>(R.id.layoutCustomPeriod)
        val arrowBudgetCustom = findViewById<TextView>(R.id.arrowBudgetCustom)

        switchMonthly.isChecked = prefs.getBoolean("budget_monthly_enabled", true)
        switchCustom.isChecked = prefs.getBoolean("budget_custom_enabled", false)
        layoutCustomPeriod.visibility = if (switchCustom.isChecked) View.VISIBLE else View.GONE
        arrowBudgetCustom.text = if (switchCustom.isChecked) "▼" else "▶"

        val etYears  = findViewById<android.widget.EditText>(R.id.etCustomYears)
        val etMonths = findViewById<android.widget.EditText>(R.id.etCustomMonths)
        val etWeeks  = findViewById<android.widget.EditText>(R.id.etCustomWeeks)
        val etDays   = findViewById<android.widget.EditText>(R.id.etCustomDays)
        val etCustomName = findViewById<android.widget.EditText>(R.id.etCustomName)
        val etCustomBudgetAmount = findViewById<android.widget.EditText>(R.id.etCustomBudgetAmount)
        prefs.getInt("budget_custom_years", 0).let { if (it > 0) etYears.setText(it.toString()) }
        prefs.getInt("budget_custom_months", 0).let { if (it > 0) etMonths.setText(it.toString()) }
        prefs.getInt("budget_custom_weeks", 0).let { if (it > 0) etWeeks.setText(it.toString()) }
        prefs.getInt("budget_custom_days", 0).let { if (it > 0) etDays.setText(it.toString()) }
        prefs.getString("budget_custom_name", "")?.let { if (it.isNotEmpty()) etCustomName.setText(it) }
        getSharedPreferences("budget", MODE_PRIVATE).getFloat("custom_limit", 0f)
            .let { if (it > 0) etCustomBudgetAmount.setText(it.toInt().toString()) }

        findViewById<LinearLayout>(R.id.rowBudgetCustom).setOnClickListener {
            val open = layoutCustomPeriod.visibility == View.VISIBLE
            layoutCustomPeriod.visibility = if (open) View.GONE else View.VISIBLE
            arrowBudgetCustom.text = if (open) "▶" else "▼"
        }

        switchCustom.setOnCheckedChangeListener { _, checked ->
            layoutCustomPeriod.visibility = if (checked) View.VISIBLE else View.GONE
            arrowBudgetCustom.text = if (checked) "▼" else "▶"
        }

        fun saveSettings() {
            prefs.edit()                .putBoolean("dark_mode", switchDarkMode.isChecked)
                .putBoolean("budget_monthly_enabled", switchMonthly.isChecked)
                .putBoolean("budget_custom_enabled", switchCustom.isChecked)
                .putString("budget_custom_name", etCustomName.text.toString().trim())
                .putInt("budget_custom_years",  etYears.text.toString().toIntOrNull() ?: 0)
                .putInt("budget_custom_months", etMonths.text.toString().toIntOrNull() ?: 0)
                .putInt("budget_custom_weeks",  etWeeks.text.toString().toIntOrNull() ?: 0)
                .putInt("budget_custom_days",   etDays.text.toString().toIntOrNull() ?: 0)
                .putInt("transition_style", spinnerStyle.selectedItemPosition)
                .putInt("slide_direction", spinnerSlideDir.selectedItemPosition)
                .putInt("fab_position",     spinnerFab.selectedItemPosition)
                .putInt("fab_margin_side",  etFabMarginSide.text.toString().toIntOrNull() ?: 16)
                .putInt("fab_margin_bottom", etFabMarginBottom.text.toString().toIntOrNull() ?: 16)
                .putInt("tab_position",     spinnerTab.selectedItemPosition)
                .putBoolean("show_summary_bar", switchSummaryBar.isChecked)
                .putBoolean("show_tab_bar", switchTabBar.isChecked)
                .apply()
            etCustomBudgetAmount.text.toString().toFloatOrNull()?.let {
                getSharedPreferences("budget", MODE_PRIVATE).edit().putFloat("custom_limit", it).apply()
            }
            AppCompatDelegate.setDefaultNightMode(
                if (switchDarkMode.isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            applyPositionSettings(prefs)
            (supportFragmentManager.findFragmentByTag("f3") as? BudgetFragment)
                ?.let { frag -> frag.view?.let { v -> frag.applySettingsVisibility(v, prefs); frag.refreshCustomFromSettings(v) } }
        }

        // Auto-save on every change
        val spinnerListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = saveSettings()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        // Spinners that already have their own listeners (style→rowSlideDir, speed→rowCustomDur) need chaining
        spinnerStyle.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                rowSlideDir.visibility = if (pos == 0) android.view.View.VISIBLE else android.view.View.GONE
                saveSettings()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        for (sp in listOf(spinnerSlideDir, spinnerFab, spinnerTab)) sp.onItemSelectedListener = spinnerListener

        val switchListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ -> saveSettings() }
        for (sw in listOf(switchDarkMode, switchSummaryBar, switchTabBar, switchMonthly)) sw.setOnCheckedChangeListener(switchListener)
        switchCustom.setOnCheckedChangeListener { _, checked ->
            layoutCustomPeriod.visibility = if (checked) View.VISIBLE else View.GONE
            arrowBudgetCustom.text = if (checked) "▼" else "▶"
            saveSettings()
        }

        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = saveSettings()
        }
        for (et in listOf(etFabMarginSide, etFabMarginBottom, etYears, etMonths, etWeeks, etDays, etCustomName, etCustomBudgetAmount)) {
            et.addTextChangedListener(textWatcher)
        }
    }//made by sibam

    fun applyPositionSettings(prefs: android.content.SharedPreferences) {
        val density = resources.displayMetrics.density

        // FAB position & margins
        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)
        val lp = fab.layoutParams as android.widget.RelativeLayout.LayoutParams
        val marginSidePx   = (prefs.getInt("fab_margin_side", 16) * density).toInt()
        val marginBottomPx = (prefs.getInt("fab_margin_bottom", 16) * density).toInt()
        if (prefs.getInt("fab_position", 0) == 1) {
            lp.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            lp.setMargins(marginSidePx, 0, 0, marginBottomPx)
        } else {
            lp.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
            lp.setMargins(0, 0, marginSidePx, marginBottomPx)
        }
        fab.layoutParams = lp

        // Summary bar visibility
        val summaryBar = findViewById<android.view.View>(R.id.summary)
        val showSummary = prefs.getBoolean("show_summary_bar", true)
        summaryBar.visibility = if (showSummary) android.view.View.VISIBLE else android.view.View.GONE

        // Tab bar visibility & position
        val tabLayout  = findViewById<TabLayout>(R.id.tabLayout)
        val searchView = findViewById<android.view.View>(R.id.searchView)
        val showTab    = prefs.getBoolean("show_tab_bar", true)
        tabLayout.visibility = if (showTab) android.view.View.VISIBLE else android.view.View.GONE

        // Re-anchor the chain: topBar → [searchView or tabLayout] → [tabLayout or searchView] → [summary] → viewPager
        val tabAbove = prefs.getInt("tab_position", 0) == 1 // tab above search
        if (tabAbove) {
            (tabLayout.layoutParams as android.widget.RelativeLayout.LayoutParams).apply {
                removeRule(android.widget.RelativeLayout.BELOW); addRule(android.widget.RelativeLayout.BELOW, R.id.topBar)
            }
            (searchView.layoutParams as android.widget.RelativeLayout.LayoutParams).apply {
                removeRule(android.widget.RelativeLayout.BELOW)
                addRule(android.widget.RelativeLayout.BELOW, if (showTab) R.id.tabLayout else R.id.topBar)
            }
        } else {
            (searchView.layoutParams as android.widget.RelativeLayout.LayoutParams).apply {
                removeRule(android.widget.RelativeLayout.BELOW); addRule(android.widget.RelativeLayout.BELOW, R.id.topBar)
            }
            (tabLayout.layoutParams as android.widget.RelativeLayout.LayoutParams).apply {
                removeRule(android.widget.RelativeLayout.BELOW); addRule(android.widget.RelativeLayout.BELOW, R.id.searchView)
            }
        }

        // ViewPager anchors below summary if visible, else below tab/search
        val lastBarId = when {
            showSummary -> R.id.summary
            showTab     -> if (tabAbove) R.id.searchView else R.id.tabLayout
            tabAbove    -> R.id.searchView
            else        -> R.id.searchView
        }
        (viewPager.layoutParams as android.widget.RelativeLayout.LayoutParams).apply {
            removeRule(android.widget.RelativeLayout.BELOW); addRule(android.widget.RelativeLayout.BELOW, lastBarId)
        }

        // Also re-anchor summary itself
        val summaryAnchor = when {
            showTab -> if (tabAbove) R.id.searchView else R.id.tabLayout
            tabAbove -> R.id.searchView
            else -> R.id.searchView
        }
        (summaryBar.layoutParams as android.widget.RelativeLayout.LayoutParams).apply {
            removeRule(android.widget.RelativeLayout.BELOW); addRule(android.widget.RelativeLayout.BELOW, summaryAnchor)
        }

        tabLayout.requestLayout(); searchView.requestLayout()
        summaryBar.requestLayout(); viewPager.requestLayout()

        // Reset any stale transform state before applying new transformer
        (viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.let { rv ->
            for (i in 0 until rv.childCount) {
                rv.getChildAt(i)?.let { it.alpha = 1f; it.scaleX = 1f; it.scaleY = 1f; it.rotationY = 0f; it.translationZ = 0f }
            }
        }

        // Tab switch animation
        when (prefs.getInt("transition_style", 0)) {
            0 -> viewPager.setPageTransformer(null) // Slide — default ViewPager2 behavior
            1 -> viewPager.setPageTransformer { page: android.view.View, position: Float ->
                // Fade — cross-dissolve, no movement
                page.alpha = (1f - Math.abs(position)).coerceIn(0f, 1f)
                page.translationZ = if (position == 0f) 1f else 0f
            }
            2 -> viewPager.setPageTransformer { page: android.view.View, position: Float ->
                // Zoom — scale down outgoing, scale up incoming
                val absPos = Math.abs(position).coerceIn(0f, 1f)
                val scale = 0.75f + (1f - absPos) * 0.25f
                page.scaleX = scale
                page.scaleY = scale
                page.alpha = 0.4f + (1f - absPos) * 0.6f
            }
            3 -> viewPager.setPageTransformer { page: android.view.View, position: Float ->
                // Flip — 3D card flip on Y axis
                page.rotationY = position * -30f
                page.alpha = (1f - Math.abs(position)).coerceIn(0.3f, 1f)
                page.translationZ = if (position == 0f) 1f else 0f
            }
        }
    }

    fun transitionDuration(): Int {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return when (prefs.getInt("transition_speed", 1)) {
            0 -> 150; 2 -> 500; 3 -> prefs.getInt("transition_custom_ms", 300); else -> 300
        }
    }

    fun applyLaunchTransition() {
        // On API 21+, AddTransactionActivity sets its own enter/return transitions in onCreate.
        // Just suppress the default overridePendingTransition so they don't conflict.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            overridePendingTransition(0, 0)
            return
        }
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val style = prefs.getInt("transition_style", 0)
        val dir   = prefs.getInt("slide_direction", 0)
        val (enterRes, exitRes) = when (style) {
            0 -> when (dir) {
                1 -> R.anim.slide_in_left to R.anim.slide_out_right
                2 -> R.anim.slide_in_bottom to R.anim.slide_out_top
                3 -> R.anim.slide_in_top to R.anim.slide_out_bottom
                else -> R.anim.slide_in_right to R.anim.slide_out_left
            }
            1, 2 -> android.R.anim.fade_in to android.R.anim.fade_out
            else -> { overridePendingTransition(0, 0); return }
        }
        overridePendingTransition(enterRes, exitRes)
    }
}
