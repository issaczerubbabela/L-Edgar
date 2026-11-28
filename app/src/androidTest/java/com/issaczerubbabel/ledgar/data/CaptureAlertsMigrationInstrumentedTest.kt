package com.issaczerubbabel.ledgar.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration
import com.issaczerubbabel.ledgar.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 28 -> 29 upgrade on the phone's own SQLite: every existing capture keeps its one Alert
 * (ADR-0012). It uses a database file of its own, never the app's `sheetsync.db`, and opens it
 * with no destructive fallback, so a schema Room disagrees with fails here instead of wiping data.
 */
@RunWith(AndroidJUnit4::class)
class CaptureAlertsMigrationInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createVersion28Database() {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(28) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        VERSION_16_SCHEMA.forEach(db::execSQL)
                        listOf(
                            BucketBudgetMigration.MIGRATION_16_17,
                            DatabaseModule.MIGRATION_17_18,
                            DatabaseModule.MIGRATION_18_19,
                            DatabaseModule.MIGRATION_19_20,
                            DatabaseModule.MIGRATION_20_21,
                            DatabaseModule.MIGRATION_21_22,
                            DatabaseModule.MIGRATION_22_23,
                            DatabaseModule.MIGRATION_23_24,
                            DatabaseModule.MIGRATION_24_25,
                            DatabaseModule.MIGRATION_25_26,
                            DatabaseModule.MIGRATION_26_27,
                            DatabaseModule.MIGRATION_27_28
                        ).forEach { it.migrate(db) }
                        db.execSQL(
                            "INSERT INTO captured_transactions (sources, sender, rawText, rawHash, capturedAt, txnTime, amount, direction, channel, confidence, status) VALUES " +
                                "('NOTIF', 'AD-HDFCBK-S', 'Credit Alert! Rs.1.00 credited to HDFC Bank A/c XX1234 on 28-11-26 from VPA *00-0@okaxis (UPI 111111111111)', 'hash-1', 100, 100, 1.0, 'CREDIT', 'UPI', 0.0, 'PENDING'), " +
                                "('NOTIF', 'Google Pay', 'A B KUMAR paid you ₹1.00\nTap to view.', 'hash-2', 200, 200, 1.0, 'CREDIT', 'UPI', 0.0, 'LINKED')"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase
        helper.close()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun everyCaptureKeepsItsOneAlert() = runBlocking {
        val db = Room.databaseBuilder(context, SheetSyncDatabase::class.java, DB_NAME)
            .addMigrations(DatabaseModule.MIGRATION_28_29)
            .build()
        val dao = db.captureDao()
        val waiting = dao.getPendingSnapshot().single()

        val alert = dao.getAlerts(waiting.id).single()
        assertEquals("AD-HDFCBK-S", alert.sender)
        assertEquals(waiting.rawText, alert.rawText)
        assertEquals("hash-1", alert.rawHash)
        assertEquals(100L, alert.capturedAt)
        assertEquals("Google Pay", dao.getAlertsFor(listOf(waiting.id + 1)).single().sender)
        assertTrue(dao.hasAlert("hash-2"))

        dao.deleteAllPending()
        assertTrue("a capture's Alerts go with it", dao.getAlerts(waiting.id).isEmpty())
        db.close()
    }

    private companion object {
        const val DB_NAME = "capture-alerts-migration-test.db"

        /** The schema version 16 had, which every later migration builds on (as in the unit tests' `CaptureMigrationTest`). */
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
