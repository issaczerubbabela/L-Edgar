package com.sheetsync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_records")
data class BudgetRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val iconEmoji: String,
    val amount: Double,
    val createdAt: Long = System.currentTimeMillis()
)
