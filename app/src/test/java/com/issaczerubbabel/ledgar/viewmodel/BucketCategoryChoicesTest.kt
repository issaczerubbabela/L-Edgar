package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class BucketCategoryChoicesTest {

    private val buckets = mapOf(1L to "Essentials", 2L to "Fun")

    @Test
    fun `the bucket editor offers only the user's expense Categories`() {
        val choices = BucketDetailViewModel.categoryChoices(
            configured = listOf("Rent", "Groceries", "Leisure & Travel"),
            assignments = listOf(
                BucketCategory(cycleId = 1, bucketId = 1, category = "rent"),
                BucketCategory(cycleId = 1, bucketId = 2, category = "Investments/Savings") // deleted from the list
            ),
            bucketNameById = buckets
        )

        assertEquals(
            listOf(
                CategoryChoice("Groceries", null, null),
                CategoryChoice("Leisure & Travel", null, null),
                CategoryChoice("Rent", 1L, "Essentials")
            ),
            choices
        )
    }

    @Test
    fun `with no Categories set up, the routed ones are still offered`() {
        val choices = BucketDetailViewModel.categoryChoices(
            configured = emptyList(),
            assignments = listOf(BucketCategory(cycleId = 1, bucketId = 2, category = "Travel")),
            bucketNameById = buckets
        )

        assertEquals(listOf(CategoryChoice("Travel", 2L, "Fun")), choices)
    }
}
