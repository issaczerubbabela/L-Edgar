package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_records")
data class AccountRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupName: String,
    val accountName: String,
    val initialBalance: Double,
    val initialBalanceDate: String = "1970-01-01",
    val isHidden: Boolean = false,
    val displayOrder: Int = 0,
    val description: String? = null,
    val includeInTotals: Boolean = true
) {
    // Backward-compatible alias for older call sites still using accountGroup.
    val accountGroup: String
        get() = groupName
}
