package com.sheetsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.BuildConfig
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.remote.ApiService
import com.sheetsync.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Success(val count: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    fun importHistoricalData() {
        if (_importState.value is ImportState.Loading) return
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            try {
                val response = apiService.importRecords(BuildConfig.APPS_SCRIPT_URL)
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    val records = dtos.map { dto ->
                        ExpenseRecord(
                            date = dto.date,
                            type = dto.type,
                            // Merge split columns back into a single category field
                            category = if (dto.type == "Expense") dto.expCategory else dto.incCategory,
                            description = dto.description,
                            amount = dto.amount,
                            paymentMode = dto.paymentMode,
                            remarks = dto.remarks,
                            isSynced = true // already in Sheets — never push back
                        )
                    }
                    records.forEach { repository.save(it) }
                    _importState.value = ImportState.Success(records.size)
                } else {
                    _importState.value = ImportState.Error("Server error: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetImportState() { _importState.value = ImportState.Idle }
}
