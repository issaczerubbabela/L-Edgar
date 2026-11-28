package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.capture.source.AlertSource
import com.issaczerubbabel.ledgar.capture.source.AlertSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamePaymentMatcherTest {

    private val second = 1_000L
    private val minute = 60 * second
    private val hdfcSms = AlertSource(AlertSourceKind.SMS, "HDFC")
    private val hdfcApp = AlertSource(AlertSourceKind.BANK_APP, "HDFC Bank")
    private val googlePay = AlertSource(AlertSourceKind.UPI_APP, "Google Pay")

    private fun alert(
        source: AlertSource,
        at: Long = T0,
        amount: Double = 1.0,
        direction: String = "CREDIT",
        ref: String? = null
    ) = AlertFacts(source, amount, direction, ref, at)

    private fun capture(
        id: Long,
        vararg alerts: AlertFacts,
        status: String = "PENDING",
        keptApartFrom: Set<AlertSource> = emptySet()
    ) = MergeCandidate(
        captureId = id,
        status = status,
        amount = alerts.first().amount,
        direction = alerts.first().direction,
        refNumber = alerts.firstNotNullOfOrNull { it.refNumber },
        alerts = alerts.map { AlertSeen(it.source, it.arrivedAt) },
        keptApartFrom = keptApartFrom
    )

    @Test
    fun aBankSmsAndGooglePayTwoSecondsApartAreOnePayment() {
        val sms = capture(1, alert(hdfcSms))

        assertEquals(sms, SamePaymentMatcher.pick(alert(googlePay, at = T0 + 2 * second), listOf(sms)))
    }

    @Test
    fun withoutAReferenceTheWindowIsTenMinutes() {
        val sms = capture(1, alert(hdfcSms))

        assertEquals(sms, SamePaymentMatcher.pick(alert(googlePay, at = T0 + 10 * minute), listOf(sms)))
        assertNull(SamePaymentMatcher.pick(alert(googlePay, at = T0 + 11 * minute), listOf(sms)))
        assertNull(SamePaymentMatcher.pick(alert(googlePay, at = T0 - 11 * minute), listOf(sms)))
    }

    @Test
    fun withoutAReferenceTheAmountMustAgreeToThePaisaAndTheDirectionToo() {
        val sms = capture(1, alert(hdfcSms, amount = 250.0))

        assertNull(SamePaymentMatcher.pick(alert(googlePay, amount = 250.01), listOf(sms)))
        assertEquals(sms, SamePaymentMatcher.pick(alert(googlePay, amount = 250.004), listOf(sms)))
        assertNull(SamePaymentMatcher.pick(alert(googlePay, amount = 250.0, direction = "DEBIT"), listOf(sms)))
    }

    @Test
    fun theSameReferenceMatchesEvenAnHourApart() {
        val sms = capture(1, alert(hdfcSms, ref = "111111111111"))

        assertEquals(sms, SamePaymentMatcher.pick(alert(hdfcApp, at = T0 + 60 * minute, ref = "111111111111"), listOf(sms)))
    }

    @Test
    fun differentReferencesNeverMatchHoweverCloseTheRest() {
        val sms = capture(1, alert(hdfcSms, ref = "111111111111"))

        assertNull(SamePaymentMatcher.pick(alert(hdfcApp, at = T0 + second, ref = "222222222222"), listOf(sms)))
    }

    @Test
    fun aReferenceOnOneSideOnlyFallsBackToAmountAndTime() {
        val sms = capture(1, alert(hdfcSms, ref = "111111111111"))

        assertEquals(sms, SamePaymentMatcher.pick(alert(googlePay, at = T0 + second), listOf(sms)))
        assertNull(SamePaymentMatcher.pick(alert(googlePay, at = T0 + 20 * minute), listOf(sms)))
    }

    @Test
    fun theSameReferenceInTheOtherDirectionIsAReversalNotARepeat() {
        val debit = capture(1, alert(hdfcSms, direction = "DEBIT", ref = "111111111111"))

        assertNull(SamePaymentMatcher.pick(alert(hdfcApp, direction = "CREDIT", ref = "111111111111"), listOf(debit)))
    }

    @Test
    fun twoAlertsFromTheSameSourceNeverMerge() {
        val first = capture(1, alert(googlePay))

        assertNull(SamePaymentMatcher.pick(alert(googlePay, at = T0 + second), listOf(first)))
        // Not even into a capture that already has another source's Alert besides.
        val merged = capture(2, alert(hdfcSms), alert(googlePay, at = T0 + second))
        assertNull(SamePaymentMatcher.pick(alert(googlePay, at = T0 + 2 * second), listOf(merged)))
    }

    @Test
    fun aSourceTheUserSplitOffNeverMergesBack() {
        val sms = capture(1, alert(hdfcSms), keptApartFrom = setOf(googlePay))

        assertNull(SamePaymentMatcher.pick(alert(googlePay, at = T0 + second), listOf(sms)))
        assertEquals(sms, SamePaymentMatcher.pick(alert(hdfcApp, at = T0 + second), listOf(sms)))
    }

    @Test
    fun theWindowCountsFromAnyOfTheCapturesAlerts() {
        val merged = capture(1, alert(hdfcSms), alert(googlePay, at = T0 + 9 * minute))

        assertEquals(merged, SamePaymentMatcher.pick(alert(hdfcApp, at = T0 + 18 * minute), listOf(merged)))
    }

    @Test
    fun aMatchingReferenceWinsThenAWaitingCaptureThenTheNearest() {
        val near = capture(1, alert(hdfcSms, at = T0 + minute))
        val byRef = capture(2, alert(hdfcSms, at = T0 + 5 * minute, ref = "111111111111"))
        assertEquals(byRef, SamePaymentMatcher.pick(alert(hdfcApp, ref = "111111111111"), listOf(near, byRef)))

        val confirmed = capture(3, alert(hdfcSms, at = T0 + second), status = "CONFIRMED")
        val waiting = capture(4, alert(hdfcSms, at = T0 + 5 * minute))
        assertEquals(waiting, SamePaymentMatcher.pick(alert(googlePay), listOf(confirmed, waiting)))

        val far = capture(5, alert(hdfcSms, at = T0 + 6 * minute))
        assertEquals(waiting, SamePaymentMatcher.pick(alert(googlePay), listOf(far, waiting)))
    }

    @Test
    fun aResolvedCaptureStillMatchesSoALateAlertCanAttachQuietly() {
        val confirmed = capture(1, alert(hdfcSms), status = "CONFIRMED")

        assertEquals(confirmed, SamePaymentMatcher.pick(alert(googlePay, at = T0 + minute), listOf(confirmed)))
    }

    private companion object {
        const val T0 = 1_795_000_000_000L
    }
}
