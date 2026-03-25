package com.sheetsync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_records")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,           // yyyy-MM-dd
    val type: String,           // "Expense" | "Income"
    val category: String,
    val description: String,
    val amount: Double,
    val paymentMode: String,
    val remarks: String,
    val isSynced: Boolean = false
)
