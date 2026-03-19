package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "transactions.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                account TEXT NOT NULL,
                note TEXT,
                description TEXT,
                date INTEGER NOT NULL,
                isCompleted INTEGER DEFAULT 0,
                completedAt INTEGER DEFAULT 0,
                isStarred INTEGER DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN completedAt INTEGER DEFAULT 0")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN isStarred INTEGER DEFAULT 0")
        }
    }

    fun addTransaction(transaction: Transaction): Long {
        val values = ContentValues().apply {
            put("title", transaction.title)
            put("type", transaction.type)
            put("amount", transaction.amount)
            put("category", transaction.category)
            put("account", transaction.account)
            put("note", transaction.note)
            put("description", transaction.description)
            put("date", transaction.date)
            put("isCompleted", if (transaction.isCompleted) 1 else 0)
        }
        return writableDatabase.insert("transactions", null, values)
    }

    fun getTransactions(startDate: Long, endDate: Long): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val cursor = readableDatabase.query(
            "transactions",
            null,
            "date BETWEEN ? AND ?",
            arrayOf(startDate.toString(), endDate.toString()),
            null, null, "date DESC"
        )
        
        cursor.use {
            while (it.moveToNext()) {
                list.add(Transaction(
                    id = it.getLong(0),
                    title = it.getString(1),
                    type = it.getString(2),
                    amount = it.getDouble(3),
                    category = it.getString(4),
                    account = it.getString(5),
                    note = it.getString(6) ?: "",
                    description = it.getString(7) ?: "",
                    date = it.getLong(8),
                    isCompleted = it.getInt(9) == 1,
                    completedAt = it.getLong(10),
                    isStarred = it.getInt(11) == 1
                ))
            }
        }
        return list
    }

    fun markCompleted(id: Long) {
        val values = ContentValues().apply {
            put("isCompleted", 1)
            put("completedAt", System.currentTimeMillis())
        }
        writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
    }

    fun getDebtPersonsSorted(): List<String> {
        val cursor = readableDatabase.rawQuery(
            "SELECT title, COUNT(*) as cnt, MAX(date) as last FROM transactions WHERE type IN ('togive','toget') GROUP BY title ORDER BY cnt DESC, last DESC",
            null
        )
        val list = mutableListOf<String>()
        cursor.use { while (it.moveToNext()) list.add(it.getString(0)) }
        return list
    }

    fun deleteTransaction(id: Long) {
        writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
    }

    fun toggleStar(id: Long, starred: Boolean) {
        val values = ContentValues().apply { put("isStarred", if (starred) 1 else 0) }
        writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
    }

    fun getStarredTransactions(): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val cursor = readableDatabase.query(
            "transactions", null, "isStarred = 1", null, null, null, "date DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(Transaction(
                    id = it.getLong(0), title = it.getString(1), type = it.getString(2),
                    amount = it.getDouble(3), category = it.getString(4), account = it.getString(5),
                    note = it.getString(6) ?: "", description = it.getString(7) ?: "",
                    date = it.getLong(8), isCompleted = it.getInt(9) == 1,
                    completedAt = it.getLong(10), isStarred = it.getInt(11) == 1
                ))
            }
        }
        return list
    }
}
