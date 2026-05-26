package com.example.myapplication

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class BudgetFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_budget, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = requireContext().getSharedPreferences("budget", Context.MODE_PRIVATE)

        // Weekly
        val etWeekly = view.findViewById<EditText>(R.id.etWeeklyBudget)
        val weeklyBudget = prefs.getFloat("weekly_limit", 0f)
        if (weeklyBudget > 0) etWeekly.setText(weeklyBudget.toInt().toString())
        view.findViewById<Button>(R.id.btnSaveWeekly).setOnClickListener {
            val v = etWeekly.text.toString().toFloatOrNull() ?: return@setOnClickListener
            prefs.edit().putFloat("weekly_limit", v).apply()
            refreshWeekly(view, prefs)
        }
        setupToggle(view, R.id.tvWeeklyDetailsToggle, R.id.layoutWeeklyDetails)
        refreshWeekly(view, prefs)

        // Monthly
        val etMonthly = view.findViewById<EditText>(R.id.etMonthlyBudget)
        val monthlyBudget = prefs.getFloat("monthly_limit", 0f)
        if (monthlyBudget > 0) etMonthly.setText(monthlyBudget.toInt().toString())
        view.findViewById<Button>(R.id.btnSaveMonthly).setOnClickListener {
            val v = etMonthly.text.toString().toFloatOrNull() ?: return@setOnClickListener
            prefs.edit().putFloat("monthly_limit", v).apply()
            refreshMonthly(view, prefs)
        }
        setupToggle(view, R.id.tvMonthlyDetailsToggle, R.id.layoutMonthlyDetails)
        refreshMonthly(view, prefs)

        // Custom
        val settingsPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        applySettingsVisibility(view, settingsPrefs)

        val etCustom = view.findViewById<EditText>(R.id.etCustomBudget)
        val customBudget = prefs.getFloat("custom_limit", 0f)
        if (customBudget > 0) etCustom.setText(customBudget.toInt().toString())
        view.findViewById<Button>(R.id.btnSaveCustom).setOnClickListener {
            val v = etCustom.text.toString().toFloatOrNull() ?: return@setOnClickListener
            prefs.edit().putFloat("custom_limit", v).apply()
            refreshCustom(view, prefs, settingsPrefs)
        }
        setupToggle(view, R.id.tvCustomDetailsToggle, R.id.layoutCustomDetails)
        refreshCustom(view, prefs, settingsPrefs)
    }

    override fun onResume() {
        super.onResume()
        val prefs = requireContext().getSharedPreferences("budget", Context.MODE_PRIVATE)
        val settingsPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        view?.let {
            refreshWeekly(it, prefs)
            refreshMonthly(it, prefs)
            refreshCustom(it, prefs, settingsPrefs)
            applySettingsVisibility(it, settingsPrefs)
        }
    }

    fun applySettingsVisibility(view: View, settingsPrefs: android.content.SharedPreferences) {
        view.findViewById<View>(R.id.cardMonthly).visibility =
            if (settingsPrefs.getBoolean("budget_monthly_enabled", true)) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.cardCustom).visibility =
            if (settingsPrefs.getBoolean("budget_custom_enabled", false)) View.VISIBLE else View.GONE
        val name = settingsPrefs.getString("budget_custom_name", "")?.takeIf { it.isNotEmpty() } ?: "Custom Budget"
        view.findViewById<TextView>(R.id.tvCustomTitle).text = name
    }

    fun refreshCustomFromSettings(view: View) {
        val prefs = requireContext().getSharedPreferences("budget", Context.MODE_PRIVATE)
        val settingsPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        refreshCustom(view, prefs, settingsPrefs)
    }

    private fun setupToggle(view: View, toggleId: Int, contentId: Int) {
        val toggle = view.findViewById<TextView>(toggleId)
        val content = view.findViewById<LinearLayout>(contentId)
        toggle.setOnClickListener {
            val open = content.visibility == View.VISIBLE
            content.visibility = if (open) View.GONE else View.VISIBLE
            toggle.text = if (open) "▶ Details" else "▼ Details"
        }
    }

    private fun refreshWeekly(view: View, prefs: android.content.SharedPreferences) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val weekStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_WEEK, 7)
        val weekEnd = cal.timeInMillis

        val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
        view.findViewById<TextView>(R.id.tvWeekLabel).text =
            "${fmt.format(Date(weekStart))} – ${fmt.format(Date(weekEnd - 1))}"

        val txns = DatabaseHelper(requireContext()).getTransactions(weekStart, weekEnd)
            .filter { it.type == "expense" }
        val spent = txns.sumOf { it.amount }.toFloat()
        val limit = prefs.getFloat("weekly_limit", 0f)
        bindCard(spent, limit,
            view.findViewById(R.id.progressWeekly),
            view.findViewById(R.id.tvWeeklySpent),
            view.findViewById(R.id.tvWeeklyRemaining))
        bindDetails(view, txns, spent, limit, 7,
            view.findViewById(R.id.tvWeeklyStats),
            view.findViewById(R.id.llWeeklyLogs))
    }

    private fun refreshMonthly(view: View, prefs: android.content.SharedPreferences) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val monthStart = cal.timeInMillis
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.add(Calendar.MONTH, 1)
        val monthEnd = cal.timeInMillis

        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        view.findViewById<TextView>(R.id.tvMonthLabel).text = fmt.format(Date(monthStart))

        val txns = DatabaseHelper(requireContext()).getTransactions(monthStart, monthEnd)
            .filter { it.type == "expense" }
        val spent = txns.sumOf { it.amount }.toFloat()
        val limit = prefs.getFloat("monthly_limit", 0f)
        bindCard(spent, limit,
            view.findViewById(R.id.progressMonthly),
            view.findViewById(R.id.tvMonthlySpent),
            view.findViewById(R.id.tvMonthlyRemaining))
        bindDetails(view, txns, spent, limit, daysInMonth,
            view.findViewById(R.id.tvMonthlyStats),
            view.findViewById(R.id.llMonthlyLogs))
    }

    private fun refreshCustom(view: View, prefs: android.content.SharedPreferences, settingsPrefs: android.content.SharedPreferences) {
        val years  = settingsPrefs.getInt("budget_custom_years", 0)
        val months = settingsPrefs.getInt("budget_custom_months", 0)
        val weeks  = settingsPrefs.getInt("budget_custom_weeks", 0)
        val days   = settingsPrefs.getInt("budget_custom_days", 0)

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val end = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        cal.add(Calendar.YEAR, -years)
        cal.add(Calendar.MONTH, -months)
        cal.add(Calendar.WEEK_OF_YEAR, -weeks)
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val start = cal.timeInMillis

        val totalDays = ((end - start) / 86400000).toInt().coerceAtLeast(1)

        val parts = mutableListOf<String>()
        if (years > 0) parts.add("$years yr")
        if (months > 0) parts.add("$months mo")
        if (weeks > 0) parts.add("$weeks wk")
        if (days > 0) parts.add("$days d")
        view.findViewById<TextView>(R.id.tvCustomLabel).text =
            if (parts.isEmpty()) "No period set" else parts.joinToString(" ")

        val txns = if (start < end) DatabaseHelper(requireContext()).getTransactions(start, end)
            .filter { it.type == "expense" } else emptyList()
        val spent = txns.sumOf { it.amount }.toFloat()
        val limit = prefs.getFloat("custom_limit", 0f)
        bindCard(spent, limit,
            view.findViewById(R.id.progressCustom),
            view.findViewById(R.id.tvCustomSpent),
            view.findViewById(R.id.tvCustomRemaining))
        bindDetails(view, txns, spent, limit, totalDays,
            view.findViewById(R.id.tvCustomStats),
            view.findViewById(R.id.llCustomLogs))
    }

    private fun bindCard(spent: Float, limit: Float,
                         progress: ProgressBar, tvSpent: TextView, tvRemaining: TextView) {
        tvSpent.text = "Spent: ₹${spent.toInt()}"
        if (limit <= 0f) {
            progress.progress = 0
            tvRemaining.text = "No limit set"
            tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            return
        }
        val pct = ((spent / limit) * 100).toInt().coerceIn(0, 100)
        progress.progress = pct
        val remaining = limit - spent
        if (remaining >= 0) {
            tvRemaining.text = "Left: ₹${remaining.toInt()}"
            tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_income))
            progress.progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.color_income))
        } else {
            tvRemaining.text = "Over by ₹${(-remaining).toInt()}"
            tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_expense))
            progress.progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.color_expense))
        }
    }

    private fun bindDetails(
        view: View,
        txns: List<Transaction>,
        spent: Float,
        limit: Float,
        periodDays: Int,
        tvStats: TextView,
        llLogs: LinearLayout
    ) {
        val ctx = requireContext()
        val dailyAvg = if (txns.isNotEmpty()) spent / periodDays else 0f
        val pct = if (limit > 0) (spent / limit * 100).toInt() else 0

        val sb = StringBuilder()
        if (limit > 0) {
            sb.append("Budget: ₹${limit.toInt()}  •  Used: $pct%\n")
            val remaining = limit - spent
            if (remaining >= 0) {
                val dailyLeft = remaining / (periodDays.coerceAtLeast(1))
                sb.append("Remaining: ₹${remaining.toInt()}  •  Daily budget left: ₹${"%.0f".format(dailyLeft)}\n")
            } else {
                sb.append("Overspent by: ₹${(-remaining).toInt()}\n")
            }
        }
        sb.append("Daily avg spend: ₹${"%.0f".format(dailyAvg)}  •  Transactions: ${txns.size}")
        tvStats.text = sb.toString()
        tvStats.textSize = 13f

        llLogs.removeAllViews()
        if (txns.isEmpty()) {
            llLogs.addView(makeRow(ctx, "No expenses in this period", "", isHeader = false, isEven = true))
            return
        }

        // Header row
        llLogs.addView(makeRow(ctx, "Date  ·  Title", "Amount", isHeader = true, isEven = false))

        val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
        txns.forEachIndexed { i, t ->
            val label = "${dateFmt.format(Date(t.date))}  ·  ${t.note.ifEmpty { t.category }}"
            llLogs.addView(makeRow(ctx, label, "₹${t.amount.toInt()}", isHeader = false, isEven = i % 2 == 0))
        }
    }

    private fun makeRow(ctx: Context, left: String, right: String, isHeader: Boolean, isEven: Boolean): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 6)
            if (!isHeader) setBackgroundColor(
                if (isEven) ContextCompat.getColor(ctx, R.color.background)
                else ContextCompat.getColor(ctx, R.color.surface)
            )
        }
        val tvLeft = TextView(ctx).apply {
            text = left
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, if (isHeader) R.color.text_primary else R.color.text_secondary))
            if (isHeader) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvRight = TextView(ctx).apply {
            text = right
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, if (isHeader) R.color.text_primary else R.color.color_expense))
            if (isHeader) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(tvLeft)
        row.addView(tvRight)
        return row
    }
}
