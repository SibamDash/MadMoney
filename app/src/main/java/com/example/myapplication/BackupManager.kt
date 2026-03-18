package com.example.myapplication

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object BackupManager {

    fun exportToJson(context: Context, uri: Uri): Result<Int> = runCatching {
        val db = DatabaseHelper(context)
        // Export all time — use a wide range
        val all = db.getTransactions(0L, Long.MAX_VALUE)
        val array = JSONArray()
        all.forEach { t ->
            array.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("type", t.type)
                put("amount", t.amount)
                put("category", t.category)
                put("account", t.account)
                put("note", t.note)
                put("description", t.description)
                put("date", t.date)
                put("isCompleted", t.isCompleted)
            })
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(array.toString(2).toByteArray()) }
        all.size
    }

    fun importFromJson(context: Context, uri: Uri): Result<Int> = runCatching {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).forEachLine { sb.append(it) }
        }
        val array = JSONArray(sb.toString())
        val db = DatabaseHelper(context)
        var count = 0
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            db.addTransaction(Transaction(
                title = o.getString("title"),
                type = o.getString("type"),
                amount = o.getDouble("amount"),
                category = o.getString("category"),
                account = o.optString("account", ""),
                note = o.optString("note", ""),
                description = o.optString("description", ""),
                date = o.getLong("date"),
                isCompleted = o.optBoolean("isCompleted", false)
            ))
            count++
        }
        count
    }
}
