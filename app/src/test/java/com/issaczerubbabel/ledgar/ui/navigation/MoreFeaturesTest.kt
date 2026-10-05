package com.issaczerubbabel.ledgar.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreFeaturesTest {

    @Test
    fun featureIdsAndRoutesAreUnique() {
        val features = MoreFeatures.all
        assertEquals("duplicate feature id", features.size, features.map { it.id }.toSet().size)
        assertEquals("duplicate feature route", features.size, features.map { it.route }.toSet().size)
    }

    @Test
    fun everyFeatureHasATitleAndARoute() {
        MoreFeatures.all.forEach {
            assertTrue("${it.id} needs a title", it.title.isNotBlank())
            assertTrue("${it.id} needs a route", it.route.isNotBlank())
        }
    }

    @Test
    fun tilesAndOverflowTogetherCoverEveryFeatureInOrder() {
        assertTrue(MoreFeatures.tiles.size <= MoreFeatures.MAX_TILES)
        assertEquals(MoreFeatures.all, MoreFeatures.tiles + MoreFeatures.overflow)
    }
}
