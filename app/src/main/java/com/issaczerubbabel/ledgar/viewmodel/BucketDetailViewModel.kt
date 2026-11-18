package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.BucketSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One expense category as offered in the bucket editor, and where it currently lives. */
data class CategoryChoice(
    val name: String,
    /** The bucket holding this category right now, or null while it is Unbucketed. */
    val bucketId: Long?,
    val bucketName: String?
)

data class BucketDetailUiState(
    val isLoading: Boolean = true,
    /** The bucket no longer exists, for example it was just deleted. */
    val notFound: Boolean = false,
    val bucket: BucketSummary? = null,
    val cycle: CycleSummary? = null,
    val choices: List<CategoryChoice> = emptyList()
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BucketDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgets: BucketBudgetRepository,
    private val expenses: ExpenseRepository,
    dropdowns: DropdownOptionRepository
) : ViewModel() {

    private val bucketId: Long = checkNotNull(savedStateHandle["bucketId"])

    val uiState: StateFlow<BucketDetailUiState> = budgets.observeBucket(bucketId)
        .flatMapLatest { bucket ->
            if (bucket == null) {
                flowOf(BucketDetailUiState(isLoading = false, notFound = true))
            } else {
                combine(
                    budgets.observeCycle(bucket.cycleId),
                    budgets.observeBuckets(bucket.cycleId),
                    budgets.observeCategoryAssignments(bucket.cycleId),
                    expenses.getAllRecords(),
                    dropdowns.getOptionsByType(EXPENSE_CATEGORY_TYPE)
                ) { cycle, buckets, assignments, records, options ->
                    if (cycle == null) {
                        BucketDetailUiState(isLoading = false, notFound = true)
                    } else {
                        val summary = CycleSummaryBuilder.build(cycle, buckets, assignments, records, LocalDate.now())
                        BucketDetailUiState(
                            isLoading = false,
                            bucket = summary.buckets.firstOrNull { it.bucket.id == bucketId },
                            cycle = summary,
                            choices = categoryChoices(
                                configured = options.map { it.name },
                                assignments = assignments,
                                bucketNameById = buckets.associate { it.id to it.name }
                            )
                        )
                    }
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BucketDetailUiState())

    fun save(
        name: String,
        note: String,
        emoji: String,
        colorIndex: Int,
        allocatedAmount: Double,
        categories: Set<String>
    ) {
        val current = uiState.value.bucket?.bucket ?: return
        viewModelScope.launch {
            budgets.updateBucket(
                current.copy(
                    name = name.trim().ifBlank { current.name },
                    note = note.trim(),
                    emoji = emoji.trim(),
                    colorIndex = colorIndex,
                    allocatedAmount = allocatedAmount.coerceAtLeast(0.0)
                )
            )
            budgets.setBucketCategories(current.cycleId, current.id, categories)
        }
    }

    fun delete() {
        val current = uiState.value.bucket?.bucket ?: return
        viewModelScope.launch { budgets.deleteBucket(current) }
    }

    companion object {
        private const val EXPENSE_CATEGORY_TYPE = "EXPENSE_CATEGORY"

        /**
         * The Categories the bucket editor offers: the user's expense Categories, nothing else. A Category
         * deleted from the list isn't offered even while still routed or spent in this cycle; saving the
         * bucket drops its routing. Only with no Categories at all (never set up) are routed ones shown.
         */
        fun categoryChoices(
            configured: List<String>,
            assignments: List<BucketCategory>,
            bucketNameById: Map<Long, String>
        ): List<CategoryChoice> {
            val bucketIdByCategory = assignments.associate { CycleSummaryBuilder.categoryKey(it.category) to it.bucketId }
            val names = LinkedHashMap<String, String>()
            configured.ifEmpty { assignments.map { it.category } }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { names.putIfAbsent(CycleSummaryBuilder.categoryKey(it), it) }
            return names.values.sortedBy { it.lowercase() }.map { name ->
                val holder = bucketIdByCategory[CycleSummaryBuilder.categoryKey(name)]
                CategoryChoice(name, holder, holder?.let { bucketNameById[it] })
            }
        }
    }
}
