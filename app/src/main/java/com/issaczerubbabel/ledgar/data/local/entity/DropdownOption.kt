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

    /** An Account group whose Accounts hold money owed, such as credit cards and loans. */
    const val LIABILITY = "LIABILITY"

    /**
     * Words that made an Account group a liability before the role existed. Used once, by the
     * 23 -> 24 upgrade, to give those groups the [LIABILITY] role; nothing guesses from names after.
     */
    val LEGACY_LIABILITY_KEYWORDS = listOf("credit card", "loan", "owed", "overdraft", "debt", "payable")

    /** Roles given once, when the column is added or a fresh install seeds its options. */
    val DEFAULTS: List<Triple<String, String, String>> = listOf(
        Triple("EXPENSE_CATEGORY", "Investments/Savings", SAVING),
        Triple("INCOME_CATEGORY", "Return", REFUND),
        Triple("ACCOUNT_GROUP", "Savings", SAVINGS),
        Triple("ACCOUNT_GROUP", "Investments", SAVINGS),
        Triple("ACCOUNT_GROUP", "Overdrafts", LIABILITY),
        Triple("ACCOUNT_GROUP", "Loan", LIABILITY)
    )
}
