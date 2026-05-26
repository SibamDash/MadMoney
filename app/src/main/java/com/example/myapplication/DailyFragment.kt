package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DailyFragment : Fragment() {

    private lateinit var adapter: GroupedTransactionAdapter
    private var activeFilter: String = "all"
    private var searchQuery: String = ""
    private var searchField: String = "all"
    private var selectedDate: Triple<Int,Int,Int>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            id = R.id.recyclerView
        }
        return rv
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = GroupedTransactionAdapter(
            onCheckClick = { t ->
                DatabaseHelper(requireContext()).markCompleted(t.id)
                load()
            },
            onCrossClick = { t ->
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete Transaction")
                    .setMessage("Delete \"${t.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        DatabaseHelper(requireContext()).deleteTransaction(t.id)
                        load()
                        com.google.android.material.snackbar.Snackbar.make(
                            requireActivity().findViewById(R.id.main), "${t.title} deleted", 5000
                        ).setAction("Undo") {
                            DatabaseHelper(requireContext()).addTransaction(t); load()
                        }.show()
                    }
                    .setNegativeButton("Cancel", null).show()
            },
            onStarToggle = { t, starred ->
                DatabaseHelper(requireContext()).toggleStar(t.id, starred); load()
            }
        )
        (view as RecyclerView).adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    fun setFilter(filter: String) { activeFilter = filter; if (isAdded) load() }
    fun setSearch(query: String) { searchQuery = query; if (isAdded) load() }
    fun setSearchField(field: String) { searchField = field; if (isAdded) load() }
    fun setDateFilter(year: Int, month: Int, day: Int) { selectedDate = Triple(year, month, day); if (isAdded) load() }
    fun clearDateFilter() { selectedDate = null; if (isAdded) load() }

    fun load() {
        val cal = selectedDate
        val (start, end) = if (cal != null) {
            val s = java.util.Calendar.getInstance().apply { set(cal.first, cal.second, cal.third, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }
            val e = java.util.Calendar.getInstance().apply { set(cal.first, cal.second, cal.third, 23, 59, 59); set(java.util.Calendar.MILLISECOND, 999) }
            Pair(s.timeInMillis, e.timeInMillis)
        } else {
            Pair(
                java.util.Calendar.getInstance().apply { set(2000, 0, 1, 0, 0, 0) }.timeInMillis,
                java.util.Calendar.getInstance().apply { set(2100, 0, 1, 0, 0, 0) }.timeInMillis
            )
        }
        val all   = DatabaseHelper(requireContext()).getTransactions(start, end)

        var filtered = when (activeFilter) {
            "expense" -> all.filter { it.type == "expense" }
            "income"  -> all.filter { it.type == "income" }
            "debts"   -> all.filter { it.type == "togive" || it.type == "toget" }
            else      -> all
        }
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            val dateFmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            filtered = filtered.filter { t ->
                when (searchField) {
                    "name"     -> t.title.lowercase().contains(q)
                    "category" -> t.category.lowercase().contains(q)
                    "amount"   -> t.amount.toString().contains(q)
                    "date"     -> dateFmt.format(java.util.Date(t.date)).lowercase().contains(q)
                    else       -> t.title.lowercase().contains(q) || t.category.lowercase().contains(q) ||
                                  t.note.lowercase().contains(q)  || t.amount.toString().contains(q) ||
                                  dateFmt.format(java.util.Date(t.date)).lowercase().contains(q)
                }
            }
        }
        adapter.submitList(filtered)

        // Update summary in host activity
        (activity as? MainActivity)?.updateSummary(all)
    }
}
