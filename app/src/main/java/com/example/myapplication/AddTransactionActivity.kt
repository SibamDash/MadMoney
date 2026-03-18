package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    private var selectedType = "expense"  // expense | income | togive | toget
    private var debtSubType = "togive"
    private var selectedDateMillis = System.currentTimeMillis()
    private val dateFmt = SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault())

    private val white get() = ContextCompat.getColor(this, android.R.color.white)
    private val grey get() = ContextCompat.getColor(this, R.color.toggle_text_unselected)
    private val selBg get() = getDrawable(R.drawable.bg_toggle_selected)
    private val defBg get() = getDrawable(R.drawable.bg_toggle)
    private val transBg get() = getDrawable(android.R.color.transparent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val flipper = findViewById<ViewFlipper>(R.id.viewFlipper)
        val btnExpense = findViewById<AppCompatButton>(R.id.btnExpense)
        val btnIncome = findViewById<AppCompatButton>(R.id.btnIncome)
        val btnDebt = findViewById<AppCompatButton>(R.id.btnDebt)

        // Set date on all 3 tvDate views
        listOf(R.id.tvDate).forEach { updateAllDates() }

        fun selectTab(type: String) {
            selectedType = if (type == "togive" || type == "toget") type else type
            val idx = when (type) { "expense" -> 0; "income" -> 1; else -> 2 }
            val goingRight = idx > flipper.displayedChild
            flipper.inAnimation = if (goingRight)
                android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
            else
                android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
            flipper.outAnimation = if (goingRight)
                android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
            else
                android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_out_right)
            flipper.displayedChild = idx

            btnExpense.background = if (type == "expense") selBg else transBg
            btnIncome.background = if (type == "income") selBg else transBg
            btnDebt.background = if (type == "debt" || type == "togive" || type == "toget") selBg else transBg
            btnExpense.setTextColor(if (type == "expense") white else grey)
            btnIncome.setTextColor(if (type == "income") white else grey)
            btnDebt.setTextColor(if (type == "debt" || type == "togive" || type == "toget") white else grey)

            selectedType = when (type) { "income" -> "income"; "debt" -> "togive"; else -> "expense" }
            updateAllDates()
        }

        btnExpense.setOnClickListener { selectTab("expense") }
        btnIncome.setOnClickListener { selectTab("income") }
        btnDebt.setOnClickListener { selectTab("debt") }

        // Debt sub-toggle
        val btnToGive = findViewById<AppCompatButton>(R.id.btnToGive)
        val btnToGet = findViewById<AppCompatButton>(R.id.btnToGet)
        fun selectDebtType(t: String) {
            debtSubType = t
            selectedType = t
            btnToGive.background = if (t == "togive") selBg else transBg
            btnToGet.background = if (t == "toget") selBg else transBg
            btnToGive.setTextColor(if (t == "togive") white else grey)
            btnToGet.setTextColor(if (t == "toget") white else grey)
        }
        btnToGive.setOnClickListener { selectDebtType("togive") }
        btnToGet.setOnClickListener { selectDebtType("toget") }

        // Date pickers for all 3 forms
        listOf(R.id.tvDate).forEach { id ->
            // handled via updateAllDates, click on any tvDate
        }
        // Wire date click on each flipper child
        for (i in 0 until flipper.childCount) {
            flipper.getChildAt(i).findViewById<TextView>(R.id.tvDate)?.setOnClickListener {
                pickDateTime()
            }
        }

        // Save buttons
        for (i in 0 until flipper.childCount) {
            val child = flipper.getChildAt(i)
            child.findViewById<AppCompatButton>(R.id.btnSave)?.setOnClickListener { save(false) }
            child.findViewById<AppCompatButton>(R.id.btnContinue)?.setOnClickListener { save(true) }
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        selectTab("expense")
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun pickDateTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                selectedDateMillis = cal.timeInMillis
                updateAllDates()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateAllDates() {
        val text = dateFmt.format(Date(selectedDateMillis))
        val flipper = findViewById<ViewFlipper>(R.id.viewFlipper)
        for (i in 0 until flipper.childCount) {
            flipper.getChildAt(i).findViewById<TextView>(R.id.tvDate)?.text = text
        }
    }

    private fun currentChild(): android.view.View =
        findViewById<ViewFlipper>(R.id.viewFlipper).currentView

    private fun save(andContinue: Boolean) {
        val child = currentChild()
        val amountText = child.findViewById<EditText>(R.id.etAmount)?.text.toString().trim()
        val note = child.findViewById<EditText>(R.id.etNote)?.text.toString().trim()

        if (amountText.isEmpty()) { child.findViewById<EditText>(R.id.etAmount)?.error = "Required"; return }

        val (title, category) = when (selectedType) {
            "income" -> {
                val src = child.findViewById<EditText>(R.id.etIncomeSource)?.text.toString().trim()
                if (src.isEmpty()) { child.findViewById<EditText>(R.id.etIncomeSource)?.error = "Required"; return }
                Pair(src, src)
            }
            "togive", "toget" -> {
                val person = child.findViewById<EditText>(R.id.etDebtPerson)?.text.toString().trim()
                if (person.isEmpty()) { child.findViewById<EditText>(R.id.etDebtPerson)?.error = "Required"; return }
                Pair(person, if (selectedType == "togive") "To Give" else "To Get")
            }
            else -> {
                val cat = child.findViewById<EditText>(R.id.etExpenseCategory)?.text.toString().trim()
                if (cat.isEmpty()) { child.findViewById<EditText>(R.id.etExpenseCategory)?.error = "Required"; return }
                Pair(cat, cat)
            }
        }

        DatabaseHelper(this).addTransaction(Transaction(
            title = title, category = category,
            amount = amountText.toDouble(), type = selectedType,
            note = note, date = selectedDateMillis
        ))
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()

        if (andContinue) {
            child.findViewById<EditText>(R.id.etAmount)?.text?.clear()
            child.findViewById<EditText>(R.id.etNote)?.text?.clear()
            child.findViewById<EditText>(R.id.etExpenseCategory)?.text?.clear()
            child.findViewById<EditText>(R.id.etIncomeSource)?.text?.clear()
            child.findViewById<EditText>(R.id.etDebtPerson)?.text?.clear()
            selectedDateMillis = System.currentTimeMillis()
            updateAllDates()
        } else {
            finish()
        }
    }
}
