package com.sheetsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _filterType = MutableStateFlow("All")
    val filterType: StateFlow<String> = _filterType.asStateFlow()

    private val _filterMonth = MutableStateFlow(LocalDate.now().monthValue)
    val filterMonth: StateFlow<Int> = _filterMonth.asStateFlow()

    private val _filterYear = MutableStateFlow(LocalDate.now().year)
    val filterYear: StateFlow<Int> = _filterYear.asStateFlow()

    val transactions: StateFlow<List<ExpenseRecord>> =
        combine(repository.getAllRecords(), _filterType, _filterMonth, _filterYear)
        { records, type, month, year ->
            records.filter { record ->
                val date = runCatching { LocalDate.parse(record.date) }.getOrNull() ?: return@filter false
                val typeMatch = type == "All" || record.type == type
                val monthMatch = date.monthValue == month
                val yearMatch = date.year == year
                typeMatch && monthMatch && yearMatch
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilterType(type: String) { _filterType.value = type }
    fun setFilterMonth(month: Int) { _filterMonth.value = month }
    fun setFilterYear(year: Int) { _filterYear.value = year }

    fun delete(record: ExpenseRecord) {
        viewModelScope.launch { repository.delete(record) }
    }
}
