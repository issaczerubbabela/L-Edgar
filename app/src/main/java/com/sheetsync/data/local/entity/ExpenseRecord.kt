package com.sheetsync.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_records",
    foreignKeys = [
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["fromAccountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("fromAccountId"), Index("toAccountId")]
)
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,           // yyyy-MM-dd
    val type: String,           // "Expense" | "Income" | "Transfer"
    val category: String,
    val description: String,
    val amount: Double,
    val paymentMode: String,
    val remarks: String,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val isSynced: Boolean = false,
    val remoteTimestamp: String? = null
)
