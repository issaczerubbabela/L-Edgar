package com.issaczerubbabel.ledgar.data.local.entity

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Raises `localVersion` whenever a Transaction's content or Sync state changes, from any write path,
 * so Sync can tell whether a row changed while its request was in flight. Writes that carry a stale
 * or default version (a whole-row `@Update` built in the UI) are still pushed past the old value.
 */
object ExpenseVersionTrigger {
    private const val NAME = "expense_records_bump_local_version"

    private val WATCHED_COLUMNS = listOf(
        "date", "type", "category", "description", "amount", "accountId", "remarks",
        "fromAccountId", "toAccountId", "accountName", "fromAccountName", "toAccountName",
        "isBookmarked", "isSynced", "remoteTimestamp", "syncAction"
    ).joinToString(", ")

    val CREATE_SQL = """
        CREATE TRIGGER IF NOT EXISTS $NAME
        AFTER UPDATE OF $WATCHED_COLUMNS ON expense_records
        FOR EACH ROW WHEN NEW.localVersion <= OLD.localVersion
        BEGIN
            UPDATE expense_records SET localVersion = OLD.localVersion + 1 WHERE id = OLD.id;
        END
    """.trimIndent()

    fun install(db: SupportSQLiteDatabase) = db.execSQL(CREATE_SQL)
}
