package com.issaczerubbabel.ledgar.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: AccountRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun openDialog() = _uiState.update { it.copy(showDialog = true) }

    fun closeDialog() = _uiState.update { it.copy(showDialog = false) }

    fun selectInterval(interval: ExportInterval) = _uiState.update { it.copy(selectedInterval = interval) }

    fun previousMonth() = _uiState.update { it.copy(anchorMonth = it.anchorMonth.minusMonths(1)) }

    fun nextMonth() = _uiState.update {
        if (it.canPickLaterMonth) it.copy(anchorMonth = it.anchorMonth.plusMonths(1)) else it
    }

    fun updateCustomStart(input: String) = _uiState.update { it.copy(customStartDateInput = input) }

    fun updateCustomEnd(input: String) = _uiState.update { it.copy(customEndDateInput = input) }

    fun clearStatusMessage() = _uiState.update { it.copy(statusMessage = null) }

    fun requestExportDocument() {
        val range = buildRange(_uiState.value)
        if (range == null) {
            _uiState.update { it.copy(statusMessage = "Enter a valid start and end date") }
            return
        }
        val from = range.first.format(FILE_STAMP_FORMATTER)
        val to = range.second.format(FILE_STAMP_FORMATTER)
        _uiState.update { it.copy(pendingFileName = "sheetsync_export_${from}_to_$to.csv") }
    }

    fun consumeExportRequest() = _uiState.update { it.copy(pendingFileName = null) }

    fun exportDataToUri(uri: Uri) {
        viewModelScope.launch {
            val range = buildRange(_uiState.value)
            if (range == null) {
                _uiState.update { it.copy(statusMessage = "Enter a valid start and end date") }
                return@launch
            }

            val records = expenseRepository
                .getRecordsByDateRange(range.first.toString(), range.second.toString())
                .first()
            val accountMap = accountRepository.getAllAccounts().first().associateBy { it.id }

            val written = runCatching { writeCsv(uri, records, accountMap) }

            _uiState.update {
                if (written.isSuccess) {
                    it.copy(
                        showDialog = false,
                        statusMessage = "Exported ${records.size} transactions"
                    )
                } else {
                    it.copy(statusMessage = "Export failed. Pick a different location and try again.")
                }
            }
        }
    }

    private fun buildRange(state: ExportUiState): Pair<LocalDate, LocalDate>? {
        val anchor = state.anchorMonth
        return when (state.selectedInterval) {
            ExportInterval.CURRENT_MONTH -> anchor.atDay(1) to anchor.atEndOfMonth()
            ExportInterval.LAST_3_MONTHS -> anchor.minusMonths(2).atDay(1) to anchor.atEndOfMonth()
            ExportInterval.CURRENT_YEAR -> LocalDate.of(anchor.year, 1, 1) to LocalDate.of(anchor.year, 12, 31)
            ExportInterval.LAST_YEAR -> {
                val year = anchor.year - 1
                LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
            }
            ExportInterval.CUSTOM -> {
                val start = runCatching { LocalDate.parse(state.customStartDateInput) }.getOrNull()
                val end = runCatching { LocalDate.parse(state.customEndDateInput) }.getOrNull()
                if (start != null && end != null && !end.isBefore(start)) start to end else null
            }
        }
    }

    private suspend fun writeCsv(
        uri: Uri,
        records: List<ExpenseRecord>,
        accountMap: Map<Long, AccountRecord>
    ) = withContext(Dispatchers.IO) {
        appContext.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream).use { writer ->
                writer.appendLine("Date,Type,Category/Account,Amount,Note")
                records.forEach { record ->
                    val categoryOrAccount = if (record.type == "Transfer") {
                        val from = record.fromAccountId?.let { accountMap[it]?.accountName } ?: "Unknown"
                        val to = record.toAccountId?.let { accountMap[it]?.accountName } ?: "Unknown"
                        "Transfer: $from -> $to"
                    } else {
                        record.category
                    }
                    val note = record.remarks.ifBlank { record.description }
                    writer.appendLine(
                        listOf(
                            csvEscape(record.date),
                            csvEscape(record.type),
                            csvEscape(categoryOrAccount),
                            record.amount.toString(),
                            csvEscape(note)
                        ).joinToString(",")
                    )
                }
            }
        } ?: error("Unable to open output stream")
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        private val FILE_STAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
    }
}
