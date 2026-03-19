package com.example.myapplication

data class Transaction(
    val id: Long = 0,
    val title: String,
    val category: String,
    val amount: Double,
    val type: String,
    val account: String = "",
    val note: String = "",
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val completedAt: Long = 0,
    val isStarred: Boolean = false
)
