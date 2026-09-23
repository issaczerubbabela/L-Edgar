package com.issaczerubbabel.ledgar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.using
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.components.NumericKeypad
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.util.applyKeypadAction
import com.issaczerubbabel.ledgar.viewmodel.LogViewModel
import com.issaczerubbabel.ledgar.viewmodel.SyncStatusUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class LogSheet { Category, Account, FromAccount, ToAccount, Note }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    innerPadding: PaddingValues,
    showEnterAppButton: Boolean = false,
    onEnterApp: () -> Unit = {},
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    vm: LogViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<LogSheet?>(null) }
    var savePulseSignal by remember { mutableIntStateOf(0) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()

    BackHandler(
        enabled = !showDatePicker && !showDeleteConfirm && activeSheet == null,
        onBack = onBack
    )

    LaunchedEffect(Unit) {
        vm.startSyncStatusObserver()
    }

    LaunchedEffect(vm.saveSuccess) {
        if (vm.saveSuccess) {
            savePulseSignal++
            if (vm.isEditMode) {
                onSaved()
            } else {
                snackbarHostState.showSnackbar("Transaction saved")
            }
            vm.resetSaveSuccess()
        }
    }
    LaunchedEffect(vm.errorMessage) {
        vm.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }
    LaunchedEffect(vm.syncInfoMessage) {
        vm.syncInfoMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSyncInfoMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            LogTopBar(
                isEditMode = vm.isEditMode,
                showEnterAppButton = showEnterAppButton,
                syncStatus = vm.syncStatus,
                onRetrySync = vm::retrySync,
                onBack = onBack,
                onEnterApp = onEnterApp,
                onDeleteClick = { showDeleteConfirm = true }
            )

            val transferContext = if (vm.selectedType == "Transfer") {
                val from = accounts.firstOrNull { it.id == vm.selectedFromAccountId }?.accountName
                val to = accounts.firstOrNull { it.id == vm.selectedToAccountId }?.accountName
                if (from != null || to != null) "${from ?: "?"} → ${to ?: "?"}" else null
            } else {
                null
            }

            DatePillRow(
                date = vm.selectedDate,
                contextText = transferContext,
                onClick = { showDatePicker = true }
            )

            TypeRow(
                selectedType = vm.selectedType,
                onSelect = { label ->
                    vm.selectedType = label
                    vm.selectedCategory = if (label == "Transfer") "Transfer" else ""
                    vm.selectedAccountId = null
                    vm.selectedFromAccountId = null
                    vm.selectedToAccountId = null
                }
            )

            AmountDisplay(
                amount = vm.amount,
                type = vm.selectedType,
                pulseSignal = savePulseSignal,
                modifier = Modifier.weight(1f)
            )

            val selectedAccountName = accounts.firstOrNull { it.id == vm.selectedAccountId }?.accountName
            val selectedFromName = accounts.firstOrNull { it.id == vm.selectedFromAccountId }?.accountName
            val selectedToName = accounts.firstOrNull { it.id == vm.selectedToAccountId }?.accountName

            ChipRow(
                selectedType = vm.selectedType,
                categoryLabel = vm.selectedCategory.ifBlank { "Category" },
                hasCategoryValue = vm.selectedCategory.isNotBlank(),
                accountLabel = selectedAccountName ?: "Account",
                hasAccountValue = selectedAccountName != null,
                fromLabel = selectedFromName ?: "From account",
                hasFromValue = selectedFromName != null,
                toLabel = selectedToName ?: "To account",
                hasToValue = selectedToName != null,
                hasNote = vm.description.isNotBlank() || vm.remarks.isNotBlank(),
                onCategoryClick = { activeSheet = LogSheet.Category },
                onAccountClick = { activeSheet = LogSheet.Account },
                onFromAccountClick = { activeSheet = LogSheet.FromAccount },
                onToAccountClick = { activeSheet = LogSheet.ToAccount },
                onNoteClick = { activeSheet = LogSheet.Note }
            )

            NumericKeypad(
                onAction = { action -> vm.amount = applyKeypadAction(vm.amount, action) },
                onCommit = vm::save,
                commitEnabled = vm.amount.isNotBlank()
            )
        }
    }

    when (activeSheet) {
        LogSheet.Category -> {
            val categories = if (vm.selectedType == "Income") incomeCategories else expenseCategories
            OptionPickerSheet(
                title = "Category",
                options = categories,
                selected = vm.selectedCategory,
                onSelect = {
                    vm.selectedCategory = it
                    activeSheet = null
                },
                onCreate = {
                    vm.addCategoryInline(it)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null },
                manageHint = "Reorder or delete in More → Dropdowns"
            )
        }

        LogSheet.Account -> OptionPickerSheet(
            title = "Account",
            options = accounts.map { it.accountName },
            selected = selectedAccountNameOrEmpty(accounts, vm.selectedAccountId),
            onSelect = { name ->
                accounts.firstOrNull { it.accountName == name }?.let { vm.selectedAccountId = it.id }
                activeSheet = null
            },
            onDismiss = { activeSheet = null },
            manageHint = "Add accounts from the Accounts tab"
        )

        LogSheet.FromAccount -> OptionPickerSheet(
            title = "From Account",
            options = accounts.map { it.accountName },
            selected = selectedAccountNameOrEmpty(accounts, vm.selectedFromAccountId),
            onSelect = { name ->
                accounts.firstOrNull { it.accountName == name }?.let { vm.selectedFromAccountId = it.id }
                activeSheet = null
            },
            onDismiss = { activeSheet = null },
            manageHint = "Add accounts from the Accounts tab"
        )

        LogSheet.ToAccount -> OptionPickerSheet(
            title = "To Account",
            options = accounts.map { it.accountName },
            selected = selectedAccountNameOrEmpty(accounts, vm.selectedToAccountId),
            onSelect = { name ->
                accounts.firstOrNull { it.accountName == name }?.let { vm.selectedToAccountId = it.id }
                activeSheet = null
            },
            onDismiss = { activeSheet = null },
            manageHint = "Add accounts from the Accounts tab"
        )

        LogSheet.Note -> NoteSheet(
            description = vm.description,
            remarks = vm.remarks,
            onDescriptionChange = { vm.description = it },
            onRemarksChange = { vm.remarks = it },
            onDismiss = { activeSheet = null }
        )

        null -> Unit
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This transaction will be removed locally now and deleted from Google Sheets on next sync.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteCurrent()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = vm.selectedDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        vm.selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}

private fun selectedAccountNameOrEmpty(
    accounts: List<AccountRecord>,
    selectedId: Long?
): String = accounts.firstOrNull { it.id == selectedId }?.accountName.orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogTopBar(
    isEditMode: Boolean,
    showEnterAppButton: Boolean,
    syncStatus: SyncStatusUi,
    onRetrySync: () -> Unit,
    onBack: () -> Unit,
    onEnterApp: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.weight(1f))
        SyncStatusIndicator(status = syncStatus, onRetry = onRetrySync)
        if (isEditMode) {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete transaction")
            }
        }
        if (showEnterAppButton) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = onEnterApp) {
                Text("Enter App")
            }
        }
    }
}

@Composable
private fun DatePillRow(date: LocalDate, contextText: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = "Change date",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (contextText != null) {
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeRow(selectedType: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val types = listOf("Expense", "Income", "Transfer")
        types.forEachIndexed { index, label ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                selected = selectedType == label,
                onClick = { onSelect(label) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun AmountDisplay(
    amount: String,
    type: String,
    pulseSignal: Int,
    modifier: Modifier = Modifier
) {
    val color = when (type) {
        "Expense" -> ExpenseOrange
        "Income" -> IncomeBlue
        else -> MaterialTheme.colorScheme.onSurface
    }
    val displayValue = amount.ifEmpty { "0" }
    val baseSp = when {
        displayValue.length <= 5 -> 96f
        displayValue.length <= 8 -> 72f
        else -> 52f
    }
    val fontSize = responsiveTextSize(baseSp = baseSp, minSp = 36f, maxSp = 104f)

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(pulseSignal) {
        if (pulseSignal == 0) return@LaunchedEffect
        pulseScale.animateTo(1.06f, tween(durationMillis = 90, easing = LinearEasing))
        pulseScale.animateTo(1f, tween(durationMillis = 160, easing = LinearEasing))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .scale(pulseScale.value),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "₹",
                fontSize = fontSize.value.times(0.4f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = fontSize.value.times(0.12f).dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = displayValue,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun responsiveTextSize(baseSp: Float, minSp: Float = 12f, maxSp: Float = 48f) =
    (baseSp * (LocalConfiguration.current.screenWidthDp / 411f).coerceIn(0.9f, 1.08f))
        .coerceIn(minSp, maxSp).sp

@Composable
private fun ChipRow(
    selectedType: String,
    categoryLabel: String,
    hasCategoryValue: Boolean,
    accountLabel: String,
    hasAccountValue: Boolean,
    fromLabel: String,
    hasFromValue: Boolean,
    toLabel: String,
    hasToValue: Boolean,
    hasNote: Boolean,
    onCategoryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onFromAccountClick: () -> Unit,
    onToAccountClick: () -> Unit,
    onNoteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = selectedType == "Transfer",
            transitionSpec = {
                (fadeIn(tween(150)) + slideInHorizontally(tween(150)) { width -> width / 6 })
                    .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { width -> -width / 6 })
                    .using(SizeTransform(clip = false))
            },
            label = "chip-type-swap"
        ) { isTransfer ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isTransfer) {
                    TransactionChip(label = fromLabel, filled = hasFromValue, onClick = onFromAccountClick)
                    TransactionChip(label = toLabel, filled = hasToValue, onClick = onToAccountClick)
                } else {
                    TransactionChip(label = categoryLabel, filled = hasCategoryValue, onClick = onCategoryClick)
                    TransactionChip(label = accountLabel, filled = hasAccountValue, onClick = onAccountClick)
                }
            }
        }
        TransactionChip(
            label = if (hasNote) "Note added" else "+ Note",
            filled = hasNote,
            onClick = onNoteClick
        )
    }
}

@Composable
private fun TransactionChip(label: String, filled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (filled) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (filled) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(
    description: String,
    remarks: String,
    onDescriptionChange: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Note", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = remarks,
                onValueChange = onRemarksChange,
                label = { Text("Remarks (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SyncStatusIndicator(status: SyncStatusUi, onRetry: () -> Unit) {
    val (text, containerColor, contentColor) = when (status) {
        SyncStatusUi.Idle -> Triple(
            "Sync idle",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SyncStatusUi.Syncing -> Triple(
            "Syncing...",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SyncStatusUi.Synced -> Triple(
            "Synced",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        SyncStatusUi.Failed -> Triple(
            "Retry sync",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    val isRetryEnabled = status == SyncStatusUi.Failed

    SuggestionChip(
        onClick = onRetry,
        enabled = isRetryEnabled,
        label = { Text(text) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor,
            labelColor = contentColor,
            disabledLabelColor = contentColor
        )
    )
}
