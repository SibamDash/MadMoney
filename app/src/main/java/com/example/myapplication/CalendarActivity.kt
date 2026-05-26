package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class CalendarActivity : AppCompatActivity() {

    private val calendar: Calendar = Calendar.getInstance()
    private lateinit var gestureDetector: GestureDetector
    private var isAnimating = false

    private lateinit var tvMonthYear: TextView
    private lateinit var rvCalendar: RecyclerView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_calendar)

        // Initialize views
        tvMonthYear = findViewById(R.id.tvMonthYear)
        rvCalendar = findViewById(R.id.rvCalendar)
        val root = findViewById<View>(R.id.root)
        val btnBack = findViewById<View>(R.id.ivBack)
        val layoutMonthPicker = findViewById<View>(R.id.layoutMonthPicker)

        // Setup Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Setup Swipe Gestures
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                // Swipe detection (User requested Swipe Left -> Previous Month)
                if (abs(diffX) > 100 && abs(velocityX) > 100) {
                    if (diffX < 0) {
                        // Swipe Left -> Previous Month
                        animateMonthChange(-1)
                    } else {
                        // Swipe Right -> Next Month
                        animateMonthChange(1)
                    }
                    return true
                }
                return false
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

        // Touch Listeners with performClick() for accessibility
        root.setOnTouchListener { view, event ->
            if (gestureDetector.onTouchEvent(event)) return@setOnTouchListener true
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            true
        }

        rvCalendar.setOnTouchListener { _, event ->
            // Catch swipes on the grid, but let it handle its own touches if not a swipe
            gestureDetector.onTouchEvent(event)
            false
        }

        btnBack.setOnClickListener { finish() }
        layoutMonthPicker.setOnClickListener { showMonthYearPicker() }
        
        loadCalendar()
    }

    /**
     * Smoothly animates the month transition.
     * @param delta Direction of change (-1 for previous, 1 for next)
     */
    private fun animateMonthChange(delta: Int) {
        if (isAnimating) return
        isAnimating = true

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        
        // Outgoing Translation: If delta is -1 (prev), we swiped left, current goes left (-screenWidth)
        val outTranslation = if (delta > 0) screenWidth else -screenWidth
        val inStartTranslation = if (delta > 0) -screenWidth else screenWidth

        tvMonthYear.animate().alpha(0f).setDuration(150).start()

        rvCalendar.animate()
            .translationX(outTranslation)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                calendar.add(Calendar.MONTH, delta)
                loadCalendar()
                
                // Prepare for entrance
                rvCalendar.translationX = inStartTranslation
                rvCalendar.alpha = 0f
                tvMonthYear.alpha = 0f
                
                // Ingoing animation
                rvCalendar.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(250)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { isAnimating = false }
                    .start()
                
                tvMonthYear.animate().alpha(1f).setDuration(250).start()
            }
            .start()
    }

    private fun showMonthYearPicker() {
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val monthPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 11
            displayedValues = months
            value = calendar.get(Calendar.MONTH)
            wrapSelectorWheel = true
        }
        val yearPicker = NumberPicker(this).apply {
            minValue = 2000
            maxValue = 2100
            value = calendar.get(Calendar.YEAR)
            wrapSelectorWheel = false
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, p / 2)
            addView(monthPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(yearPicker,  LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        AlertDialog.Builder(this)
            .setTitle("Select Month")
            .setView(container)
            .setPositiveButton("Go") { _, _ ->
                calendar.set(Calendar.MONTH, monthPicker.value)
                calendar.set(Calendar.YEAR, yearPicker.value)
                loadCalendar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCalendar() {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvMonthYear.text = fmt.format(calendar.time)

        val monthStart = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthEnd = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val transactions = DatabaseHelper(this).getTransactions(monthStart.timeInMillis, monthEnd.timeInMillis)

        // Process transaction data into a map for easy grid lookup
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayMap = mutableMapOf<String, DaySummary>()
        for (t in transactions) {
            val key = dayFmt.format(Date(t.date))
            val current = dayMap.getOrDefault(key, DaySummary())
            when (t.type) {
                "income"          -> current.income += t.amount
                "expense"         -> current.expense += t.amount
                "togive", "toget" -> current.debt += t.amount
            }
            dayMap[key] = current
        }

        // Prepare grid cells
        val firstDayOfWeek = monthStart.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = monthEnd.get(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<Int?>().apply {
            repeat(firstDayOfWeek) { add(null) }
            for (d in 1..daysInMonth) add(d)
        }

        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                             today.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
        val todayDay = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else -1

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        rvCalendar.apply {
            if (layoutManager == null) {
                layoutManager = GridLayoutManager(this@CalendarActivity, 7)
            }
            adapter = CalendarAdapter(cells, dayMap, year, month, todayDay) { day ->
                val targetCal = Calendar.getInstance().apply {
                    set(year, month - 1, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val intent = Intent(this@CalendarActivity, MainActivity::class.java).apply {
                    putExtra("selected_date_millis", targetCal.timeInMillis)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
        }
    }
}
