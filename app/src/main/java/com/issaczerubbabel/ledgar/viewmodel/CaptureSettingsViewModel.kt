package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.capture.CaptureIngestor
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
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
import javax.inject.Inject

@HiltViewModel
class CaptureSettingsViewModel @Inject constructor(
    private val preferences: CapturePreferencesRepository,
    private val captureRepository: CaptureRepository,
    private val ingestor: CaptureIngestor,
    keywords: KeywordDictionary,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    val canonicalKeys: List<String> = keywords.canonicalKeys

    val enabled: StateFlow<Boolean> = preferences.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val categoryMapping: StateFlow<Map<String, String>> = preferences.categoryMappingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val expenseCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Turning capture off also clears queued captures; what was learned stays. */
    fun setEnabled(on: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(on)
            if (!on) captureRepository.clearPending()
        }
    }

    fun mapCategory(canonical: String, category: String?) {
        viewModelScope.launch {
            preferences.mapCategory(canonical, category)
            ingestor.recategorizePending()
        }
    }
}
