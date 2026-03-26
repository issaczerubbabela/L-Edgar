package com.sheetsync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_records")
data class AccountRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountGroup: String,
    val accountName: String,
    val initialBalance: Double
)
