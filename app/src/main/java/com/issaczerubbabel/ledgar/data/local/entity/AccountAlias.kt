package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved mapping from a bank account's last four digits or a UPI VPA to one of the user's own
 * Accounts, used to work out which Account a Captured transaction belongs to. See "Account
 * alias" in CONTEXT.md.
 */
@Entity(
    tableName = "account_aliases",
    foreignKeys = [
        ForeignKey(
            entity = AccountRecord::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId"])]
)
data class AccountAlias(
    @PrimaryKey val alias: String,
    val accountId: Long
)
