package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a real version-16 database through every migration to the current version and checks
 * that Stats roles (ADR-0004) get their defaults once, without touching anything else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DropdownRoleMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createOldDatabase() {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) = VERSION_16_SCHEMA.forEach(db::execSQL)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        listOf(
            "EXPENSE_CATEGORY" to "Investments/Savings",
            "EXPENSE_CATEGORY" to "Food & Snacks",
            "INCOME_CATEGORY" to " return ",
            "INCOME_CATEGORY" to "Salary",
            "ACCOUNT_GROUP" to "Savings",
            "ACCOUNT_GROUP" to "Investments",
            "ACCOUNT_GROUP" to "Accounts",
            "EXPENSE_CATEGORY" to "Savings" // same name as the group, but the wrong type for SAVINGS
        ).forEachIndexed { i, (type, name) ->
            db.execSQL("INSERT INTO dropdown_options (optionType, name, displayOrder) VALUES (?, ?, ?)", arrayOf<Any>(type, name, i))
        }
        helper.close()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `upgrading gives the default roles by type and name and nothing else`() = runBlocking {
        val db = DatabaseModule.provideDatabase(context)
        val roles = db.dropdownOptionDao().getAllOptionsSnapshot().associate { (it.optionType to it.name.trim()) to it.role }

        assertEquals(8, roles.size) // no data lost, nothing reseeded
        assertEquals(DropdownRole.SAVING, roles["EXPENSE_CATEGORY" to "Investments/Savings"])
        assertEquals(DropdownRole.REFUND, roles["INCOME_CATEGORY" to "return"])
        assertEquals(DropdownRole.SAVINGS, roles["ACCOUNT_GROUP" to "Savings"])
        assertEquals(DropdownRole.SAVINGS, roles["ACCOUNT_GROUP" to "Investments"])
        assertEquals("", roles["EXPENSE_CATEGORY" to "Food & Snacks"])
        assertEquals("", roles["INCOME_CATEGORY" to "Salary"])
        assertEquals("", roles["ACCOUNT_GROUP" to "Accounts"])
        assertEquals("", roles["EXPENSE_CATEGORY" to "Savings"])
        db.close()
    }

    /**
     * A development build numbered the roles migration 18 -> 19 before sync phase 2 took that number,
     * so a phone that ran it sits at "version 19" with roles but none of the sync columns.
     */
    @Test
    fun aRolesOnlyVersion19FromADevelopmentBuildStillUpgrades() = runBlocking {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        VERSION_16_SCHEMA.forEach(db::execSQL)
                        com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration.MIGRATION_16_17.migrate(db)
                        DatabaseModule.MIGRATION_17_18.migrate(db)
                        db.execSQL("ALTER TABLE dropdown_options ADD COLUMN role TEXT NOT NULL DEFAULT ''")
                        db.execSQL("INSERT INTO dropdown_options (optionType, name, displayOrder, role) VALUES ('EXPENSE_CATEGORY', 'Investments/Savings', 0, 'SAVING')")
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

        val db = DatabaseModule.provideDatabase(context)
        assertEquals("Kept through the upgrade", db.expenseDao().getAllRecordsSnapshot().single().description)
        assertEquals(DropdownRole.SAVING, db.dropdownOptionDao().getAllOptionsSnapshot().single().role)
        val columns = db.openHelper.readableDatabase.query("PRAGMA table_info(expense_records)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toSet()
        }
        assertEquals(setOf("syncId", "syncedRevision", "sheetConflictJson"), columns.intersect(setOf("syncId", "syncedRevision", "sheetConflictJson")))
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
