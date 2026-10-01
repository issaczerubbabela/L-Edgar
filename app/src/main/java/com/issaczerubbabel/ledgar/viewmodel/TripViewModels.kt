package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripSettlementRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.PostRow
import com.issaczerubbabel.ledgar.data.repository.TripDetail
import com.issaczerubbabel.ledgar.data.repository.TripRepository
import com.issaczerubbabel.ledgar.trip.MemberBalance
import com.issaczerubbabel.ledgar.trip.PlannedPayment
import com.issaczerubbabel.ledgar.trip.SplitMode
import com.issaczerubbabel.ledgar.trip.SplitProblem
import com.issaczerubbabel.ledgar.trip.TripExpenseInput
import com.issaczerubbabel.ledgar.trip.TripMath
import com.issaczerubbabel.ledgar.trip.TripSettlementInput
import com.issaczerubbabel.ledgar.util.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private fun TripSettlementRecord.toInput() = TripSettlementInput(fromMemberId, toMemberId, amountPaise)

/** What the Trip screen's three tabs show, worked out by [TripMath]. */
data class TripScreenState(
    val detail: TripDetail,
    val balances: List<MemberBalance>,
    val plan: List<PlannedPayment>,
    val totalPaise: Long,
    val myShareTotalPaise: Long,
    val myShares: Map<Long, Long>
) {
    val isArchived: Boolean get() = detail.trip.isArchived
}

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val repository: TripRepository
) : ViewModel() {

    val trips: StateFlow<List<TripRecord>> = repository.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createTrip(name: String, startDate: String, endDate: String?, members: List<String>, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch { onCreated(repository.createTrip(name, startDate, endDate, members)) }
    }
}

/** The Active Trip for the Trans tab banner: name, total and expense count. */
data class ActiveTripSummary(val tripId: Long, val name: String, val totalPaise: Long, val expenseCount: Int)

@HiltViewModel
class ActiveTripViewModel @Inject constructor(
    private val repository: TripRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val active: StateFlow<ActiveTripSummary?> = repository.observeActiveTrip()
        .flatMapLatest { trip -> if (trip == null) flowOf(null) else repository.observeDetail(trip.id) }
        .map { detail ->
            detail?.let { ActiveTripSummary(it.trip.id, it.trip.name, it.expenses.sumOf { e -> e.amountPaise }, it.expenses.size) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@HiltViewModel
class TripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TripRepository
) : ViewModel() {

    val tripId: Long = checkNotNull(savedStateHandle.get<Long>("tripId"))

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<TripScreenState?> = repository.observeDetail(tripId).map { detail ->
        detail?.let {
            val settlements = it.settlements.map { s -> s.toInput() }
            val balances = TripMath.balances(it.members, it.expenses, settlements)
            val selfId = it.self?.id
            val myShares = it.expenses.associate { e -> e.id to (selfId?.let { id -> TripMath.shares(e)[id] } ?: 0L) }
            TripScreenState(
                detail = it,
                balances = balances,
                plan = TripMath.settleUpPlan(balances),
                totalPaise = it.expenses.sumOf { e -> e.amountPaise },
                myShareTotalPaise = myShares.values.sum(),
                myShares = myShares
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun clearMessage() { _message.value = null }

    fun addMember(name: String) {
        if (name.isBlank()) return
        launchSafely { repository.addMember(tripId, name) }
    }

    fun updateMember(memberId: Long, name: String, upiId: String?) = launchSafely { repository.updateMember(memberId, name, upiId) }

    fun removeMember(memberId: Long) = launchSafely {
        if (!repository.removeMember(memberId)) _message.value = "They're on an expense or a payment. Edit those first."
    }

    fun markPaid(payment: PlannedPayment) = launchSafely {
        repository.recordSettlement(tripId, payment.fromId, payment.toId, payment.amountPaise, LocalDate.now().toString())
    }

    fun undoSettlement(settlementId: Long) = launchSafely { repository.deleteSettlement(settlementId) }

    fun unarchive() = launchSafely {
        val removed = repository.unarchive(tripId)
        _message.value = "Deleted $removed posted transactions. The trip is active again."
    }

    fun summaryText(): String? = state.value?.detail?.let {
        TripMath.summaryText(it.trip.name, it.members, it.expenses, it.settlements.map { s -> s.toInput() })
    }

    fun csvText(): String? = state.value?.detail?.let { TripMath.csv(it.members, it.expenses) }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { _message.value = it.message ?: "Something went wrong" }
        }
    }
}

/** The Trip expense form. Amounts are typed as rupee strings and kept as paise. */
data class TripExpenseForm(
    val amount: String = "",
    val purpose: String = "",
    val payerId: Long = 0,
    val mode: SplitMode = SplitMode.EQUAL,
    val memberIds: List<Long> = emptyList(),
    val inputs: Map<Long, String> = emptyMap(),
    val category: String = "",
    val date: String = LocalDate.now().toString()
)

data class TripExpenseScreenState(
    val detail: TripDetail? = null,
    val form: TripExpenseForm = TripExpenseForm(),
    val isEditing: Boolean = false,
    val fromCapture: Boolean = false,
    val categories: List<String> = emptyList()
) {
    val amountPaise: Long get() = parsePaise(form.amount)

    val input: TripExpenseInput
        get() = TripExpenseInput(
            id = 0,
            date = form.date,
            purpose = form.purpose,
            amountPaise = amountPaise,
            payerId = form.payerId,
            mode = form.mode,
            memberIds = detail?.members?.map { it.id }?.filter { it in form.memberIds } ?: form.memberIds,
            inputs = form.inputs.mapValues { parsePaise(it.value) }.filterValues { it != 0L },
            category = form.category
        )

    val shares: Map<Long, Long> get() = TripMath.shares(input)

    val outsideTrip: Boolean
        get() = detail?.trip?.let { form.date < it.startDate || (it.endDate != null && form.date > it.endDate) } ?: false

    /** Why Save is disabled, or null when the expense can be saved. */
    val problem: String?
        get() = when {
            amountPaise <= 0 -> "Enter an amount"
            form.purpose.isBlank() -> "Add a purpose"
            else -> when (val p = TripMath.splitProblem(input)) {
                null -> null
                SplitProblem.NoMembers -> "Pick at least one person"
                SplitProblem.AdjustTooLarge -> "The extras are more than the amount"
                is SplitProblem.ExactLeft ->
                    if (p.leftPaise > 0) "${TripMath.rupees(p.leftPaise)} left to assign" else "${TripMath.rupees(-p.leftPaise)} too much assigned"
            }
        }
}

fun parsePaise(text: String): Long =
    text.trim().replace(",", "").toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong() ?: 0L

fun paiseToInput(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else TripMath.decimal(paise)

@HiltViewModel
class TripExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TripRepository,
    private val captureRepository: CaptureRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle.get<Long>("tripId"))
    private val expenseId: Long = savedStateHandle.get<Long>("expenseId") ?: 0L
    private val captureId: Long = savedStateHandle.get<Long>("captureId") ?: 0L

    private val _state = MutableStateFlow(TripExpenseScreenState(isEditing = expenseId != 0L, fromCapture = captureId != 0L))
    val state: StateFlow<TripExpenseScreenState> = _state.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    init {
        viewModelScope.launch {
            dropdownOptionRepository.getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION).collect { options ->
                _state.update { it.copy(categories = options.map { o -> o.name }) }
            }
        }
        viewModelScope.launch {
            val detail = repository.observeDetail(tripId).first() ?: return@launch
            val existing = detail.expenses.firstOrNull { it.id == expenseId }
            val capture = if (captureId != 0L) captureRepository.getById(captureId) else null
            val form = when {
                existing != null -> TripExpenseForm(
                    amount = paiseToInput(existing.amountPaise),
                    purpose = existing.purpose,
                    payerId = existing.payerId,
                    mode = existing.mode,
                    memberIds = existing.memberIds,
                    inputs = existing.inputs.mapValues { paiseToInput(it.value) },
                    category = existing.category,
                    date = existing.date
                )
                else -> TripExpenseForm(
                    amount = capture?.let { paiseToInput(Math.round(it.amount * 100)) } ?: "",
                    purpose = capture?.let { c -> (c.merchantRaw ?: c.merchantNorm).orEmpty() } ?: "",
                    payerId = detail.self?.id ?: detail.members.first().id,
                    memberIds = detail.members.map { it.id },
                    category = capture?.suggestedCategory.orEmpty(),
                    date = capture?.let { Instant.ofEpochMilli(it.txnTime).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
                        ?: LocalDate.now().toString()
                )
            }
            _state.update { it.copy(detail = detail, form = form) }
        }
    }

    private fun edit(change: (TripExpenseForm) -> TripExpenseForm) = _state.update { it.copy(form = change(it.form)) }

    fun setAmount(value: String) = edit { it.copy(amount = value) }
    fun setPurpose(value: String) = edit { it.copy(purpose = value) }
    fun setPayer(memberId: Long) = edit { it.copy(payerId = memberId) }
    fun setCategory(value: String) = edit { it.copy(category = value) }
    fun setDate(value: String) = edit { it.copy(date = value) }
    fun setInput(memberId: Long, value: String) = edit { it.copy(inputs = it.inputs + (memberId to value)) }

    fun setMode(mode: SplitMode) {
        val s = _state.value
        // Switching to Exact starts from the equal split, so only the differences need typing.
        val inputs = if (mode == SplitMode.EXACT) s.copy(form = s.form.copy(mode = SplitMode.EQUAL)).shares.mapValues { paiseToInput(it.value) } else emptyMap()
        edit { it.copy(mode = mode, inputs = inputs) }
    }

    fun toggleMember(memberId: Long) = edit {
        if (memberId in it.memberIds) it.copy(memberIds = it.memberIds - memberId, inputs = it.inputs - memberId)
        else it.copy(memberIds = it.memberIds + memberId)
    }

    fun save() {
        val s = _state.value
        if (s.problem != null) return
        viewModelScope.launch {
            val input = s.input.copy(id = expenseId)
            if (captureId != 0L && expenseId == 0L) repository.addCaptureToTrip(captureId, tripId, input)
            else repository.saveExpense(tripId, input)
            _done.value = true
        }
    }

    fun delete() {
        if (expenseId == 0L) return
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            _done.value = true
        }
    }
}

/** One Review & Post row with whatever the user changed on it. */
data class ReviewRowUi(
    val tripExpenseId: Long,
    val date: String,
    val description: String,
    val ofAmountPaise: Long,
    val payerName: String,
    val amount: String,
    val category: String,
    val accountId: Long?,
    val include: Boolean,
    val edited: Boolean
)

private data class ReviewEdit(
    val include: Boolean = true,
    val amount: String? = null,
    val category: String? = null,
    val accountId: Long? = null
)

data class ReviewState(
    val tripName: String = "",
    val rows: List<ReviewRowUi> = emptyList(),
    val skippedCount: Int = 0,
    val openPayments: Int = 0,
    val defaultAccountId: Long? = null
) {
    val included: List<ReviewRowUi> get() = rows.filter { it.include }
    val missingCategory: Int get() = included.count { it.category.isBlank() }
    val totalPaise: Long get() = included.sumOf { parsePaise(it.amount) }
    val canPost: Boolean get() = included.isNotEmpty() && missingCategory == 0 && included.all { parsePaise(it.amount) > 0 }
}

@HiltViewModel
class TripReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TripRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle.get<Long>("tripId"))
    private val edits = MutableStateFlow<Map<Long, ReviewEdit>>(emptyMap())
    private val defaultAccount = MutableStateFlow<Long?>(null)

    val accounts: StateFlow<List<AccountRecord>> = accountRepository.getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _posted = MutableStateFlow<Int?>(null)
    val posted: StateFlow<Int?> = _posted.asStateFlow()

    val state: StateFlow<ReviewState> = combine(repository.observeDetail(tripId), edits, defaultAccount, accounts) { detail, edited, chosen, accountList ->
        if (detail == null) return@combine ReviewState()
        val selfId = detail.self?.id ?: return@combine ReviewState(tripName = detail.trip.name)
        val names = detail.members.associate { it.id to it.name }
        val fallback = chosen ?: accountList.firstOrNull()?.id
        val preview = TripMath.postPreview(detail.trip.name, selfId, detail.expenses)
        val byId = detail.expenses.associateBy { it.id }
        val balances = TripMath.balances(detail.members, detail.expenses, detail.settlements.map { it.toInput() })
        ReviewState(
            tripName = detail.trip.name,
            rows = preview.map { row ->
                val e = edited[row.expenseId] ?: ReviewEdit()
                val source = byId.getValue(row.expenseId)
                ReviewRowUi(
                    tripExpenseId = row.expenseId,
                    date = row.date,
                    description = row.description,
                    ofAmountPaise = source.amountPaise,
                    payerName = names[source.payerId].orEmpty(),
                    amount = e.amount ?: paiseToInput(row.sharePaise),
                    category = e.category ?: row.category,
                    accountId = e.accountId ?: fallback,
                    include = e.include,
                    edited = e.amount != null && parsePaise(e.amount) != row.sharePaise
                )
            },
            skippedCount = detail.expenses.size - preview.size,
            openPayments = TripMath.settleUpPlan(balances).size,
            defaultAccountId = fallback
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReviewState())

    private fun editRow(id: Long, change: (ReviewEdit) -> ReviewEdit) = edits.update { it + (id to change(it[id] ?: ReviewEdit())) }

    fun setDefaultAccount(accountId: Long) {
        defaultAccount.value = accountId
        edits.update { all -> all.mapValues { it.value.copy(accountId = null) } }
    }

    fun setInclude(id: Long, include: Boolean) = editRow(id) { it.copy(include = include) }
    fun setAmount(id: Long, amount: String) = editRow(id) { it.copy(amount = amount) }
    fun setCategory(id: Long, category: String) = editRow(id) { it.copy(category = category) }
    fun setAccount(id: Long, accountId: Long) = editRow(id) { it.copy(accountId = accountId) }

    fun post() {
        val s = state.value
        if (!s.canPost) return
        viewModelScope.launch {
            _posted.value = repository.post(
                tripId,
                s.included.map {
                    PostRow(it.tripExpenseId, it.date, it.description, it.category, parsePaise(it.amount), it.accountId)
                }
            )
        }
    }
}
