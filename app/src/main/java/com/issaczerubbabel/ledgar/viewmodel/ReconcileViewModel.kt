package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.util.formatMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The Reconcile sheet, on an Account's page and in its edit form. Both routes carry the Account as
 * `accountId` (a String on the page's route, a Long on the form's).
 */
@HiltViewModel
class ReconcileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val accountId: Long = savedStateHandle.get<Any>("accountId")?.toString()?.toLongOrNull() ?: -1L

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    val sheet: StateFlow<ReconcileSheetUi?> = combine(accountRepository.getAccountBook(), _input) { book, text ->
        reconcileSheet(book, accountId, text, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Emits once the sheet's work is saved, so the screen can close it. */
    private val _done = MutableSharedFlow<Unit>()
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    fun setInput(text: String) {
        _input.value = text
    }

    fun clear() {
        _input.value = ""
    }

    fun reconcile() {
        val current = sheet.value ?: return
        val bankBalance = current.bankBalance ?: return
        viewModelScope.launch {
            if (!accountRepository.reconcile(accountId, bankBalance, LocalDate.now())) return@launch
            _messages.emit(
                if (current.matches) "${current.accountName} matches your bank"
                else "Added a Balance adjustment of ${current.difference}"
            )
            _input.value = ""
            _done.emit(Unit)
        }
    }

    fun startFresh() {
        val current = sheet.value ?: return
        val bankBalance = current.bankBalance ?: return
        viewModelScope.launch {
            if (!accountRepository.startFresh(accountId, bankBalance, LocalDate.now())) return@launch
            _messages.emit("${current.accountName} starts fresh at ${formatMoney(bankBalance)} today")
            _input.value = ""
            _done.emit(Unit)
        }
    }
}
