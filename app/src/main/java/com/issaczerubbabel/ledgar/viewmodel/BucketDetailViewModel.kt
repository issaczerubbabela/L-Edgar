package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.BucketSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummary
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
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
                        val bucketNameById = buckets.associate { it.id to it.name }
                        val bucketIdByCategory = assignments.associate { it.category.trim().lowercase() to it.bucketId }

                        // Every category the user could reasonably want to place: the configured
                        // list, anything already routed, and anything that has actually been spent.
                        val names = LinkedHashMap<String, String>()
                        (options.map { it.name } +
                            assignments.map { it.category } +
                            summary.buckets.flatMap { it.categories }.map { it.category } +
                            summary.unbucketed.map { it.category })
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { names.putIfAbsent(it.lowercase(), it) }

                        BucketDetailUiState(
                            isLoading = false,
                            bucket = summary.buckets.firstOrNull { it.bucket.id == bucketId },
                            cycle = summary,
                            choices = names.values.sortedBy { it.lowercase() }.map { name ->
                                val holder = bucketIdByCategory[name.lowercase()]
                                CategoryChoice(name, holder, holder?.let { bucketNameById[it] })
                            }
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

    private companion object {
        const val EXPENSE_CATEGORY_TYPE = "EXPENSE_CATEGORY"
    }
}
