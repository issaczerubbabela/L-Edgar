package com.issaczerubbabel.ledgar.capture.categorize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNormalizerTest {

    @Test
    fun stripsVpaHandleFromDomain() {
        val result = MerchantNormalizer.normalize("swiggy.instamart@icici")
        assertEquals("SWIGGY INSTAMART", result.norm)
        assertFalse(result.isP2p)
    }

    @Test
    fun flagsPhoneNumberVpaAsP2p() {
        val result = MerchantNormalizer.normalize("9876543210@ybl")
        assertEquals("P2P:9876543210", result.norm)
        assertTrue(result.isP2p)
    }

    @Test
    fun dropsCorporateStopWords() {
        val result = MerchantNormalizer.normalize("AMAZON PAY INDIA PVT LTD")
        assertEquals("AMAZON PAY", result.norm)
    }

    @Test
    fun dropsStoreNumbersAndPunctuation() {
        val result = MerchantNormalizer.normalize("ZOMATO*ORDER 88213")
        assertEquals("ZOMATO ORDER", result.norm)
    }

    @Test
    fun handlesPersonNamesUnchangedBesidesCase() {
        val result = MerchantNormalizer.normalize("Mrs Jane Doe")
        assertEquals("MRS JANE DOE", result.norm)
        assertFalse(result.isP2p)
    }

    @Test
    fun blankInputNormalizesToEmpty() {
        val result = MerchantNormalizer.normalize(null)
        assertEquals("", result.norm)
    }
}
