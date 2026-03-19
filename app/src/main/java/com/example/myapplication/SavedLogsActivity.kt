package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SavedLogsActivity : AppCompatActivity() {

    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_logs)

        findViewById<android.widget.ImageView>(R.id.ivBack).setOnClickListener { finish() }

        adapter = TransactionAdapter(
            transactions,
            onCheckClick = { t ->
                DatabaseHelper(this).markCompleted(t.id)
                loadStarred()
            },
            onCrossClick = { t ->
                DatabaseHelper(this).deleteTransaction(t.id)
                loadStarred()
                Toast.makeText(this, "${t.title} deleted", Toast.LENGTH_SHORT).show()
            },
            onStarToggle = { t, starred ->
                DatabaseHelper(this).toggleStar(t.id, starred)
                loadStarred()
            }
        )

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@SavedLogsActivity)
            adapter = this@SavedLogsActivity.adapter
        }

        loadStarred()
    }

    private fun loadStarred() {
        transactions.clear()
        transactions.addAll(DatabaseHelper(this).getStarredTransactions())
        adapter.notifyDataSetChanged()
    }
}
