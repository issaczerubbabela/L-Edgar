package com.issaczerubbabel.ledgar.data.local

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
 * Opens a real version-28 database through the app's own builder and lets it migrate to 29, which
 * gives every capture its Alerts (ADR-0012). A broken migration would not crash:
 * `fallbackToDestructiveMigration` would silently erase local data. The device test
 * `CaptureAlertsMigrationInstrumentedTest` runs the same upgrade on a phone's SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureAlertsMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createVersion28Database() {
        context.deleteDatabase(CaptureMigrationTest.DB_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(CaptureMigrationTest.DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(28) {
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
                            DatabaseModule.MIGRATION_26_27,
                            DatabaseModule.MIGRATION_27_28
                        ).forEach { it.migrate(db) }
                        db.execSQL(
                            "INSERT INTO captured_transactions (sources, sender, rawText, rawHash, capturedAt, txnTime, amount, direction, channel, confidence, status) VALUES " +
                                "('NOTIF', 'HDFC Bank', 'Sent Rs.70.00 From HDFC Bank A/C *1234 To SHOP On 26/09/26 Ref 111111111111', 'hash-1', 100, 100, 70.0, 'DEBIT', 'UPI', 0.0, 'PENDING'), " +
                                "('NOTIF', 'Google Pay', 'A B KUMAR paid you ₹1.00\nTap to view.', 'hash-2', 200, 200, 1.0, 'CREDIT', 'UPI', 0.0, 'CONFIRMED')"
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
        context.deleteDatabase(CaptureMigrationTest.DB_NAME)
    }

    @Test
    fun everyCaptureKeepsItsOneAlert() = runBlocking {
        val db = DatabaseModule.provideDatabase(context)
        val dao = db.captureDao()
        val waiting = dao.getPendingSnapshot().single()

        val alert = dao.getAlerts(waiting.id).single()
        assertEquals("HDFC Bank", alert.sender)
        assertEquals("NOTIF", alert.source)
        assertEquals(waiting.rawText, alert.rawText)
        assertEquals("hash-1", alert.rawHash)
        assertEquals(100L, alert.capturedAt)

        val confirmed = dao.getAlertsFor(listOf(waiting.id + 1)).single()
        assertEquals("Google Pay", confirmed.sender)
        assertEquals("A B KUMAR paid you ₹1.00\nTap to view.", confirmed.rawText)
        // A repeat of an old Alert is still recognised, and deleting a capture takes its Alerts with it.
        assertTrue(dao.hasAlert("hash-2"))
        dao.deleteAllPending()
        assertTrue(dao.getAlerts(waiting.id).isEmpty())
        db.close()
    }
}
