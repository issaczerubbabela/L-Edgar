package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens a real version-20 database through the app's own builder and lets it migrate to 21, the
 * same way [DatabaseUpgradeTest] checks the version-16 path. A broken migration would not crash:
 * `fallbackToDestructiveMigration` would silently erase local data, unsynced changes included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class RecurringRuleMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createVersion20Database() {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                    override fun onCreate(db: SupportSQLiteDatabase) = VERSION_20_SCHEMA.forEach(db::execSQL)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO account_records (groupName, accountName, initialBalance, initialBalanceDate, isHidden, displayOrder, includeInTotals) " +
                "VALUES ('Cash', 'Wallet', 0, '1970-01-01', 0, 0, 1)"
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO expense_records (date, type, category, description, amount, accountId, remarks, isBookmarked, isSynced, remoteTimestamp, syncAction, localVersion) " +
                "VALUES ('2026-09-01', 'Expense', 'Food', 'Unsynced lunch', 42.5, 1, '', 0, 0, NULL, 'INSERT', 0)"
        )
        helper.close()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `upgrading from version 20 adds the recurring rules table and links a Transaction to a rule`() = runBlocking {
        val db = DatabaseModule.provideDatabase(context)
        val expenseDao = db.expenseDao()
        val ruleDao = db.recurringRuleDao()

        val kept = expenseDao.getAllRecordsSnapshot().single()
        assertNull("existing Transactions start unlinked to any rule", kept.recurringRuleId)

        val accountId = db.accountDao().getAllAccountsSnapshot().single().id
        val ruleId = ruleDao.insert(
            RecurringRule(
                type = "Expense",
                category = "Rent",
                description = "Rent",
                amount = 15000.0,
                accountId = accountId,
                remarks = "",
                frequency = "MONTHLY",
                interval = 1,
                anchorDay = 1,
                startDate = "2026-09-01",
                nextDate = "2026-10-01",
                createdAt = "2026-09-01"
            )
        )

        expenseDao.update(kept.copy(recurringRuleId = ruleId))
        assertEquals(ruleId, expenseDao.getById(kept.id)!!.recurringRuleId)

        // A rule's account is nulled, not the rule itself, when the account it points at is deleted.
        db.accountDao().delete(db.accountDao().getAllAccountsSnapshot().single())
        assertNull(ruleDao.getById(ruleId)!!.accountId)

        db.close()
    }

    private companion object {
        const val DB_NAME = "sheetsync.db"

        val VERSION_20_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `account_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupName` TEXT NOT NULL, `accountName` TEXT NOT NULL, `initialBalance` REAL NOT NULL, `initialBalanceDate` TEXT NOT NULL, `isHidden` INTEGER NOT NULL, `displayOrder` INTEGER NOT NULL, `description` TEXT, `includeInTotals` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `expense_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `amount` REAL NOT NULL, `accountId` INTEGER, `remarks` TEXT NOT NULL, `fromAccountId` INTEGER, `toAccountId` INTEGER, `accountName` TEXT, `fromAccountName` TEXT, `toAccountName` TEXT, `isBookmarked` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, `remoteTimestamp` TEXT, `syncAction` TEXT NOT NULL, `localVersion` INTEGER NOT NULL DEFAULT 0, `syncId` TEXT, `syncedRevision` TEXT, `sheetConflictJson` TEXT, FOREIGN KEY(`accountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`fromAccountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`toAccountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_expense_records_accountId` ON `expense_records` (`accountId`)",
            "CREATE INDEX IF NOT EXISTS `index_expense_records_fromAccountId` ON `expense_records` (`fromAccountId`)",
            "CREATE INDEX IF NOT EXISTS `index_expense_records_toAccountId` ON `expense_records` (`toAccountId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_records_syncId` ON `expense_records` (`syncId`)",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `monthYear` TEXT NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_monthYear_category` ON `budgets` (`monthYear`, `category`)",
            "CREATE TABLE IF NOT EXISTS `dropdown_options` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `optionType` TEXT NOT NULL, `name` TEXT NOT NULL, `displayOrder` INTEGER NOT NULL, `role` TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE IF NOT EXISTS `budget_cycles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, `spendableAmount` REAL NOT NULL, `closedAt` TEXT)",
            "CREATE TABLE IF NOT EXISTS `budget_buckets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycleId` INTEGER NOT NULL, `name` TEXT NOT NULL, `note` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `emoji` TEXT NOT NULL, `allocatedAmount` REAL NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`cycleId`) REFERENCES `budget_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_budget_buckets_cycleId` ON `budget_buckets` (`cycleId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_buckets_id_cycleId` ON `budget_buckets` (`id`, `cycleId`)",
            "CREATE TABLE IF NOT EXISTS `bucket_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycleId` INTEGER NOT NULL, `bucketId` INTEGER NOT NULL, `category` TEXT NOT NULL COLLATE NOCASE, FOREIGN KEY(`bucketId`, `cycleId`) REFERENCES `budget_buckets`(`id`, `cycleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_bucket_categories_cycleId_category` ON `bucket_categories` (`cycleId`, `category`)",
            "CREATE INDEX IF NOT EXISTS `index_bucket_categories_bucketId_cycleId` ON `bucket_categories` (`bucketId`, `cycleId`)"
        )
    }
}
