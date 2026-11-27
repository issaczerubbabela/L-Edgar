package com.issaczerubbabel.ledgar.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Creates the Fund tables (ADR-0011). Run by the 27 -> 28 migration in DatabaseModule. Additive and
 * idempotent (IF NOT EXISTS). The statements match the DDL Room generates for the entities in
 * FundRecords.kt, which it checks on open.
 */
object FundMigration {

    fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `funds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `targetAmount` REAL, `dueDate` TEXT, `archivedAt` TEXT, " +
                "`sortOrder` INTEGER NOT NULL, `createdDate` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fund_holdings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`fundId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                "`openingAmount` REAL NOT NULL, `startDate` TEXT NOT NULL, " +
                "FOREIGN KEY(`fundId`) REFERENCES `funds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`accountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_fund_holdings_fundId_accountId` ON `fund_holdings` (`fundId`, `accountId`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_holdings_accountId` ON `fund_holdings` (`accountId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fund_plans` (`fundId` INTEGER NOT NULL, `cycleId` INTEGER NOT NULL, " +
                "`plannedAmount` REAL NOT NULL, PRIMARY KEY(`fundId`, `cycleId`), " +
                "FOREIGN KEY(`fundId`) REFERENCES `funds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`cycleId`) REFERENCES `budget_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_plans_cycleId` ON `fund_plans` (`cycleId`)")
    }
}
