package com.issaczerubbabel.ledgar.capture

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A Not-a-payment alert is dropped: no Captured transaction and no Unparsed alert (#177). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureNotAPaymentTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var ingestor: CaptureIngestor

    private val settings = object : CaptureSettingsSource {
        override suspend fun isEnabled() = true
        override suspend fun categoryMapping() = emptyMap<String, String>()
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val pipeline = CategorizationPipeline(KeywordDictionary(emptyMap())) { db.merchantRuleDao().get(it) }
        ingestor = CaptureIngestor(
            db, db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()), pipeline, settings,
            db.unparsedAlertDao(), RoomCaptureHistorySource(db.expenseDao(), db.accountAliasDao()),
            LedgerMatchFinder(db.expenseDao(), db.captureDao())
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aPaymentRequestFromABankSenderLeavesNoCaptureAndNoUnparsedAlert() = runBlocking {
        val result = ingestor.ingest(
            CaptureSource.NOTIFICATION,
            "AX-AXISBK-S",
            "<NAME> has requested money from you on Google Pay. On approving the request, INR 500.00 will be " +
                "debited from your A/c - Axis Bank"
        )

        assertTrue(result is IngestResult.Ignored)
        assertTrue(db.captureDao().getPendingSnapshot().isEmpty())
        assertTrue(db.unparsedAlertDao().getAllSnapshot().isEmpty())
    }

    @Test
    fun aGooglePayOfferLeavesNoUnparsedAlert() = runBlocking {
        ingestor.ingest(CaptureSource.NOTIFICATION, "Google Pay", "Up to ₹2,500 in welcome rewards. Open a savings account")

        assertTrue(db.captureDao().getPendingSnapshot().isEmpty())
        assertTrue(db.unparsedAlertDao().getAllSnapshot().isEmpty())
    }

    @Test
    fun anAlertNoParserCanReadIsStillKeptAsUnparsed() = runBlocking {
        val result = ingestor.ingest(CaptureSource.NOTIFICATION, "AX-HDFCBK-S", "Your HDFC Bank statement for Rs.0 is ready")

        assertEquals(IngestResult.Unparsed, result)
        assertEquals(1, db.unparsedAlertDao().getAllSnapshot().size)
    }
}
