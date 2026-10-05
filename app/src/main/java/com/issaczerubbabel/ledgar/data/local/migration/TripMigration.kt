package com.issaczerubbabel.ledgar.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.issaczerubbabel.ledgar.trip.LegacySplit
import com.issaczerubbabel.ledgar.trip.MemberPalette

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

    /**
     * 22 -> 23: gives each Member a stored colour (in Trip Member order) and turns Adjust and Exact
     * expenses into Custom ones with Locked amounts, keeping every Share exactly as saved (see
     * [LegacySplit]). Safe to run twice: columns are added once, colours are only assigned when the
     * column is new, and converted expenses no longer match the legacy modes.
     */
    fun upgradeTo23(db: SupportSQLiteDatabase) {
        val addedColour = "colorIndex" !in columnsOf(db, "trip_members")
        if (addedColour) db.execSQL("ALTER TABLE `trip_members` ADD COLUMN `colorIndex` INTEGER NOT NULL DEFAULT 0")
        if ("locked" !in columnsOf(db, "trip_expense_shares")) {
            db.execSQL("ALTER TABLE `trip_expense_shares` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
        }
        if (addedColour) assignColours(db)
        convertLegacySplits(db)
    }

    private fun assignColours(db: SupportSQLiteDatabase) {
        val members = mutableListOf<Pair<Long, Long>>() // trip, member
        db.query("SELECT tripId, id FROM trip_members ORDER BY tripId, isSelf DESC, displayOrder, id").use { c ->
            while (c.moveToNext()) members += c.getLong(0) to c.getLong(1)
        }
        var currentTrip = -1L
        var index = 0
        members.forEach { (tripId, memberId) ->
            if (tripId != currentTrip) { currentTrip = tripId; index = 0 }
            db.execSQL("UPDATE trip_members SET colorIndex = ${index % MemberPalette.colors.size} WHERE id = $memberId")
            index++
        }
    }

    private fun convertLegacySplits(db: SupportSQLiteDatabase) {
        class Legacy(val id: Long, val mode: String, val amount: Long)
        val legacy = mutableListOf<Legacy>()
        db.query("SELECT id, splitMode, amountPaise FROM trip_expenses WHERE splitMode IN ('ADJUST', 'EXACT')").use { c ->
            while (c.moveToNext()) legacy += Legacy(c.getLong(0), c.getString(1), c.getLong(2))
        }
        legacy.forEach { e ->
            val rows = mutableListOf<LegacySplit.Row>()
            db.query(
                "SELECT s.memberId, s.inputPaise, s.sharePaise FROM trip_expense_shares s " +
                    "JOIN trip_members m ON m.id = s.memberId WHERE s.expenseId = ${e.id} " +
                    "ORDER BY m.isSelf DESC, m.displayOrder, m.id"
            ).use { c -> while (c.moveToNext()) rows += LegacySplit.Row(c.getLong(0), c.getLong(1), c.getLong(2)) }
            val locked = LegacySplit.lockedFor(e.mode, e.id, e.amount, rows)
            db.execSQL("UPDATE trip_expense_shares SET locked = 0, inputPaise = 0 WHERE expenseId = ${e.id}")
            locked.forEach { (memberId, amount) ->
                db.execSQL("UPDATE trip_expense_shares SET locked = 1, inputPaise = $amount WHERE expenseId = ${e.id} AND memberId = $memberId")
            }
            db.execSQL("UPDATE trip_expenses SET splitMode = 'CUSTOM' WHERE id = ${e.id}")
        }
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            generateSequence { if (c.moveToNext()) c.getString(name) else null }.toSet()
        }

}
