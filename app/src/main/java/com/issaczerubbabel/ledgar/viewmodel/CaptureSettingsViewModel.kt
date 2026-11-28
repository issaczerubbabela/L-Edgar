package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.capture.CaptureIngestor
import com.issaczerubbabel.ledgar.capture.CaptureKinds
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.capture.source.BankEmails
import com.issaczerubbabel.ledgar.data.preferences.CapturePreferencesRepository
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.util.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CaptureSettingsViewModel @Inject constructor(
    private val preferences: CapturePreferencesRepository,
    private val captureRepository: CaptureRepository,
    private val ingestor: CaptureIngestor,
    keywords: KeywordDictionary,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    /** The merchant kinds (Expense), then the kinds of money in an alert names itself (Income, a cashback). */
    val canonicalKeys: List<String> = keywords.canonicalKeys + CaptureKinds.INCOME

    /** True for a kind mapped to an Income Category; the rest map to Expense ones. */
    fun isIncomeKind(key: String): Boolean = key in CaptureKinds.INCOME

    val enabled: StateFlow<Boolean> = preferences.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val readBankEmails: StateFlow<Boolean> = preferences.readBankEmails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** When the latest bank email was read, or null before the first, for the status line ([bankEmailStatus]). */
    val lastBankEmailReadAt: StateFlow<Long?> = preferences.lastBankEmailReadAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The Gmail label the user's filter applies. */
    val bankEmailLabel: StateFlow<String> = preferences.bankEmailLabelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BankEmails.DEFAULT_LABEL)

    val unparsedCount: StateFlow<Int> = captureRepository.observeUnparsedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val categoryMapping: StateFlow<Map<String, String>> = preferences.categoryMappingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val expenseCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.INCOME_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Turning capture off also clears queued captures; what was learned stays. */
    fun setEnabled(on: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(on)
            if (!on) captureRepository.clearPending()
        }
    }

    /** Off ignores Gmail again; what was already captured from email stays. */
    fun setReadBankEmails(on: Boolean) {
        viewModelScope.launch { preferences.setReadBankEmails(on) }
    }

    fun setBankEmailLabel(label: String) {
        viewModelScope.launch { preferences.setBankEmailLabel(label) }
    }

    fun mapCategory(canonical: String, category: String?) {
        viewModelScope.launch {
            preferences.mapCategory(canonical, category)
            ingestor.reguessPending()
        }
    }
}

private val bankEmailTime = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

/** "Last bank email read: 28 Nov, 3:47 PM", or "None yet, check your Gmail filter" before the first. */
internal fun bankEmailStatus(lastReadAt: Long?, zone: ZoneId): String =
    if (lastReadAt == null) "None yet, check your Gmail filter"
    else "Last bank email read: " + bankEmailTime.format(Instant.ofEpochMilli(lastReadAt).atZone(zone))
