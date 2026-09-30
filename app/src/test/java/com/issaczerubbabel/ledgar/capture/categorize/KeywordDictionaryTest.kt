package com.issaczerubbabel.ledgar.capture.categorize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class KeywordDictionaryTest {

    private val shipped = KeywordDictionary.fromJson(File("src/main/assets/merchant_keywords.json").readText())

    @Test
    fun longestKeywordWins() {
        // Both "swiggy" (food_delivery) and "instamart" (groceries) are present.
        assertEquals("groceries", shipped.match("SWIGGY INSTAMART", "swiggy.instamart@icici")?.canonical)
    }

    @Test
    fun matchesWholeWordsOnly() {
        assertNull(shipped.match("STEAM GAMES", null))
        assertEquals("snacks", shipped.match("CHAI POINT", null)?.canonical)
    }

    @Test
    fun findsKeywordsThatNormalizationDropped() {
        // The normalizer strips digits, so "1mg" only survives in the raw text.
        assertEquals("health", shipped.match("MG", "1mg")?.canonical)
    }

    @Test
    fun recognisesFuelStationsFromARealAlert() {
        assertEquals("fuel", shipped.match("SHRI UJAGAR FUELS", "Shri Ujagar Fuels")?.canonical)
    }

    @Test
    fun recognisesAtmWithdrawals() {
        assertEquals("cash", shipped.match("ATM WITHDRAWAL", "ATM Withdrawal")?.canonical)
    }

    @Test
    fun noMatchForAPersonsName() {
        assertNull(shipped.match("MRS JANE DOE", "Mrs Jane Doe"))
    }
}
