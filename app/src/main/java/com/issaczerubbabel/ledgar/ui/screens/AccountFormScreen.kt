package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ui.components.DropdownField
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.viewmodel.AccountChoice
import com.issaczerubbabel.ledgar.viewmodel.AccountForm
import com.issaczerubbabel.ledgar.viewmodel.AccountFormResult
import com.issaczerubbabel.ledgar.viewmodel.AccountFormUiState
import com.issaczerubbabel.ledgar.viewmodel.AddEditAccountViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

private val asOfLabel = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * The one form for adding and editing an Account. When editing, the Initial balance is read-only
 * (Reconcile corrects a balance) and a Danger zone holds Archive and Delete….
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountFormScreen(
    onClose: () -> Unit,
    onFinished: (AccountFormResult) -> Unit,
    vm: AddEditAccountViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.results.collect { onFinished(it) } }
    LaunchedEffect(Unit) { vm.messages.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit account" else "Add account") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                },
                actions = {
                    Button(onClick = vm::save, enabled = state.isLoaded, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val nameError = state.errors.name
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::setName,
                label = { Text("Name") },
                placeholder = { Text("e.g. HDFC Savings") },
                isError = state.errors.name != null,
                supportingText = if (nameError != null) { { Text(nameError) } } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Group")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.groups.forEach { group ->
                        FilterChip(
                            selected = group == state.group,
                            onClick = { vm.setGroup(group) },
                            label = { Text(group) },
                            leadingIcon = if (group == state.group) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                    }
                    FilterChip(selected = false, onClick = { showNewGroup = true }, label = { Text("+ New group") })
                }
                val groupNote = state.errors.group
                    ?: if (state.isLiability) "${state.group} is a Liability group: its balance counts as money owed." else null
                if (groupNote != null) {
                    Text(
                        text = groupNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.errors.group != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            BalanceFields(state = state, onAmountChange = vm::setAmount, onPickDate = { showDatePicker = true })

            OutlinedTextField(
                value = state.description,
                onValueChange = vm::setDescription,
                label = { Text("Description (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            ) {
                SwitchRow(
                    title = "Included in totals",
                    detail = "Counts towards Net worth, Assets and Liabilities",
                    checked = state.includeInTotals,
                    onCheckedChange = vm::setIncludeInTotals
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                SwitchRow(
                    title = "Hidden",
                    detail = "Leaves the Accounts list and account pickers; still counted if included",
                    checked = state.isHidden,
                    onCheckedChange = vm::setHidden
                )
            }

            if (state.isEditMode) {
                DangerZone(
                    transactionCount = state.transactionCount,
                    onArchive = vm::archive,
                    onDelete = { showDelete = true }
                )
            }
        }
    }

    if (showDatePicker) {
        SingleDatePickerDialog(
            initialDate = state.asOfDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                vm.setAsOfDate(date)
                showDatePicker = false
            }
        )
    }

    if (showNewGroup) {
        NewGroupDialog(
            onDismiss = { showNewGroup = false },
            onAdd = { name ->
                vm.addGroup(name)
                showNewGroup = false
            }
        )
    }

    if (showDelete) {
        val accountId = state.accountId
        if (accountId != null) {
            AccountDeleteDialog(
                accountId = accountId,
                accountName = state.name,
                transactionCount = state.transactionCount,
                otherAccounts = state.otherAccounts,
                onDismiss = { showDelete = false },
                onConfirm = { moveTo ->
                    showDelete = false
                    vm.delete(moveTo)
                }
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BalanceFields(state: AccountFormUiState, onAmountChange: (String) -> Unit, onPickDate: () -> Unit) {
    val readOnly = state.isEditMode
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = onAmountChange,
                label = { Text(if (state.isLiability) "Amount owed" else "Balance") },
                prefix = { Text("₹") },
                readOnly = readOnly,
                enabled = !readOnly,
                isError = state.errors.amount != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.asOfDate.format(asOfLabel),
                onValueChange = {},
                label = { Text("Balance at end of") },
                readOnly = true,
                enabled = !readOnly,
                trailingIcon = if (readOnly) null else {
                    { IconButton(onClick = onPickDate) { Icon(Icons.Filled.DateRange, contentDescription = "Pick the date") } }
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = state.errors.amount ?: if (readOnly) {
                "Set when the account was added. To match your bank, Reconcile instead: changing this would change every past balance."
            } else {
                "What your bank shows at the end of this day. Transactions dated after it change the balance."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.errors.amount != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun DangerZone(transactionCount: Int, onArchive: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Danger zone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        DangerButton(
            title = "Archive",
            detail = "Hide it and leave it out of totals. Its transactions stay.",
            isDestructive = false,
            onClick = onArchive
        )
        DangerButton(
            title = "Delete…",
            detail = when (transactionCount) {
                0 -> "No transactions use this account"
                1 -> "1 transaction uses this account"
                else -> "$transactionCount transactions use this account"
            },
            isDestructive = true,
            onClick = onDelete
        )
    }
}

@Composable
private fun DangerButton(title: String, detail: String, isDestructive: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NewGroupDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Delete…: an Account without Transactions just needs a confirm. Otherwise its Transactions move to
 * another Account, or are deleted too once the Account's name is typed.
 */
@Composable
private fun AccountDeleteDialog(
    accountId: Long,
    accountName: String,
    transactionCount: Int,
    otherAccounts: List<AccountChoice>,
    onDismiss: () -> Unit,
    onConfirm: (moveToAccountId: Long?) -> Unit
) {
    var deleteTransactions by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf<AccountChoice?>(null) }
    var typedName by remember { mutableStateOf("") }
    val canConfirm = AccountForm.canConfirmDelete(
        transactionCount = transactionCount,
        deleteTransactions = deleteTransactions,
        moveToAccountId = moveTo?.id,
        accountId = accountId,
        typedName = typedName,
        accountName = accountName
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $accountName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (transactionCount == 0) {
                    Text("No transactions use this account. This can't be undone.")
                } else {
                    Text(
                        if (transactionCount == 1) "1 transaction uses this account. Choose what happens to it."
                        else "$transactionCount transactions use this account. Choose what happens to them."
                    )
                    ChoiceRow(
                        selected = !deleteTransactions,
                        title = "Move them to another account",
                        detail = "Your history stays complete",
                        onClick = { deleteTransactions = false }
                    )
                    if (!deleteTransactions) {
                        DropdownField(
                            label = "Move to",
                            options = otherAccounts.map { it.label },
                            selected = moveTo?.label.orEmpty(),
                            onSelect = { label -> moveTo = otherAccounts.firstOrNull { it.label == label } }
                        )
                    }
                    ChoiceRow(
                        selected = deleteTransactions,
                        title = "Delete them too",
                        detail = "Also removed from your Google Sheet. This can't be undone.",
                        onClick = { deleteTransactions = true }
                    )
                    if (deleteTransactions) {
                        OutlinedTextField(
                            value = typedName,
                            onValueChange = { typedName = it },
                            label = { Text("Type $accountName to confirm") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(if (transactionCount == 0 || deleteTransactions) null else moveTo?.id) },
                enabled = canConfirm,
                colors = if (deleteTransactions || transactionCount == 0) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else ButtonDefaults.buttonColors()
            ) {
                Text(if (transactionCount > 0 && !deleteTransactions) "Move and delete" else "Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
