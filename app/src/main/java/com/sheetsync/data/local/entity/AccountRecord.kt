package com.sheetsync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_records")
data class AccountRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountName: String,
    val accountType: String, // Cash | Bank | Card
    val initialBalance: Double
)
