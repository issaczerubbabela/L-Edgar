package com.issaczerubbabel.ledgar.data.local.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import java.time.YearMonth

/**
 * v16 -> v17: adds the salary-cycle bucket tables and seeds them from the old per-category
 * `budgets` table.
 *
 * The `budgets` table is left untouched (and keeps syncing to the `_budgets` sheet) so that
 * every existing backup still restores cleanly and there is a rollback path.
 */
object BucketBudgetMigration {

    private const val TAG = "BucketBudgetMigration"

    /** Category the old model used to store the month's overall budget. */
    const val LEGACY_TOTAL_CATEGORY = "__TOTAL__"

    data class LegacyBudget(val monthYear: String, val category: String, val amount: Double)

    data class SeedBucket(
        val name: String,
        val allocatedAmount: Double,
        val colorIndex: Int,
        val sortOrder: Int
    )

    data class SeedCycle(
        val startDate: String,
        val endDate: String,
        val spendableAmount: Double,
        val buckets: List<SeedBucket>
    )

    val MIGRATION_16_17: Migration = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createTables(db)
            // Tables are created above, outside this guard, so a seeding problem can never
            // leave the schema unable to open. Worst case the user starts with an empty screen.
            try {
                seedFromLegacyBudgets(db)
            } catch (e: Exception) {
                Log.e(TAG, "Could not carry old budgets into the bucket model", e)
                clearSeededRows(db)
            }
        }
    }

    /**
     * Turns the old per-category budgets into the first cycle: the most recent month becomes
     * one running cycle, and each category budget becomes a single-category bucket.
     * Returns null when there is nothing usable to carry over.
     *
     * Pure function so the conversion rules can be unit tested without a database.
     */
    fun plan(rows: List<LegacyBudget>): SeedCycle? {
        val byMonth = rows.mapNotNull { row ->
            val month = runCatching { YearMonth.parse(row.monthYear.trim()) }.getOrNull()
            month?.let { it to row }
        }
        val latest = byMonth.maxOfOrNull { it.first } ?: return null
        val monthRows = byMonth.filter { it.first == latest }.map { it.second }

        val total = monthRows
            .firstOrNull { it.category.equals(LEGACY_TOTAL_CATEGORY, ignoreCase = true) }
            ?.amount
            ?.takeIf { it.isFinite() && it >= 0.0 }

        // The old unique index was case-sensitive, so "Food" and "food" can both exist. The new
        // one is not, and category spend is matched ignoring case everywhere, so merge them.
        val merged = LinkedHashMap<String, Pair<String, Double>>()
        monthRows
            .filterNot { it.category.equals(LEGACY_TOTAL_CATEGORY, ignoreCase = true) }
            .filter { it.category.isNotBlank() }
            .sortedBy { it.category }
            .forEach { row ->
                val name = row.category.trim()
                val amount = row.amount.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                val key = name.lowercase()
                val existing = merged[key]
                merged[key] = if (existing == null) name to amount else existing.first to (existing.second + amount)
            }

        val buckets = merged.values.mapIndexed { index, (name, amount) ->
            SeedBucket(
                name = name,
                allocatedAmount = amount,
                colorIndex = BudgetBucket.colorIndexFor(name),
                sortOrder = index
            )
        }

        return SeedCycle(
            startDate = latest.atDay(1).toString(),
            endDate = latest.atEndOfMonth().toString(),
            spendableAmount = total ?: buckets.sumOf { it.allocatedAmount },
            buckets = buckets
        )
    }

    private fun seedFromLegacyBudgets(db: SupportSQLiteDatabase) {
        val rows = db.query("SELECT monthYear, category, amount FROM budgets").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(LegacyBudget(cursor.getString(0), cursor.getString(1), cursor.getDouble(2)))
                }
            }
        }
        val cycle = plan(rows) ?: return

        val cycleId = db.insert(
            "budget_cycles",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("startDate", cycle.startDate)
                put("endDate", cycle.endDate)
                put("spendableAmount", cycle.spendableAmount)
                putNull("closedAt")
            }
        )

        cycle.buckets.forEach { bucket ->
            val bucketId = db.insert(
                "budget_buckets",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("cycleId", cycleId)
                    put("name", bucket.name)
                    put("note", "")
                    put("colorIndex", bucket.colorIndex)
                    put("emoji", "")
                    put("allocatedAmount", bucket.allocatedAmount)
                    put("sortOrder", bucket.sortOrder)
                }
            )
            db.insert(
                "bucket_categories",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("cycleId", cycleId)
                    put("bucketId", bucketId)
                    put("category", bucket.name)
                }
            )
        }
    }

    /**
     * Foreign keys are not enforced while a migration runs, so cascading deletes would not fire;
     * clear the three tables explicitly, children first.
     */
    private fun clearSeededRows(db: SupportSQLiteDatabase) {
        runCatching {
            db.execSQL("DELETE FROM bucket_categories")
            db.execSQL("DELETE FROM budget_buckets")
            db.execSQL("DELETE FROM budget_cycles")
        }
    }

    // These statements are copied from the DDL Room generates for the entities
    // (SheetSyncDatabase_Impl). Room validates the migrated schema against the entities on open,
    // so they have to match it exactly, including column order, collation and index names.
    private fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_cycles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, `spendableAmount` REAL NOT NULL, `closedAt` TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_buckets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`cycleId` INTEGER NOT NULL, `name` TEXT NOT NULL, `note` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, " +
                "`emoji` TEXT NOT NULL, `allocatedAmount` REAL NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "FOREIGN KEY(`cycleId`) REFERENCES `budget_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_buckets_cycleId` ON `budget_buckets` (`cycleId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_buckets_id_cycleId` ON `budget_buckets` (`id`, `cycleId`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bucket_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`cycleId` INTEGER NOT NULL, `bucketId` INTEGER NOT NULL, `category` TEXT NOT NULL COLLATE NOCASE, " +
                "FOREIGN KEY(`bucketId`, `cycleId`) REFERENCES `budget_buckets`(`id`, `cycleId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_bucket_categories_cycleId_category` " +
                "ON `bucket_categories` (`cycleId`, `category`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bucket_categories_bucketId_cycleId` " +
                "ON `bucket_categories` (`bucketId`, `cycleId`)"
        )
    }
}
