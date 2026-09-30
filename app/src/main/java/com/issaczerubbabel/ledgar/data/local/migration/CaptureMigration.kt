package com.issaczerubbabel.ledgar.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Creates the auto-capture tables (`captured_transactions`, `merchant_rules`, `account_aliases`,
 * `unparsed_alerts`). Run by the 20 -> 21 migration in DatabaseModule. Additive and idempotent
 * (IF NOT EXISTS): development builds of auto-capture numbered these tables 18 -> 19 and 19 -> 20
 * before sync phase 2 and Stats roles took those numbers, so an install from one already has some.
 * The statements match the DDL Room generates for the entities, which it checks on open.
 */
object CaptureMigration {

    fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `captured_transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sources` TEXT NOT NULL, `sender` TEXT NOT NULL, `rawText` TEXT NOT NULL, " +
                "`rawHash` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `txnTime` INTEGER NOT NULL, " +
                "`amount` REAL NOT NULL, `direction` TEXT NOT NULL, `channel` TEXT NOT NULL, " +
                "`merchantRaw` TEXT, `merchantNorm` TEXT, `accountHint` TEXT, `accountId` INTEGER, " +
                "`refNumber` TEXT, `suggestedType` TEXT, `suggestedCategory` TEXT, " +
                "`confidence` REAL NOT NULL, `decidedBy` TEXT, `traceJson` TEXT, `status` TEXT NOT NULL, " +
                "`confirmedExpenseId` INTEGER, `finalCategory` TEXT, " +
                "FOREIGN KEY(`accountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_captured_transactions_rawHash` ON `captured_transactions` (`rawHash`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_captured_transactions_refNumber` ON `captured_transactions` (`refNumber`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_captured_transactions_status` ON `captured_transactions` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_captured_transactions_txnTime` ON `captured_transactions` (`txnTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_captured_transactions_accountId` ON `captured_transactions` (`accountId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `merchant_rules` (" +
                "`merchantNorm` TEXT NOT NULL, `category` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`defaultAccountId` INTEGER, `origin` TEXT NOT NULL, `hitCount` INTEGER NOT NULL, " +
                "`streak` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`merchantNorm`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `account_aliases` (" +
                "`alias` TEXT NOT NULL, `accountId` INTEGER NOT NULL, PRIMARY KEY(`alias`), " +
                "FOREIGN KEY(`accountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_aliases_accountId` ON `account_aliases` (`accountId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `unparsed_alerts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sender` TEXT NOT NULL, `rawText` TEXT NOT NULL, `rawHash` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_unparsed_alerts_rawHash` ON `unparsed_alerts` (`rawHash`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_unparsed_alerts_capturedAt` ON `unparsed_alerts` (`capturedAt`)")
    }
}
