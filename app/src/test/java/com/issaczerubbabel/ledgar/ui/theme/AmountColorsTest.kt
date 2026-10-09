package com.issaczerubbabel.ledgar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amount colours carry meaning, so each must stay readable (WCAG AA, 4.5:1) on the background it's used on. */
class AmountColorsTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private val darkBackgrounds = listOf(Color(0xFF121212), Color(0xFF1C1B1F), Color(0xFF000000))
    private val lightBackgrounds = listOf(Color(0xFFFFFFFF), Color(0xFFFFFBFE))

    @Test
    fun everyAmountColourMeetsAaOnDarkBackgrounds() {
        for (colour in listOf(IncomeBlueOnDark, ExpenseOrangeOnDark, TransferGray)) {
            for (background in darkBackgrounds) {
                assertTrue("$colour on $background", contrast(colour, background) >= 4.5)
            }
        }
    }

    @Test
    fun incomeAndExpenseMeetAaOnLightBackgrounds() {
        for (colour in listOf(IncomeBlueOnLight, ExpenseOrangeOnLight)) {
            for (background in lightBackgrounds) {
                assertTrue("$colour on $background", contrast(colour, background) >= 4.5)
            }
        }
    }
}
