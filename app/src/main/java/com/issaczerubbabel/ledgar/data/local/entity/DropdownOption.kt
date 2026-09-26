package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dropdown_options")
data class DropdownOption(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val optionType: String,
    val name: String,
    val displayOrder: Int,
    /** The option's Stats role, one of [DropdownRole], or empty for none (see ADR-0004). */
    @ColumnInfo(defaultValue = "") val role: String = ""
)

/** The part a Dropdown option plays in Stats. Each role belongs to one option type. */
object DropdownRole {
    /** An expense Category whose money is Saved, not Spent. */
    const val SAVING = "SAVING"

    /** An income Category that reduces Spent instead of adding to Earned. */
    const val REFUND = "REFUND"

    /** An Account group whose incoming Transfers count as Saved. */
    const val SAVINGS = "SAVINGS"

    /** Roles given once, when the column is added or a fresh install seeds its options. */
    val DEFAULTS: List<Triple<String, String, String>> = listOf(
        Triple("EXPENSE_CATEGORY", "Investments/Savings", SAVING),
        Triple("INCOME_CATEGORY", "Return", REFUND),
        Triple("ACCOUNT_GROUP", "Savings", SAVINGS),
        Triple("ACCOUNT_GROUP", "Investments", SAVINGS)
    )
}
