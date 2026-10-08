package com.issaczerubbabel.ledgar.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole

/** The 23 -> 24 upgrade for the Accounts revamp. Run by DatabaseModule; every step is idempotent. */
object AccountMigration {

    fun upgradeTo24(db: SupportSQLiteDatabase) {
        if ("reconciledAt" !in columnsOf(db, "account_records")) {
            db.execSQL("ALTER TABLE account_records ADD COLUMN reconciledAt TEXT")
        }
        giveLiabilityRoleByLegacyKeywords(db)
        linkTransfersByDestinationName(db)
    }

    /** Groups today's keywords treat as liabilities get the role, unless the user already chose one. */
    private fun giveLiabilityRoleByLegacyKeywords(db: SupportSQLiteDatabase) {
        DropdownRole.LEGACY_LIABILITY_KEYWORDS.forEach { keyword ->
            db.execSQL(
                "UPDATE dropdown_options SET role = ? WHERE optionType = 'ACCOUNT_GROUP' AND role = '' AND LOWER(name) LIKE ?",
                arrayOf<Any>(DropdownRole.LIABILITY, "%$keyword%")
            )
        }
    }

    /**
     * Older Transfers stored only the destination's name. Link each to its Account when exactly one
     * Account has that name (trimmed, ignoring case); leave no match or an ambiguous one unlinked.
     */
    private fun linkTransfersByDestinationName(db: SupportSQLiteDatabase) {
        val sameName = "FROM account_records a WHERE LOWER(TRIM(a.accountName)) = LOWER(TRIM(expense_records.toAccountName))"
        db.execSQL(
            """
            UPDATE expense_records SET toAccountId = (SELECT a.id $sameName)
            WHERE LOWER(type) = 'transfer' AND toAccountId IS NULL AND toAccountName IS NOT NULL
              AND (SELECT COUNT(*) $sameName) = 1
            """.trimIndent()
        )
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameColumn) else null }.toSet()
        }
}
