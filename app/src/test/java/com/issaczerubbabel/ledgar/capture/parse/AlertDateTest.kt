package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AlertDateTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun millis(dateTime: LocalDateTime) = dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun readsEveryDateFormatSeenInRealAlerts() {
        // HDFC UPI send and ATM withdrawal: dd/MM/yy
        assertEquals(LocalDate.of(2026, 9, 26), ParsingUtils.dateOf("From HDFC Bank A/C *1234 To Mrs Jane Doe On 26/09/26 Ref 66353123456788"))
        assertEquals(LocalDate.of(2026, 9, 26), ParsingUtils.dateOf("UPI RRN:663586931247 on 26/09/26 Not you?"))
        // HDFC deposit: dd-MMM-yy
        assertEquals(LocalDate.of(2026, 9, 25), ParsingUtils.dateOf("deposited in HDFC Bank A/c XX1234 on 25-SEP-26 for WFISPL CREDIT"))
        // City Union Bank: dd-MM-yyyy
        assertEquals(LocalDate.of(2026, 9, 14), ParsingUtils.dateOf("is credited for Rs.35.00 on 14-09-2026 and debited from"))
        // Axis: dd-MM-yy, with a time after it
        assertEquals(LocalDate.of(2024, 12, 19), ParsingUtils.dateOf("debited from your A/c no. XX1236 on 19-12-24 at 10:19:54 IST via UPI"))
        assertEquals(LocalDate.of(2026, 3, 28), ParsingUtils.dateOf("credited to your Axis Bank A/c XX3113 on 28-03-26 by IMPS Ref 608721234567"))
    }

    @Test
    fun noDateMeansNull() {
        assertNull(ParsingUtils.dateOf("Paid Rs.500 to Ramesh Kumar. Rs.500.00 debited from Bank Account (XXXX1234)."))
    }

    @Test
    fun anImpossibleDateIsIgnoredRatherThanGuessed() {
        assertNull(ParsingUtils.dateOf("debited on 31/02/26"))
        assertNull(ParsingUtils.dateOf("debited on 12-XYZ-26"))
        assertNull(ParsingUtils.dateOf("debited on 45/01/26"))
    }

    @Test
    fun aPastAlertDateIsUsedWithTheArrivalTimeOfDay() {
        val arrived = millis(LocalDateTime.of(2026, 9, 30, 23, 39))

        val resolved = TxnTime.resolve(LocalDate.of(2026, 9, 26), arrived, zone)

        assertEquals(millis(LocalDateTime.of(2026, 9, 26, 23, 39)), resolved)
    }

    @Test
    fun todaysDateKeepsTheExactArrivalTime() {
        val arrived = millis(LocalDateTime.of(2026, 9, 30, 23, 39, 12))

        assertEquals(arrived, TxnTime.resolve(LocalDate.of(2026, 9, 30), arrived, zone))
    }

    @Test
    fun noDateFallsBackToArrival() {
        val arrived = millis(LocalDateTime.of(2026, 9, 30, 9, 0))

        assertEquals(arrived, TxnTime.resolve(null, arrived, zone))
    }

    @Test
    fun aDateMoreThanADayInTheFutureIsTreatedAsAMisreadAndIgnored() {
        val arrived = millis(LocalDateTime.of(2026, 9, 30, 9, 0))

        assertEquals(arrived, TxnTime.resolve(LocalDate.of(2026, 10, 15), arrived, zone))
        // Tomorrow is allowed: the phone and the bank can disagree about midnight.
        assertEquals(millis(LocalDateTime.of(2026, 10, 1, 9, 0)), TxnTime.resolve(LocalDate.of(2026, 10, 1), arrived, zone))
    }

    @Test
    fun theRegistryAttachesTheDateToParsedAlerts() {
        val registry = ParserRegistry.default()

        assertEquals(
            LocalDate.of(2026, 9, 26),
            registry.parse("HDFC Bank", "Sent Rs.70.00 From HDFC Bank A/C *1234 To Mrs Jane Doe On 26/09/26 Ref 66353123456788")!!.txnDate
        )
        assertEquals(
            LocalDate.of(2024, 12, 19),
            registry.parse("AXISBK", "INR 100.00 was debited from your A/c no. XX1236 on 19-12-24 at 10:19:54 IST via UPI/P2M/435476373861/Shri Ujagar Fuels. Avail. Bal: INR 5,420.10.")!!.txnDate
        )
        assertNull(registry.parse("Google Pay", "Paid Rs.500 to Ramesh Kumar. Rs.500.00 debited from Bank Account (XXXX1234).")!!.txnDate)
    }
}
