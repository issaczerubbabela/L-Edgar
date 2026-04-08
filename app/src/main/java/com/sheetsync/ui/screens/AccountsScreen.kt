package com.sheetsync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeBlue
import com.sheetsync.viewmodel.AddEditAccountViewModel
import com.sheetsync.viewmodel.AccountListItemUi
import com.sheetsync.viewmodel.AccountsViewModel

private enum class AccountActionMode {
    None,
    ShowHide,
    Delete,
    ModifyOrders
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    innerPadding: PaddingValues,
    onOpenAccountDetail: (Long) -> Unit,
    vm: AccountsViewModel = hiltViewModel(),
    formVm: AddEditAccountViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val formState by formVm.uiState.collectAsState()
    val accountGroups by formVm.accountGroups.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var actionMode by remember { mutableStateOf(AccountActionMode.None) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        formVm.saved.collect {
            showAddSheet = false
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        formVm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Accounts", color = MaterialTheme.colorScheme.onBackground) },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Add") },
                            onClick = {
                                showMenu = false
                                actionMode = AccountActionMode.None
                                formVm.startCreate()
                                showAddSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (actionMode == AccountActionMode.ShowHide) "Done Show/Hide" else "Show/Hide") },
                            onClick = {
                                showMenu = false
                                actionMode = if (actionMode == AccountActionMode.ShowHide) {
                                    AccountActionMode.None
                                } else {
                                    AccountActionMode.ShowHide
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (actionMode == AccountActionMode.Delete) "Done Delete" else "Delete") },
                            onClick = {
                                showMenu = false
                                actionMode = if (actionMode == AccountActionMode.Delete) {
                                    AccountActionMode.None
                                } else {
                                    AccountActionMode.Delete
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (actionMode == AccountActionMode.ModifyOrders) "Done Modify Orders" else "Modify Orders") },
                            onClick = {
                                showMenu = false
                                actionMode = if (actionMode == AccountActionMode.ModifyOrders) {
                                    AccountActionMode.None
                                } else {
                                    AccountActionMode.ModifyOrders
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { topPad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPad)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            SummaryTopBar(
                assets = state.assets,
                liabilities = state.liabilities,
                total = state.total
            )
            if (state.autoClassifiedLiabilityGroups.isNotEmpty()) {
                LiabilityLegendChip(state.autoClassifiedLiabilityGroups)
            }
            if (actionMode != AccountActionMode.None) {
                ActionModeHint(actionMode)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.assetGroups.isNotEmpty()) {
                    item { SectionHeader("Assets") }
                    state.assetGroups.forEach { (groupName, accounts) ->
                        item { GroupHeader(groupName) }
                        items(accounts.size) { index ->
                            AccountRow(
                                item = accounts[index],
                                mode = actionMode,
                                onOpen = onOpenAccountDetail,
                                onToggleVisibility = vm::toggleAccountVisibility,
                                onDelete = vm::deleteAccount,
                                onMoveUp = vm::moveAccountUp,
                                onMoveDown = vm::moveAccountDown
                            )
                        }
                    }
                }

                if (state.liabilityGroups.isNotEmpty()) {
                    item { SectionHeader("Liabilities") }
                    state.liabilityGroups.forEach { (groupName, accounts) ->
                        item { GroupHeader(groupName) }
                        items(accounts.size) { index ->
                            AccountRow(
                                item = accounts[index],
                                mode = actionMode,
                                onOpen = onOpenAccountDetail,
                                onToggleVisibility = vm::toggleAccountVisibility,
                                onDelete = vm::deleteAccount,
                                onMoveUp = vm::moveAccountUp,
                                onMoveDown = vm::moveAccountDown
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddEditAccountSheet(
            state = formState,
            accountGroups = accountGroups,
            onDismiss = { showAddSheet = false },
            onGroupChange = formVm::updateGroup,
            onNameChange = formVm::updateName,
            onAmountChange = formVm::updateAmount,
            onDescriptionChange = formVm::updateDescription,
            onIncludeInTotalsChange = formVm::updateIncludeInTotals,
            onHiddenChange = formVm::updateHidden,
            onSave = formVm::save,
            onDelete = formVm::deleteIfAllowed
        )
    }
}

@Composable
private fun LiabilityLegendChip(groups: List<String>) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = "Auto-classified liabilities: ${groups.joinToString(", ")}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ActionModeHint(mode: AccountActionMode) {
    val text = when (mode) {
        AccountActionMode.ShowHide -> "Show/Hide mode: tap the eye icon on any account."
        AccountActionMode.Delete -> "Delete mode: tap the delete icon on an account to remove it."
        AccountActionMode.ModifyOrders -> "Modify Orders mode: use arrows to move accounts up or down."
        AccountActionMode.None -> ""
    }
    if (text.isBlank()) return

    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun SummaryTopBar(assets: Double, liabilities: Double, total: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        SummaryCol("Assets", assets, IncomeBlue, Modifier.weight(1f))
        SummaryCol("Liabilities", liabilities, ExpenseRed, Modifier.weight(1f))
        SummaryCol("Total", total, MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

@Composable
private fun SummaryCol(label: String, amount: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text("₹ ${money(amount)}", color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AccountRow(
    item: AccountListItemUi,
    mode: AccountActionMode,
    onOpen: (Long) -> Unit,
    onToggleVisibility: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit
) {
    val rowAlpha = if (item.isHidden) 0.6f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = mode == AccountActionMode.None) { onOpen(item.id) }
            .alpha(rowAlpha)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                item.accountName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (item.isHidden) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = "Hidden account",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "₹ ${money(item.balance)}",
            color = if (item.balance < 0) ExpenseRed else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(end = if (mode == AccountActionMode.None) 0.dp else 8.dp)
        )

        when (mode) {
            AccountActionMode.ShowHide -> {
                IconButton(onClick = { onToggleVisibility(item.id) }) {
                    Icon(
                        imageVector = if (item.isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (item.isHidden) "Show account" else "Hide account"
                    )
                }
            }

            AccountActionMode.Delete -> {
                IconButton(onClick = { onDelete(item.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete account")
                }
            }

            AccountActionMode.ModifyOrders -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onMoveUp(item.id) }, enabled = item.canMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { onMoveDown(item.id) }, enabled = item.canMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                    }
                }
            }

            AccountActionMode.None -> Unit
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

private fun money(value: Double): String {
    val sign = if (value < 0) "-" else ""
    return sign + "%,.2f".format(kotlin.math.abs(value))
}
