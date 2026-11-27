package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration
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
 * Opens a real version-27 database through the app's own builder and lets it migrate to 28, which
 * adds the Fund tables. A broken migration would not crash: `fallbackToDestructiveMigration` would
 * silently erase local data, unsynced changes included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class FundMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createVersion27Database() {
        context.deleteDatabase(DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        CaptureMigrationTest.VERSION_16_SCHEMA.forEach(db::execSQL)
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
                            DatabaseModule.MIGRATION_26_27
                        ).forEach { it.migrate(db) }
                        db.execSQL(
                            "INSERT INTO account_records (groupName, accountName, initialBalance, initialBalanceDate, isHidden, displayOrder, includeInTotals) " +
                                "VALUES ('Accounts', 'HDFC', 100000, '2026-08-31', 0, 0, 1), ('Savings', 'Emergency Fund RD', 30000, '2026-08-31', 0, 1, 1)"
                        )
                        db.execSQL(
                            "INSERT INTO expense_records (date, type, category, description, amount, accountId, remarks, isBookmarked, isSynced, remoteTimestamp, syncAction, localVersion) " +
                                "VALUES ('2026-09-01', 'Expense', 'Food', 'Unsynced lunch', 42.5, 1, '', 0, 0, NULL, 'INSERT', 0)"
                        )
                        db.execSQL("INSERT INTO budget_cycles (startDate, endDate, spendableAmount, closedAt) VALUES ('2026-09-26', '2026-10-25', 115842, NULL)")
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
    fun upgradingFromVersion27KeepsEverythingAndAddsTheFundTables() = runBlocking {
        val db = DatabaseModule.provideDatabase(context)

        assertEquals("Unsynced lunch", db.expenseDao().getAllRecordsSnapshot().single().description)
        assertEquals(listOf("HDFC", "Emergency Fund RD"), db.accountDao().getAllAccountsSnapshot().map { it.accountName })
        val cycle = db.bucketBudgetDao().getAllCycles().single()

        val dao = db.fundDao()
        assertTrue(dao.getAllFunds().isEmpty())
        val fundId = dao.insertFund(Fund(name = "Emergency", createdDate = "2026-09-01"))
        val rd = db.accountDao().getAllAccountsSnapshot().single { it.accountName == "Emergency Fund RD" }
        dao.insertHolding(FundHolding(fundId = fundId, accountId = rd.id, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01"))
        dao.upsertPlan(FundPlan(fundId, cycle.id, 15000.0))
        assertEquals(1, dao.getAllHoldings().size)
        assertEquals(listOf(15000.0), dao.getAllPlans().map { it.plannedAmount })

        // Deleting the Account takes its holding, and deleting the cycle its plans; the Fund stays.
        db.accountDao().delete(rd)
        db.bucketBudgetDao().deleteAllCycles()
        assertTrue(dao.getAllHoldings().isEmpty())
        assertTrue(dao.getAllPlans().isEmpty())
        assertEquals(listOf("Emergency"), dao.getAllFunds().map { it.name })
        db.close()
    }

    private companion object {
        const val DB_NAME = "sheetsync.db"
    }
}
