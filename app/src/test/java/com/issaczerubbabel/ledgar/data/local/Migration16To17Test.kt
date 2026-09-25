package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens a real version-16 database through the app's own builder. A broken migration would not
 * crash: `fallbackToDestructiveMigration` would silently erase local data, unsynced changes included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class Migration16To17Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createVersion16Database() {
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
        helper.writableDatabase.execSQL(
            "INSERT INTO expense_records (date, type, category, description, amount, remarks, isBookmarked, isSynced, remoteTimestamp, syncAction) " +
                "VALUES ('2026-09-01', 'Expense', 'Food', 'Unsynced lunch', 42.5, '', 0, 0, NULL, 'INSERT')"
        )
        helper.close()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `upgrading keeps every Transaction and starts versioning from zero`() = runBlocking {
        val db = DatabaseModule.provideDatabase(context)
        val dao = db.expenseDao()

        val kept = dao.getAllRecordsSnapshot().single()
        assertEquals("Unsynced lunch", kept.description)
        assertEquals("INSERT", kept.syncAction)
        assertEquals(0L, kept.localVersion)

        dao.updateTransactionsDescriptionByIds(listOf(kept.id), "Edited after upgrade")
        assertTrue("the version trigger is installed", dao.getById(kept.id)!!.localVersion > 0)
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
