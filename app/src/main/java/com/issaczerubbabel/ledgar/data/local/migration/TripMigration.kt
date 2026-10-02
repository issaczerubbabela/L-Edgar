package com.issaczerubbabel.ledgar.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Creates the Trip tables. Run by the 21 -> 22 migration in DatabaseModule. Additive and idempotent
 * (IF NOT EXISTS). The statements match the DDL Room generates for the entities in TripRecords.kt,
 * which it checks on open.
 */
object TripMigration {

    fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `status` TEXT NOT NULL, `postedAt` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, `name` TEXT NOT NULL, `isSelf` INTEGER NOT NULL, `upiId` TEXT, " +
                "`displayOrder` INTEGER NOT NULL, " +
                "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId` ON `trip_members` (`tripId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, `date` TEXT NOT NULL, `purpose` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, " +
                "`payerMemberId` INTEGER NOT NULL, `splitMode` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`postedExpenseId` INTEGER, " +
                "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_expenses_tripId` ON `trip_expenses` (`tripId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_expense_shares` (`expenseId` INTEGER NOT NULL, `memberId` INTEGER NOT NULL, " +
                "`inputPaise` INTEGER NOT NULL, `sharePaise` INTEGER NOT NULL, PRIMARY KEY(`expenseId`, `memberId`), " +
                "FOREIGN KEY(`expenseId`) REFERENCES `trip_expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_expense_shares_memberId` ON `trip_expense_shares` (`memberId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_settlements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, `fromMemberId` INTEGER NOT NULL, `toMemberId` INTEGER NOT NULL, " +
                "`amountPaise` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_settlements_tripId` ON `trip_settlements` (`tripId`)")
    }
}
