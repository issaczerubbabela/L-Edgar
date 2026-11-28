package com.issaczerubbabel.ledgar.capture

import com.issaczerubbabel.ledgar.capture.parse.Channel
import com.issaczerubbabel.ledgar.capture.parse.Direction
import com.issaczerubbabel.ledgar.capture.parse.ParsedTxn
import com.issaczerubbabel.ledgar.capture.source.AlertSource
import com.issaczerubbabel.ledgar.capture.source.AlertSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertMergeTest {

    private val hdfcSms = AlertSource(AlertSourceKind.SMS, "HDFC")
    private val hdfcEmail = AlertSource(AlertSourceKind.EMAIL, "HDFC")
    private val googlePay = AlertSource(AlertSourceKind.UPI_APP, "Google Pay")

    private fun read(
        source: AlertSource,
        at: Long,
        merchant: String? = null,
        account: String? = null,
        ref: String? = null,
        channel: Channel = Channel.UPI,
        txnTime: Long = at
    ) = ReadAlert(
        source = source,
        sender = source.name,
        parsed = ParsedTxn(1.0, Direction.CREDIT, merchant, account, ref, channel),
        arrivedAt = at,
        txnTime = txnTime
    )

    @Test
    fun oneAlertIsItsOwnFacts() {
        val sms = read(hdfcSms, at = 10, merchant = "SHOP", account = "1234", ref = "111")

        val facts = AlertMerge.facts(listOf(sms))

        assertEquals(sms.parsed, facts.parsed)
        assertNull(facts.bankMerchantRaw)
        assertEquals(10L, facts.txnTime)
        assertEquals("HDFC", facts.bankSender)
    }

    @Test
    fun theUpiAppNamesThePayerAndOnlyTheBankNamesTheAccount() {
        val facts = AlertMerge.facts(
            listOf(
                read(googlePay, at = 10, merchant = "A B KUMAR"),
                read(hdfcSms, at = 12, merchant = "VPA *00-0@okaxis", account = "1234")
            )
        )

        assertEquals("A B KUMAR", facts.parsed.merchantRaw)
        assertEquals("1234", facts.parsed.accountHint)
        assertEquals("VPA *00-0@okaxis", facts.bankMerchantRaw)
        assertEquals("HDFC", facts.bankSender)
    }

    @Test
    fun thePayeeComesFromTheUpiAppThenEmailThenSms() {
        val sms = read(hdfcSms, at = 10, merchant = "BANK TEXT", account = "1234")
        val email = read(hdfcEmail, at = 11, merchant = "EMAIL TEXT", account = "1234")
        val app = read(googlePay, at = 12, merchant = "APP TEXT")

        assertEquals("EMAIL TEXT", AlertMerge.facts(listOf(sms, email)).parsed.merchantRaw)
        assertEquals("APP TEXT", AlertMerge.facts(listOf(sms, email, app)).parsed.merchantRaw)
        // A source that names nobody gives way to the next.
        assertEquals("BANK TEXT", AlertMerge.facts(listOf(sms, read(googlePay, at = 12))).parsed.merchantRaw)
    }

    @Test
    fun theDateIsTheEarliestAlertsAndTheReferenceAnyAlertsThatHasOne() {
        val facts = AlertMerge.facts(
            listOf(
                read(hdfcSms, at = 20, account = "1234", ref = "111", txnTime = 5),
                read(googlePay, at = 10, merchant = "A B KUMAR", txnTime = 10)
            )
        )

        assertEquals(10L, facts.txnTime)
        assertEquals("111", facts.parsed.refNumber)
    }

    @Test
    fun theChannelIsTheBanksWhenItKnowsOne() {
        val facts = AlertMerge.facts(
            listOf(read(googlePay, at = 10, channel = Channel.UPI), read(hdfcSms, at = 11, channel = Channel.UNKNOWN))
        )
        assertEquals(Channel.UPI, facts.parsed.channel)

        assertEquals(
            Channel.IMPS,
            AlertMerge.facts(listOf(read(googlePay, at = 10, channel = Channel.UPI), read(hdfcSms, at = 11, channel = Channel.IMPS))).parsed.channel
        )
    }

    @Test
    fun withoutABankAlertTheUpiAppsFactsStand() {
        val facts = AlertMerge.facts(listOf(read(googlePay, at = 10, merchant = "A B KUMAR")))

        assertNull(facts.parsed.accountHint)
        assertNull(facts.bankSender)
        assertEquals("A B KUMAR", facts.parsed.merchantRaw)
    }

    @Test
    fun theBanksOwnAppGuessesTheAccountEvenAfterItsSms() {
        val app = AlertSource(AlertSourceKind.BANK_APP, "HDFC Bank")
        val facts = AlertMerge.facts(listOf(read(hdfcSms, at = 10, account = "9999"), read(app, at = 11, account = "9999")))

        assertEquals("HDFC Bank", facts.bankSender)
    }
}
