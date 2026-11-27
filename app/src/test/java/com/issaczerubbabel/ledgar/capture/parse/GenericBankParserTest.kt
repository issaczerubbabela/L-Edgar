package com.issaczerubbabel.ledgar.capture.parse

import com.issaczerubbabel.ledgar.capture.categorize.MerchantNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenericBankParserTest {

    private val parser = GenericBankParser()

    @Test
    fun parsesIciciDebitAlertWordedCreditedForTheCounterparty() {
        val text = "ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Sep-26; ZOMATO credited. " +
            "UPI:327145093128. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766."

        val parsed = parser.parse(text)!!

        assertEquals(240.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("ZOMATO", parsed.merchantRaw)
    }

    @Test
    fun parsesDebitThenCreditOrderAsDebit() {
        val parsed = parser.parse("Rs.500.00 debited from a/c XX1234 and credited to a/c XX5678")!!

        assertEquals(Direction.DEBIT, parsed.direction)
    }

    @Test
    fun parsesCreditThenDebitOrderAsCredit() {
        val parsed = parser.parse("Rs.500 credited to your a/c XX1234 which was debited from a/c XX9999")!!

        assertEquals(Direction.CREDIT, parsed.direction)
    }

    @Test
    fun neverReturnsAnAllDigitMerchant() {
        val parsed = parser.parse("Rs.100 debited from your a/c XX5555 to 9876543210 on 01-01-26.")!!

        assertNull(parsed.merchantRaw)
    }

    // Synthetic: no real VPA-addressed bank alert has been captured yet.
    @Test
    fun parsesAVpaDebitWithTheHandleAsMerchant() {
        val parsed = parser.parse("Rs.250.00 debited from A/c XX1234 to VPA swiggy.stores@axb on 12-11-26 Ref 1234")!!

        assertEquals(250.0, parsed.amount, 0.001)
        assertEquals(Direction.DEBIT, parsed.direction)
        assertEquals("swiggy.stores@axb", parsed.merchantRaw)
        assertEquals("SWIGGY STORES", MerchantNormalizer.normalize(parsed.merchantRaw).norm)
    }

    // Synthetic, as above.
    @Test
    fun aPhoneNumberVpaNormalisesToP2p() {
        val parsed = parser.parse("Rs.500 debited from A/c XX1234 to VPA 9876543210@ybl on 12-11-26. SMS BLOCK 123 to 9215676766")!!

        assertEquals("9876543210@ybl", parsed.merchantRaw)
        assertEquals("P2P:9876543210", MerchantNormalizer.normalize(parsed.merchantRaw).norm)
    }
}
