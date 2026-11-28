package com.issaczerubbabel.ledgar.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.util.TransactionType
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
import java.math.BigDecimal
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
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.write("﻿") // so Excel opens the file as UTF-8 and shows ₹ correctly
                writer.appendLine(
                    "Date,Type,Category,Amount,Account,From account,To account,Description,Remarks,Bookmarked,Transaction ID"
                )
                records.forEach { record ->
                    val (account, fromAccount, toAccount) = if (record.type == TransactionType.TRANSFER) {
                        Triple(
                            "",
                            record.fromAccountId?.let { accountMap[it]?.accountName } ?: record.fromAccountName.orEmpty(),
                            record.toAccountId?.let { accountMap[it]?.accountName } ?: record.toAccountName.orEmpty()
                        )
                    } else {
                        Triple(
                            record.accountId?.let { accountMap[it]?.accountName } ?: record.accountName.orEmpty(),
                            "",
                            ""
                        )
                    }
                    writer.appendLine(
                        listOf(
                            csvCell(record.date),
                            csvCell(record.type),
                            csvCell(record.category),
                            plainAmount(record.amount),
                            csvCell(account),
                            csvCell(fromAccount),
                            csvCell(toAccount),
                            csvCell(record.description),
                            csvCell(record.remarks),
                            csvCell(if (record.isBookmarked) "Yes" else ""),
                            csvCell(record.syncId.orEmpty())
                        ).joinToString(",")
                    )
                }
            }
        } ?: error("Unable to open output stream")
    }

    /** `Double.toString()` can land in scientific notation (e.g. `1.2E7`); a plain string never does. */
    private fun plainAmount(amount: Double): String = BigDecimal(amount.toString()).toPlainString()

    /**
     * Quotes a cell and escapes `"` as `""`. A cell starting with `= + - @` would otherwise run as a
     * formula when the file is opened in a spreadsheet — a risk since merchant names can come from a
     * bank alert via auto-capture — so it gets a leading `'` to stay plain text.
     */
    private fun csvCell(value: String): String {
        val guarded = if (value.isNotEmpty() && value[0] in FORMULA_PREFIXES) "'$value" else value
        return "\"${guarded.replace("\"", "\"\"")}\""
    }

    companion object {
        private val FILE_STAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
        private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@')
    }
}
