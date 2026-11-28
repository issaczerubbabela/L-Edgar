package com.issaczerubbabel.ledgar.capture.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every real sample, routed through the default parser order, so the wrong parser can't claim one. */
class ParserRegistryTest {

    private val registry = ParserRegistry.default()

    @Test
    fun routesEachBankToItsOwnParser() {
        val hdfc = registry.parse("HDFC Bank", "Sent Rs.70.00 From HDFC Bank A/C *1234 To Mrs Jane Doe On 26/09/26 Ref 66353123456788")!!
        assertEquals("Mrs Jane Doe", hdfc.merchantRaw)

        val cub = registry.parse(
            "CUB",
            "Your a/c no. XXXXXXXX1234 is credited for Rs.35.00 on 14-09-2026 and debited from a/c no. XXXXXXXX5424 (UPI Ref no 62575731739387) -CUB"
        )!!
        assertEquals(Direction.CREDIT, cub.direction)
        assertEquals("5424", cub.counterpartyAccountHint)

        val axis = registry.parse(
            "AXISBK",
            "INR 100.00 was debited from your A/c no. XX1236 on 19-12-24 at 10:19:54 IST via UPI/P2M/435476373861/Shri Ujagar Fuels. Avail. Bal: INR 5,420.10."
        )!!
        assertEquals("Shri Ujagar Fuels", axis.merchantRaw)

        val gpay = registry.parse("Google Pay", "Paid ₹500 to Ramesh Kumar. ₹500.00 debited from Bank Account (XXXX1234).")!!
        assertEquals("Ramesh Kumar", gpay.merchantRaw)
    }

    // Synthetic: "deducted" is read as a debit only in HDFC's "Amt Deducted!", never by the generic parser.
    @Test
    fun aCreditMentioningADeductionIsStillACredit() {
        val parsed = registry.parse("XYZBNK", "Salary of Rs.50,000.00 (TDS deducted) credited to your a/c XX5555 on 01-12-26")!!

        assertEquals(Direction.CREDIT, parsed.direction)
    }

    @Test
    fun unknownBankFallsBackToTheGenericParser() {
        val parsed = registry.parse("XYZBNK", "Rs.250.00 debited from your a/c XX5555 at BIG BAZAAR on 01-01-26.")!!

        assertEquals(250.0, parsed.amount, 0.001)
        assertEquals("5555", parsed.accountHint)
    }

    @Test
    fun iciciDebitAlertWordedCreditedForTheCounterpartyIsAnExpenseNotIncome() {
        val parsed = registry.parse(
            "ICICI Bank",
            "ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Sep-26; ZOMATO credited. " +
                "UPI:327145093128. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766."
        )!!

        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals(240.0, parsed.amount, 0.001)
        assertEquals("ZOMATO", parsed.merchantRaw)
    }

    @Test
    fun ignoresOtpAndTextWithoutAnAmount() {
        assertNull(registry.parse("HDFC Bank", "123456 is your OTP for txn of Rs.2,500 at FLIPKART."))
        assertNull(registry.parse("Anyone", "Your statement is ready to view."))
    }

    // Synthetic: no real VPA-addressed bank alert has been captured yet.
    @Test
    fun aVpaDebitFromAnUnknownBankNamesTheHandle() {
        val parsed = registry.parse("XYZBNK", "Rs.250.00 debited from A/c XX1234 to VPA swiggy.stores@axb on 12-11-26 Ref 1234")!!

        assertEquals(250.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("swiggy.stores@axb", parsed.merchantRaw)
    }

    @Test
    fun aNotAPaymentAlertIsReadAsNotAPaymentRatherThanUnreadable() {
        val request = "<NAME> has requested money from you on Google Pay. On approving the request, INR 500.00 will be " +
            "debited from your A/c - Axis Bank"

        assertEquals(AlertReading.NotAPayment, registry.read("AX-AXISBK-S", request))
        assertNull(registry.parse("AX-AXISBK-S", request))
        assertEquals(AlertReading.NotAPayment, registry.read("Google Pay", "Up to ₹2,500 in welcome rewards. Open a savings account"))
    }

    @Test
    fun anAlertNoParserCanReadIsUnreadable() {
        assertEquals(AlertReading.Unreadable, registry.read("AX-HDFCBK-S", "Your HDFC Bank statement for Rs.0 is ready"))
    }

    @Test
    fun aRealPaymentIsReadAsAPayment() {
        val reading = registry.read("HDFC Bank", "UPI Mandate: Sent Rs.2000.00 from HDFC Bank A/c 1234 To ACME BROKING LTD On 05/11/26 Ref 111111111111")

        val txn = (reading as AlertReading.Payment).txn
        assertEquals(2000.0, txn.amount, 0.001)
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals("ACME BROKING LTD", txn.merchantRaw)
    }
}
