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

    @Test
    fun unknownBankFallsBackToTheGenericParser() {
        val parsed = registry.parse("XYZBNK", "Rs.250.00 debited from your a/c XX5555 at BIG BAZAAR on 01-01-26.")!!

        assertEquals(250.0, parsed.amount, 0.001)
        assertEquals("5555", parsed.accountHint)
    }

    @Test
    fun ignoresOtpAndTextWithoutAnAmount() {
        assertNull(registry.parse("HDFC Bank", "123456 is your OTP for txn of Rs.2,500 at FLIPKART."))
        assertNull(registry.parse("Anyone", "Your statement is ready to view."))
    }
}
