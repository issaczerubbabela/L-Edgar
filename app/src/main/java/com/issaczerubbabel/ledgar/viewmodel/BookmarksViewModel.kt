package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    undoableActions: UndoableActions
) : ViewModel() {

    val bookmarkedTransactions: StateFlow<List<ExpenseRecord>> = repository
        .getBookmarkedTransactions()
        .combine(undoableActions.hiddenTransactionIds) { records, hidden -> records.filterNot { it.id in hidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeBookmark(id: Long) {
        viewModelScope.launch {
            repository.setBookmarked(id = id, isBookmarked = false)
        }
    }
}
