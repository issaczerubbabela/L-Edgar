package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named pot of money inside one [BudgetCycle]. Buckets belong to a cycle rather than
 * living across cycles, so a closed cycle stays exactly as it was and "carry over my
 * buckets" is a copy, not a pointer.
 */
@Entity(
    tableName = "budget_buckets",
    foreignKeys = [
        ForeignKey(
            entity = BudgetCycle::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cycleId"]),
        // Lets bucket_categories reference (id, cycleId) as a composite foreign key.
        Index(value = ["id", "cycleId"], unique = true)
    ]
)
data class BudgetBucket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val name: String,
    val note: String = "",
    /** Index into the calendar DOT_PALETTE in ui/theme/Color.kt. */
    val colorIndex: Int,
    /** Free text the user typed; may be empty. Decoration only, colour carries identity. */
    val emoji: String = "",
    val allocatedAmount: Double,
    val sortOrder: Int = 0
) {
    companion object {
        /** Size of DOT_PALETTE. Kept here so the data layer does not depend on Compose colours. */
        const val COLOR_COUNT = 9

        /**
         * Default colour for a category-named bucket. Same formula as categoryDotColor() in
         * ui/theme/Color.kt so a migrated bucket keeps the dot colour its category already had;
         * taking abs() after the modulo (not before) also survives Int.MIN_VALUE hash codes.
         */
        fun colorIndexFor(category: String): Int = kotlin.math.abs(category.hashCode() % COLOR_COUNT)
    }
}
