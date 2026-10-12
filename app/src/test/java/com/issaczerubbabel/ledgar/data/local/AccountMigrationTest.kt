package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration
import com.issaczerubbabel.ledgar.data.local.migration.CaptureMigration
import com.issaczerubbabel.ledgar.data.local.migration.TripMigration
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
 * 23 -> 24 prepares the Accounts revamp: Account groups that today's keywords treat as liabilities
 * get the Liability role, Accounts get an empty "reconciled on" date, and old Transfers that only
 * stored the destination's name are linked to that Account when exactly one matches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AccountMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createVersion23Database() {
        context.deleteDatabase(CaptureMigrationTest.DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(CaptureMigrationTest.DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        CaptureMigrationTest.VERSION_16_SCHEMA.forEach(db::execSQL)
                        BucketBudgetMigration.MIGRATION_16_17.migrate(db)
                        DatabaseModule.MIGRATION_17_18.migrate(db)
                        DatabaseModule.MIGRATION_18_19.migrate(db)
                        DatabaseModule.MIGRATION_19_20.migrate(db)
                        CaptureMigration.createTables(db)
                        TripMigration.createTables(db)
                        TripMigration.upgradeTo23(db)
                        insertVersion23Data(db)
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
        context.deleteDatabase(CaptureMigrationTest.DB_NAME)
    }

    private fun insertVersion23Data(db: SupportSQLiteDatabase) {
        listOf(
            "Credit Card" to "",
            "Home loan" to "",
            "Overdrafts" to "",
            "Money owed" to "",
            "Card loan" to DropdownRole.SAVINGS, // the user's own choice stays
            "Accounts" to "",
            "Savings" to DropdownRole.SAVINGS
        ).forEachIndexed { i, (name, role) ->
            db.execSQL(
                "INSERT INTO dropdown_options (optionType, name, displayOrder, role) VALUES ('ACCOUNT_GROUP', ?, ?, ?)",
                arrayOf<Any>(name, i, role)
            )
        }
        db.execSQL("INSERT INTO dropdown_options (optionType, name, displayOrder, role) VALUES ('EXPENSE_CATEGORY', 'Loan repayment', 0, '')")

        listOf(1 to "SBI Salary", 2 to "HDFC Millennia", 3 to "Wallet", 4 to "wallet ").forEach { (id, name) ->
            db.execSQL(
                "INSERT INTO account_records (id, groupName, accountName, initialBalance, initialBalanceDate, isHidden, displayOrder, description, includeInTotals) " +
                    "VALUES ($id, 'Accounts', ?, 0, '2026-01-01', 0, $id, NULL, 1)",
                arrayOf<Any>(name)
            )
        }

        // id, toAccountId, toAccountName
        listOf(
            Triple(1, "NULL", "'  hdfc MILLENNIA '"), // one match: linked
            Triple(2, "NULL", "'Wallet'"), // two Accounts match: left alone
            Triple(3, "NULL", "'Closed account'"), // no match: left alone
            Triple(4, "1", "'HDFC Millennia'") // already linked: left alone
        ).forEach { (id, toId, toName) ->
            db.execSQL(
                "INSERT INTO expense_records (id, date, type, category, description, amount, fromAccountId, toAccountId, toAccountName, remarks, isBookmarked, isSynced, remoteTimestamp, syncAction, syncId) " +
                    "VALUES ($id, '2026-09-01', 'Transfer', '', 'Transfer $id', 100, 1, $toId, $toName, '', 0, 1, NULL, 'NONE', 'sync-$id')"
            )
        }
        db.execSQL(
            "INSERT INTO expense_records (id, date, type, category, description, amount, accountId, toAccountName, remarks, isBookmarked, isSynced, remoteTimestamp, syncAction, syncId) " +
                "VALUES (5, '2026-09-01', 'Expense', 'Food', 'Not a Transfer', 100, 1, 'HDFC Millennia', '', 0, 1, NULL, 'NONE', 'sync-5')"
        )
    }

    @Test
    fun accountGroupsNamedLikeLiabilitiesGetTheRoleWithoutOverridingTheUsersOwn() = runBlocking {
        val db = DatabaseModule.provideDatabase(context)
        val roles = db.dropdownOptionDao().getAllOptionsSnapshot().associate { (it.optionType to it.name) to it.role }

        assertEquals(DropdownRole.LIABILITY, roles["ACCOUNT_GROUP" to "Credit Card"])
        assertEquals(DropdownRole.LIABILITY, roles["ACCOUNT_GROUP" to "Home loan"])
        assertEquals(DropdownRole.LIABILITY, roles["ACCOUNT_GROUP" to "Overdrafts"])
        assertEquals(DropdownRole.LIABILITY, roles["ACCOUNT_GROUP" to "Money owed"])
        assertEquals(DropdownRole.SAVINGS, roles["ACCOUNT_GROUP" to "Card loan"])
        assertEquals("", roles["ACCOUNT_GROUP" to "Accounts"])
        assertEquals(DropdownRole.SAVINGS, roles["ACCOUNT_GROUP" to "Savings"])
        assertEquals("", roles["EXPENSE_CATEGORY" to "Loan repayment"])
        db.close()
    }

    @Test
    fun accountsAreNotReconciledYet() = runBlocking {
        val db = DatabaseModule.provideDatabase(context)

        db.accountDao().getAllAccountsSnapshot().forEach { assertNull(it.reconciledAt) }
        db.close()
    }

    @Test
    fun transfersAreLinkedToTheirDestinationOnlyWhenExactlyOneAccountHasThatName() = runBlocking {
        val db = DatabaseModule.provideDatabase(context)
        val toAccount = db.expenseDao().getAllRecordsSnapshot().associate { it.id to it.toAccountId }

        assertEquals(mapOf(1L to 2L, 2L to null, 3L to null, 4L to 1L, 5L to null), toAccount)
        db.close()
    }
}
