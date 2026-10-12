package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.entity.TripRecord
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration
import com.issaczerubbabel.ledgar.data.local.migration.CaptureMigration
import com.issaczerubbabel.ledgar.data.local.migration.TripMigration
import com.issaczerubbabel.ledgar.data.repository.TripRepositoryImpl
import com.issaczerubbabel.ledgar.di.DatabaseModule
import com.issaczerubbabel.ledgar.trip.SplitMode
import com.issaczerubbabel.ledgar.trip.TripMath
import com.issaczerubbabel.ledgar.viewmodel.toInput
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
 * The Trip tables arrive in 21 -> 22, and 22 -> 23 gives each person a colour and turns Adjust and
 * Exact splits into Custom ones. A trip entered before the upgrade must come out with every Share,
 * Balance and Settlement exactly as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TripMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(CaptureMigrationTest.DB_NAME)
    }

    private fun createAt(version: Int, build: (SupportSQLiteDatabase) -> Unit) {
        context.deleteDatabase(CaptureMigrationTest.DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(CaptureMigrationTest.DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        CaptureMigrationTest.VERSION_16_SCHEMA.forEach(db::execSQL)
                        BucketBudgetMigration.MIGRATION_16_17.migrate(db)
                        DatabaseModule.MIGRATION_17_18.migrate(db)
                        DatabaseModule.MIGRATION_18_19.migrate(db)
                        DatabaseModule.MIGRATION_19_20.migrate(db)
                        CaptureMigration.createTables(db)
                        db.execSQL(
                            "INSERT INTO expense_records (date, type, category, description, amount, remarks, isBookmarked, isSynced, remoteTimestamp, syncAction) " +
                                "VALUES ('2026-09-01', 'Expense', 'Food', 'Kept through the upgrade', 42.5, '', 0, 0, NULL, 'INSERT')"
                        )
                        build(db)
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase
        helper.close()
    }

    @Test
    fun version21GetsTheTripTablesAndKeepsItsData() = runBlocking {
        createAt(21) { }

        val db = DatabaseModule.provideDatabase(context)
        assertEquals("Kept through the upgrade", db.expenseDao().getAllRecordsSnapshot().single().description)
        val tripId = db.tripDao().insertTrip(TripRecord(name = "Goa trip", startDate = "2026-10-10"))
        assertEquals("Goa trip", db.tripDao().observeTrips().first().single().name)
        assertEquals(tripId, db.tripDao().observeActiveTrip().first()?.id)
        db.close()
    }

    /** One share row: expense, member, the legacy input, and the Share that was stored. */
    private data class Stored(val expense: Long, val member: Long, val input: Long, val share: Long)

    // Members: 1 You, 2 Rahul, 3 Priya, 4 Arjun, in that order.
    private val stored = listOf(
        // 1: Train tickets ₹4,800, Equal between all four
        Stored(1, 1, 0, 120000), Stored(1, 2, 0, 120000), Stored(1, 3, 0, 120000), Stored(1, 4, 0, 120000),
        // 2: ₹10.02 with Rahul +₹1: locking only Rahul would move a paisa, so everyone ends up locked
        Stored(2, 1, 0, 225), Stored(2, 2, 100, 325), Stored(2, 3, 0, 226), Stored(2, 4, 0, 226),
        // 5: Scooter ₹400, Equal between You and Rahul
        Stored(5, 1, 0, 20000), Stored(5, 2, 0, 20000),
        // 7: Dinner ₹3,460 with Arjun +₹300 (Adjust)
        Stored(7, 1, 0, 79000), Stored(7, 2, 0, 79000), Stored(7, 3, 0, 79000), Stored(7, 4, 30000, 109000),
        // 8: Souvenirs ₹1,200, Exact between You and Priya
        Stored(8, 1, 40000, 40000), Stored(8, 3, 80000, 80000),
        // 9: ₹100.01 Equal between Rahul, Priya and Arjun, with a spare paisa
        Stored(9, 2, 0, 3334), Stored(9, 3, 0, 3334), Stored(9, 4, 0, 3333)
    )

    private fun insertVersion22Trip(db: SupportSQLiteDatabase) {
        TripMigration.createTables(db)
        db.execSQL("INSERT INTO trips (id, name, startDate, endDate, status, postedAt) VALUES (1, 'Goa trip', '2026-10-10', '2026-10-12', 'ACTIVE', NULL)")
        listOf(1L to "You", 2L to "Rahul", 3L to "Priya", 4L to "Arjun").forEachIndexed { i, (id, name) ->
            db.execSQL("INSERT INTO trip_members (id, tripId, name, isSelf, upiId, displayOrder) VALUES ($id, 1, '$name', ${if (id == 1L) 1 else 0}, NULL, $i)")
        }
        // id, date, purpose, amount, payer, mode
        listOf(
            listOf("1", "'2026-10-10'", "'Train tickets'", "480000", "1", "'EQUAL'"),
            listOf("2", "'2026-10-10'", "'Chai'", "1002", "2", "'ADJUST'"),
            listOf("5", "'2026-10-10'", "'Scooter'", "40000", "1", "'EQUAL'"),
            listOf("7", "'2026-10-11'", "'Dinner'", "346000", "1", "'ADJUST'"),
            listOf("8", "'2026-10-12'", "'Souvenirs'", "120000", "3", "'EXACT'"),
            listOf("9", "'2026-10-12'", "'Parasailing'", "10001", "2", "'EQUAL'")
        ).forEach { v ->
            db.execSQL(
                "INSERT INTO trip_expenses (id, tripId, date, purpose, amountPaise, payerMemberId, splitMode, category, postedExpenseId) " +
                    "VALUES (${v[0]}, 1, ${v[1]}, ${v[2]}, ${v[3]}, ${v[4]}, ${v[5]}, 'Food & Snacks', NULL)"
            )
        }
        stored.forEach { r ->
            db.execSQL("INSERT INTO trip_expense_shares (expenseId, memberId, inputPaise, sharePaise) VALUES (${r.expense}, ${r.member}, ${r.input}, ${r.share})")
        }
        db.execSQL("INSERT INTO trip_settlements (id, tripId, fromMemberId, toMemberId, amountPaise, date) VALUES (1, 1, 3, 1, 50000, '2026-10-12')")
    }

    @Test
    fun version22TripsKeepEveryShareAndBalanceAndGetColoursAndCustomSplits() = runBlocking {
        createAt(22) { insertVersion22Trip(it) }

        val db = DatabaseModule.provideDatabase(context)
        val repo = TripRepositoryImpl(db, db.tripDao(), db.expenseDao(), db.captureDao())
        val detail = repo.observeDetail(1).first()!!

        // Colours follow the order the people were added.
        assertEquals(listOf("You" to 0, "Rahul" to 1, "Priya" to 2, "Arjun" to 3), detail.members.map { it.name to it.colorIndex })

        // Every expense's Shares come out exactly as they were stored before the upgrade.
        val before = stored.groupBy { it.expense }.mapValues { (_, rows) -> rows.associate { it.member to it.share } }
        detail.expenses.forEach { e ->
            assertEquals("expense ${e.id}", before.getValue(e.id), TripMath.shares(e))
        }

        // Adjust and Exact became Custom with the right people locked; Equal stayed Equal.
        val byId = detail.expenses.associateBy { it.id }
        assertEquals(SplitMode.EQUAL, byId.getValue(1).mode)
        assertEquals(SplitMode.EQUAL, byId.getValue(5).mode)
        assertEquals(SplitMode.EQUAL, byId.getValue(9).mode)
        assertEquals(SplitMode.CUSTOM, byId.getValue(7).mode)
        assertEquals(mapOf(4L to 109000L), byId.getValue(7).locked)
        assertEquals(SplitMode.CUSTOM, byId.getValue(8).mode)
        assertEquals(mapOf(1L to 40000L, 3L to 80000L), byId.getValue(8).locked)
        assertEquals(SplitMode.CUSTOM, byId.getValue(2).mode)
        assertEquals(mapOf(1L to 225L, 2L to 325L, 3L to 226L, 4L to 226L), byId.getValue(2).locked)

        // Balances: what each person paid, minus their Shares, plus the recorded payment.
        val paid = mapOf(1L to 480000L + 40000L + 346000L, 2L to 1002L + 10001L, 3L to 120000L, 4L to 0L)
        val shareTotals = stored.groupBy { it.member }.mapValues { (_, rows) -> rows.sumOf { it.share } }
        val expected = (1L..4L).associateWith { id ->
            paid.getValue(id) - (shareTotals[id] ?: 0L) + (if (id == 3L) 50000L else 0L) - (if (id == 1L) 50000L else 0L)
        }
        val balances = TripMath.balances(detail.members, detail.expenses, detail.settlements.map { it.toInput() })
        assertEquals(expected, balances.associate { it.memberId to it.netPaise })
        assertTrue(balances.sumOf { it.netPaise } == 0L)

        // The regular ledger is untouched.
        assertEquals("Kept through the upgrade", db.expenseDao().getAllRecordsSnapshot().single().description)
        db.close()
    }
}
