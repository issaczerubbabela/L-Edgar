package com.sheetsync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_records")
data class AccountRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupName: String,
    val accountName: String,
    val initialBalance: Double,
    val isHidden: Boolean = false
) {
    // Backward-compatible alias for older call sites still using accountGroup.
    val accountGroup: String
        get() = groupName
}
