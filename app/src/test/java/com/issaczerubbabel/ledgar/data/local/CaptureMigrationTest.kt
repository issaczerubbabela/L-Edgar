package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration
import com.issaczerubbabel.ledgar.data.local.migration.CaptureMigration
import com.issaczerubbabel.ledgar.di.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The auto-capture tables arrive in 20 -> 21. Development builds of auto-capture numbered them 18 -> 19
 * and 19 -> 20 before sync phase 2 and Stats roles took those numbers, so each starting point below
 * must open (Room checks the whole schema on open) without losing data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    private fun createAt(version: Int, build: (SupportSQLiteDatabase) -> Unit) {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        VERSION_16_SCHEMA.forEach(db::execSQL)
                        BucketBudgetMigration.MIGRATION_16_17.migrate(db)
                        DatabaseModule.MIGRATION_17_18.migrate(db)
                        build(db)
                        db.execSQL("INSERT INTO account_records (groupName, accountName, initialBalance, initialBalanceDate, isHidden, displayOrder, includeInTotals) VALUES ('Accounts', 'HDFC Savings', 0, '2026-01-01', 0, 0, 1)")
                        db.execSQL(
                            "INSERT INTO expense_records (date, type, category, description, amount, remarks, isBookmarked, isSynced, remoteTimestamp, syncAction) " +
                                "VALUES ('2026-09-01', 'Expense', 'Food', 'Kept through the upgrade', 42.5, '', 0, 0, NULL, 'INSERT')"
                        )
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase
        helper.close()
    }

    private fun insertCapture(db: SupportSQLiteDatabase) = db.execSQL(
        "INSERT INTO captured_transactions (sources, sender, rawText, rawHash, capturedAt, txnTime, amount, direction, channel, confidence, status) " +
            "VALUES ('NOTIF', 'HDFC Bank', 'Sent Rs.70.00', 'hash-1', 1, 1, 70.0, 'DEBIT', 'UPI', 0.0, 'PENDING')"
    )

    private fun columns(db: SheetSyncDatabase, table: String): Set<String> =
        db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toSet()
        }

    private fun assertFullyUpgraded(db: SheetSyncDatabase) = runBlocking {
        assertEquals("Kept through the upgrade", db.expenseDao().getAllRecordsSnapshot().single().description)
        assertTrue("sync columns", columns(db, "expense_records").containsAll(setOf("syncId", "syncedRevision", "sheetConflictJson")))
        assertTrue("roles", "role" in columns(db, "dropdown_options"))
        // Every capture table works through its DAO.
        db.unparsedAlertDao().insert(UnparsedAlert(sender = "AD-HDFCBK-T", rawText = "x", rawHash = "u1", capturedAt = 1))
        assertEquals(1, db.unparsedAlertDao().getAllSnapshot().size)
        assertEquals(null, db.merchantRuleDao().getByMerchant("NOBODY"))
        assertTrue(db.accountAliasDao().getAllSnapshot().isEmpty())
    }

    @Test
    fun mainsVersion20GetsTheCaptureTables() = runBlocking {
        createAt(20) { db ->
            DatabaseModule.MIGRATION_18_19.migrate(db)
            DatabaseModule.MIGRATION_19_20.migrate(db)
        }

        val db = DatabaseModule.provideDatabase(context)
        assertFullyUpgraded(db)
        assertTrue(db.captureDao().observePending().first().isEmpty())
        db.close()
    }

    @Test
    fun aDevelopmentBuildsVersion20WithCaptureTablesButNoSyncColumnsIsRepaired() = runBlocking {
        createAt(20) { db ->
            CaptureMigration.createTables(db)
            insertCapture(db)
        }

        val db = DatabaseModule.provideDatabase(context)
        assertFullyUpgraded(db)
        assertEquals("the capture made before the upgrade is kept", 1, db.captureDao().observePending().first().size)
        db.close()
    }

    @Test
    fun aDevelopmentBuildsVersion19WithoutTheUnparsedTableIsRepaired() = runBlocking {
        createAt(19) { db ->
            CaptureMigration.createTables(db)
            db.execSQL("DROP TABLE unparsed_alerts")
            insertCapture(db)
        }

        val db = DatabaseModule.provideDatabase(context)
        assertFullyUpgraded(db)
        assertEquals(1, db.captureDao().observePending().first().size)
        db.close()
    }

    private companion object {
        const val DB_NAME = "sheetsync.db"

        val VERSION_16_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `expense_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `amount` REAL NOT NULL, `accountId` INTEGER, `remarks` TEXT NOT NULL, `fromAccountId` INTEGER, `toAccountId` INTEGER, `accountName` TEXT, `fromAccountName` TEXT, `toAccountName` TEXT, `isBookmarked` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, `remoteTimestamp` TEXT, `syncAction` TEXT NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`fromAccountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`toAccountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_expense_records_accountId` ON `expense_records` (`accountId`)",
            "CREATE INDEX IF NOT EXISTS `index_expense_records_fromAccountId` ON `expense_records` (`fromAccountId`)",
            "CREATE INDEX IF NOT EXISTS `index_expense_records_toAccountId` ON `expense_records` (`toAccountId`)",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `monthYear` TEXT NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_monthYear_category` ON `budgets` (`monthYear`, `category`)",
            "CREATE TABLE IF NOT EXISTS `account_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupName` TEXT NOT NULL, `accountName` TEXT NOT NULL, `initialBalance` REAL NOT NULL, `initialBalanceDate` TEXT NOT NULL, `isHidden` INTEGER NOT NULL, `displayOrder` INTEGER NOT NULL, `description` TEXT, `includeInTotals` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `dropdown_options` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `optionType` TEXT NOT NULL, `name` TEXT NOT NULL, `displayOrder` INTEGER NOT NULL)"
        )
    }
}
