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
import com.issaczerubbabel.ledgar.di.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Trip tables arrive in 21 -> 22; a version-21 database must open with its data intact. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TripMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(CaptureMigrationTest.DB_NAME)
    }

    @Test
    fun version21GetsTheTripTablesAndKeepsItsData() = runBlocking {
        context.deleteDatabase(CaptureMigrationTest.DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(CaptureMigrationTest.DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(21) {
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
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase
        helper.close()

        val db = DatabaseModule.provideDatabase(context)
        assertEquals("Kept through the upgrade", db.expenseDao().getAllRecordsSnapshot().single().description)
        val tripId = db.tripDao().insertTrip(TripRecord(name = "Goa trip", startDate = "2026-10-10"))
        assertEquals("Goa trip", db.tripDao().observeTrips().first().single().name)
        assertEquals(tripId, db.tripDao().observeActiveTrip().first()?.id)
        db.close()
    }
}
