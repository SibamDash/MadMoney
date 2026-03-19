package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    private var selectedType = "expense"
    private var debtSubType = "togive"
    private var selectedDateMillis = System.currentTimeMillis()
    private val dateFmt = SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault())

    private val white get() = ContextCompat.getColor(this, android.R.color.white)
    private val grey get() = ContextCompat.getColor(this, R.color.toggle_text_unselected)
    private val selBg get() = getDrawable(R.drawable.bg_toggle_selected)
    private val transBg get() = getDrawable(android.R.color.transparent)

    private val defaultCategories = listOf("Food", "Social Life", "Pets", "Transport", "Health", "Education", "Gift", "Apparel")
    private val defaultEmojis = mapOf(
        "Food" to "🍔", "Social Life" to "🎉", "Pets" to "🐾", "Transport" to "🚗",
        "Health" to "💊", "Education" to "📚", "Gift" to "🎁", "Apparel" to "👗"
    )
    private val defaultIncomeCategories = listOf("Allowance", "Salary", "Cash", "Bonus")
    private val defaultIncomeEmojis = mapOf(
        "Allowance" to "💰", "Salary" to "💼", "Cash" to "💵", "Bonus" to "🎯"
    )

    // For crop: source URI from gallery, destination file
    private var cropSourceUri: Uri? = null
    private var pendingCropIndex: Int = -1
    private var pendingCropPrefsKey: String = "cat_icons"
    private var currentSheetAdapter: BaseAdapter? = null
    private var currentCategories: MutableList<String>? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        cropSourceUri = uri
        launchCrop(uri)
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val croppedUri = result.data?.data ?: return@registerForActivityResult
            saveCroppedImageKeyed(croppedUri, pendingCropIndex, pendingCropPrefsKey)
            currentSheetAdapter?.notifyDataSetChanged()
        }
    }

    private fun launchCrop(sourceUri: Uri) {
        val destFile = File(cacheDir, "cropped/cat_${pendingCropPrefsKey}_$pendingCropIndex.jpg").also { it.parentFile?.mkdirs() }
        val destUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", destFile)
        val intent = Intent("com.android.camera.action.CROP").apply {
            setDataAndType(sourceUri, "image/*")
            putExtra("crop", "true")
            putExtra("aspectX", 1); putExtra("aspectY", 1)
            putExtra("outputX", 200); putExtra("outputY", 200)
            putExtra("output", destUri)
            putExtra("return-data", false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        // Fallback: if no crop app, just use the image directly
        val resolves = packageManager.queryIntentActivities(intent, 0)
        if (resolves.isNotEmpty()) {
            cropLauncher.launch(intent)
        } else {
            saveCroppedImageKeyed(sourceUri, pendingCropIndex, pendingCropPrefsKey)
            currentSheetAdapter?.notifyDataSetChanged()
        }
    }

    private fun saveCroppedImage(uri: Uri, index: Int) = saveCroppedImageKeyed(uri, index, "cat_icons")

    private fun getCategoryIconPath(index: Int, prefsKey: String = "cat_icons"): String? =
        getSharedPreferences(prefsKey, Context.MODE_PRIVATE).getString("icon_$index", null)

    private fun getCategoryEmoji(index: Int, name: String, prefsKey: String = "cat_icons", defaults: Map<String, String> = defaultEmojis): String? =
        getSharedPreferences(prefsKey, Context.MODE_PRIVATE).getString("emoji_$index", defaults[name])

    private fun saveCategoryEmoji(index: Int, emoji: String, prefsKey: String = "cat_icons") {
        getSharedPreferences(prefsKey, Context.MODE_PRIVATE).edit()
            .putString("emoji_$index", emoji)
            .remove("icon_$index")
            .apply()
        File(filesDir, "cat_icon_${prefsKey}_$index.jpg").delete()
    }

    private fun saveCroppedImageKeyed(uri: Uri, index: Int, prefsKey: String) {
        try {
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
            val dest = File(filesDir, "cat_icon_${prefsKey}_$index.jpg").also { it.parentFile?.mkdirs() }
            dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            getSharedPreferences(prefsKey, Context.MODE_PRIVATE).edit()
                .putString("icon_$index", dest.absolutePath).apply()
        } catch (_: Exception) {}
    }

    private fun getCategories(prefsKey: String = "categories", defaults: List<String> = defaultCategories): MutableList<String> {
        val prefs = getSharedPreferences(prefsKey, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("list", null)
        return if (saved != null) {
            (0 until saved.size).map { prefs.getString("cat_$it", defaults.getOrElse(it) { "" })!! }.toMutableList()
        } else {
            saveCategories(defaults, prefsKey)
            defaults.toMutableList()
        }
    }

    private fun saveCategories(list: List<String>, prefsKey: String = "categories") {
        val prefs = getSharedPreferences(prefsKey, Context.MODE_PRIVATE).edit()
        prefs.putStringSet("list", list.indices.map { it.toString() }.toSet())
        list.forEachIndexed { i, s -> prefs.putString("cat_$i", s) }
        prefs.apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val flipper = findViewById<ViewFlipper>(R.id.viewFlipper)
        val btnExpense = findViewById<AppCompatButton>(R.id.btnExpense)
        val btnIncome = findViewById<AppCompatButton>(R.id.btnIncome)
        val btnDebt = findViewById<AppCompatButton>(R.id.btnDebt)

        fun selectTab(type: String) {
            val idx = when (type) { "expense" -> 0; "income" -> 1; else -> 2 }
            val goingRight = idx > flipper.displayedChild
            flipper.inAnimation = android.view.animation.AnimationUtils.loadAnimation(
                this, if (goingRight) R.anim.slide_in_right else R.anim.slide_in_left)
            flipper.outAnimation = android.view.animation.AnimationUtils.loadAnimation(
                this, if (goingRight) R.anim.slide_out_left else R.anim.slide_out_right)
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

        val btnToGive = findViewById<AppCompatButton>(R.id.btnToGive)
        val btnToGet = findViewById<AppCompatButton>(R.id.btnToGet)
        fun selectDebtType(t: String) {
            debtSubType = t; selectedType = t
            btnToGive.background = if (t == "togive") selBg else transBg
            btnToGet.background = if (t == "toget") selBg else transBg
            btnToGive.setTextColor(if (t == "togive") white else grey)
            btnToGet.setTextColor(if (t == "toget") white else grey)
        }
        btnToGive.setOnClickListener { selectDebtType("togive") }
        btnToGet.setOnClickListener { selectDebtType("toget") }

        // Debt person autocomplete
        val etDebtPerson = findViewById<AutoCompleteTextView>(R.id.etDebtPerson)
        val persons = DatabaseHelper(this).getDebtPersonsSorted()
        val autoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, persons)
        etDebtPerson.setAdapter(autoAdapter)
        etDebtPerson.setOnClickListener {
            if (etDebtPerson.text.isEmpty()) etDebtPerson.showDropDown()
        }

        for (i in 0 until flipper.childCount) {
            flipper.getChildAt(i).findViewById<TextView>(R.id.tvDate)?.setOnClickListener { pickDateTime() }
        }
        for (i in 0 until flipper.childCount) {
            val child = flipper.getChildAt(i)
            child.findViewById<AppCompatButton>(R.id.btnSave)?.setOnClickListener { save(false) }
            child.findViewById<AppCompatButton>(R.id.btnContinue)?.setOnClickListener { save(true) }
        }

        findViewById<TextView>(R.id.etExpenseCategory).setOnClickListener {
            showCategorySheet(it as TextView, "expense")
        }
        findViewById<TextView>(R.id.etIncomeSource).setOnClickListener {
            showCategorySheet(it as TextView, "income")
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        selectTab("expense")
    }

    private fun showCategorySheet(tvCategory: TextView, type: String = "expense") {
        val sheet = BottomSheetDialog(this)
        val grid = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_categories, null) as GridView
        sheet.setContentView(grid)

        val isIncome = type == "income"
        val prefsKey = if (isIncome) "income_cat_icons" else "cat_icons"
        val catPrefsKey = if (isIncome) "income_categories" else "categories"
        val defaults = if (isIncome) defaultIncomeCategories else defaultCategories
        val defaultEmojiMap = if (isIncome) defaultIncomeEmojis else defaultEmojis
        val circleBg = if (isIncome) R.drawable.bg_circle_icon_green else R.drawable.bg_circle_icon

        val categories = getCategories(catPrefsKey, defaults)
        currentCategories = categories

        val adapter = object : BaseAdapter() {
            override fun getCount() = categories.size
            override fun getItem(pos: Int) = categories[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_category, parent, false)
                val name = categories[pos]
                v.findViewById<TextView>(R.id.tvCategoryName).text = name
                val tvEmoji = v.findViewById<TextView>(R.id.tvCategoryEmoji)
                val ivImage = v.findViewById<ImageView>(R.id.ivCategoryImage)
                tvEmoji.setBackgroundResource(circleBg)
                val imagePath = getCategoryIconPath(pos, prefsKey)
                if (imagePath != null && File(imagePath).exists()) {
                    tvEmoji.visibility = View.GONE
                    ivImage.visibility = View.VISIBLE
                    ivImage.setImageBitmap(BitmapFactory.decodeFile(imagePath))
                } else {
                    tvEmoji.visibility = View.VISIBLE
                    ivImage.visibility = View.GONE
                    tvEmoji.text = getCategoryEmoji(pos, name, prefsKey, defaultEmojiMap) ?: "•"
                }
                return v
            }
        }
        currentSheetAdapter = adapter
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, pos, _ ->
            tvCategory.text = categories[pos]
            sheet.dismiss()
        }

        grid.setOnItemLongClickListener { _, _, pos, _ ->
            showEditCategoryDialog(pos, categories, adapter, prefsKey, catPrefsKey)
            true
        }

        sheet.show()
    }

    private fun showEditCategoryDialog(pos: Int, categories: MutableList<String>, adapter: BaseAdapter,
                                       prefsKey: String = "cat_icons", catPrefsKey: String = "categories") {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_category, null)
        val etName = view.findViewById<EditText>(R.id.etEditName).apply { setText(categories[pos]) }
        val etEmoji = view.findViewById<EditText>(R.id.etEditEmoji).apply {
            setText(getCategoryEmoji(pos, categories[pos], prefsKey) ?: "")
        }
        view.findViewById<ImageView>(R.id.btnPickGallery).setOnClickListener {
            pendingCropIndex = pos
            pendingCropPrefsKey = prefsKey
            galleryLauncher.launch("image/*")
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Category")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                val newEmoji = etEmoji.text.toString().trim()
                if (newName.isNotEmpty()) { categories[pos] = newName; saveCategories(categories, catPrefsKey) }
                if (newEmoji.isNotEmpty()) saveCategoryEmoji(pos, newEmoji, prefsKey)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun currentChild(): View = findViewById<ViewFlipper>(R.id.viewFlipper).currentView

    private fun save(andContinue: Boolean) {
        val child = currentChild()
        val amountText = child.findViewById<EditText>(R.id.etAmount)?.text.toString().trim()

        if (amountText.isEmpty()) { child.findViewById<EditText>(R.id.etAmount)?.error = "Required"; return }

        val (title, category, note) = when (selectedType) {
            "income" -> {
                val src = child.findViewById<TextView>(R.id.etIncomeSource)?.text.toString().trim()
                if (src.isEmpty()) { Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show(); return }
                Triple(src, src, child.findViewById<EditText>(R.id.etIncomeNote)?.text.toString().trim())
            }
            "togive", "toget" -> {
                val person = child.findViewById<EditText>(R.id.etDebtPerson)?.text.toString().trim()
                if (person.isEmpty()) { child.findViewById<EditText>(R.id.etDebtPerson)?.error = "Required"; return }
                Triple(person, if (selectedType == "togive") "To Give" else "To Get",
                    child.findViewById<EditText>(R.id.etDebtNote)?.text.toString().trim())
            }
            else -> {
                val cat = child.findViewById<TextView>(R.id.etExpenseCategory)?.text.toString().trim()
                if (cat.isEmpty()) { Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show(); return }
                Triple(cat, cat, child.findViewById<EditText>(R.id.etExpenseNote)?.text.toString().trim())
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
            child.findViewById<EditText>(R.id.etExpenseNote)?.text?.clear()
            child.findViewById<EditText>(R.id.etIncomeNote)?.text?.clear()
            child.findViewById<EditText>(R.id.etDebtNote)?.text?.clear()
            child.findViewById<TextView>(R.id.etExpenseCategory)?.text = ""
            child.findViewById<TextView>(R.id.etIncomeSource)?.text = ""
            child.findViewById<EditText>(R.id.etDebtPerson)?.text?.clear()
            selectedDateMillis = System.currentTimeMillis()
            updateAllDates()
        } else {
            finish()
        }
    }
}
