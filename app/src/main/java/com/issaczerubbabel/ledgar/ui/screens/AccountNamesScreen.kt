package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.viewmodel.AccountNameToFix
import com.issaczerubbabel.ledgar.viewmodel.AccountNamesViewModel

/**
 * Account names from the Sheet that match none of the user's Accounts. Their Transactions count in
 * no balance (or, from older versions, in a guessed one) until the user creates or picks an Account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountNamesScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: AccountNamesViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf<AccountNameToFix?>(null) }
    var mapping by remember { mutableStateOf<AccountNameToFix?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Account names") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
    ) { padding ->
        if (state.names.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Every account name in your Sheet matches one of your accounts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "These names come from your Sheet and match none of your accounts, so their transactions " +
                        "aren't in the right balance. Create the account, or pick the one they belong to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.names, key = { "${it.name.lowercase()}|${it.countedIn}" }) { entry ->
                AccountNameCard(entry, onCreate = { creating = entry }, onMap = { mapping = entry })
            }
        }
    }

    creating?.let { entry ->
        CreateAccountDialog(
            name = entry.name,
            groups = state.groups,
            onDismiss = { creating = null },
            onCreate = { name, group ->
                vm.createAccount(name, group)
                creating = null
            }
        )
    }
    mapping?.let { entry ->
        MapAccountDialog(
            entry = entry,
            accounts = state.accounts,
            onDismiss = { mapping = null },
            onMap = { accountId ->
                vm.mapTo(entry.name, accountId)
                mapping = null
            }
        )
    }
}

@Composable
private fun AccountNameCard(entry: AccountNameToFix, onCreate: () -> Unit, onMap: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            val count = if (entry.transactionCount == 1) "1 transaction" else "${entry.transactionCount} transactions"
            Text(
                if (entry.countedIn == null) "$count · in no account" else "$count · counted in ${entry.countedIn}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCreate) { Text("Create account") }
                OutlinedButton(onClick = onMap) { Text("Map to…") }
            }
        }
    }
}

@Composable
private fun CreateAccountDialog(
    name: String,
    groups: List<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, group: String) -> Unit
) {
    var accountName by remember { mutableStateOf(name) }
    var group by remember { mutableStateOf(groups.firstOrNull().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create account") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Group", style = MaterialTheme.typography.labelLarge)
                groups.forEach { option ->
                    ChoiceRow(label = option, selected = option == group, onClick = { group = option })
                }
                Text(
                    "It starts at a balance of 0. You can set its balance in Accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(accountName.trim(), group) }, enabled = accountName.isNotBlank() && group.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MapAccountDialog(
    entry: AccountNameToFix,
    accounts: List<AccountRecord>,
    onDismiss: () -> Unit,
    onMap: (accountId: Long) -> Unit
) {
    var selected by remember { mutableStateOf(accounts.firstOrNull { it.accountName == entry.countedIn }?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map “${entry.name}” to") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                accounts.sortedWith(compareBy({ it.isHidden }, { it.displayOrder })).forEach { account ->
                    ChoiceRow(
                        label = account.accountName,
                        supporting = account.groupName,
                        selected = account.id == selected,
                        onClick = { selected = account.id }
                    )
                }
                Text(
                    "Its transactions move to that account, and your Sheet shows the account's name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onMap) }, enabled = selected != null) { Text("Map") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, supporting: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
