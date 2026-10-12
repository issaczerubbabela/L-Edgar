package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.viewmodel.RepeatRuleUi
import com.issaczerubbabel.ledgar.viewmodel.RepeatingViewModel

/**
 * More > Repeating transactions: occurrences waiting for the user (Ask me first), what's coming in
 * the next 30 days, and every Repeat rule, each opening a sheet to pause, edit or delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatingScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    /** Opens Add Transaction for the rule's due occurrence ("Edit & add"). */
    onEditAndAdd: (Long) -> Unit,
    /** Opens Add Transaction on the Repeat rule itself. */
    onEditRule: (Long) -> Unit,
    /** A rule to open straight away, from a Repeating transaction's "Edit rule". */
    initialRuleId: Long? = null,
    vm: RepeatingViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var openRuleId by rememberSaveable { mutableLongStateOf(initialRuleId ?: -1L) }

    LaunchedEffect(Unit) {
        vm.deleted.collect { deleted ->
            val result = snackbarHostState.showSnackbar(deleted.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete(deleted)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Repeating transactions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .padding(bottom = innerPadding.calculateBottomPadding())
        if (state.isLoaded && state.rules.isEmpty()) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "Set Repeat when adding rent, salary or subscriptions, and they'll add themselves here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (state.waiting.isNotEmpty()) {
                item(key = "waiting-header") { SectionHeader("Waiting for you") }
                items(state.waiting, key = { "waiting-${it.rule.id}" }) { row ->
                    WaitingCard(
                        row = row,
                        onAdd = { vm.add(row.rule) },
                        onEditAndAdd = { onEditAndAdd(row.rule.id) },
                        onSkip = { vm.skip(row.rule) }
                    )
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming-header") { SectionHeader("Upcoming (next 30 days)") }
                items(state.upcoming, key = { "upcoming-${it.rule.id}" }) { row ->
                    RuleRow(row = row, onClick = { openRuleId = row.rule.id })
                }
            }
            item(key = "all-header") { SectionHeader("All rules") }
            items(state.rules, key = { "rule-${it.rule.id}" }) { row ->
                RuleRow(row = row, onClick = { openRuleId = row.rule.id })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
    }

    state.rules.firstOrNull { it.rule.id == openRuleId }?.let { row ->
        RuleSheet(
            row = row,
            onDismiss = { openRuleId = -1L },
            onAskFirstChange = { vm.setAskFirst(row.rule, it) },
            onPausedChange = { vm.setPaused(row.rule, it) },
            onEdit = { openRuleId = -1L; onEditRule(row.rule.id) },
            onDelete = { openRuleId = -1L; vm.delete(row.rule) }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { heading() }
    )
}

@Composable
private fun WaitingCard(row: RepeatRuleUi, onAdd: () -> Unit, onEditAndAdd: () -> Unit, onSkip: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            RuleSummary(row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) { Text("Skip") }
                TextButton(onClick = onEditAndAdd) { Text("Edit & add") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onAdd) { Text("Add") }
            }
        }
    }
}

@Composable
private fun RuleRow(row: RepeatRuleUi, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open repeat", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        RuleSummary(row)
    }
}

@Composable
private fun RuleSummary(row: RepeatRuleUi) {
    val dimmed = row.isPaused || row.isFinished
    val primary = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = row.amount, style = MaterialTheme.typography.bodyLarge, color = primary)
        }
        Text(
            text = row.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.status,
            style = MaterialTheme.typography.labelMedium,
            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    row: RepeatRuleUi,
    onDismiss: () -> Unit,
    onAskFirstChange: (Boolean) -> Unit,
    onPausedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val rule: RecurringRule = row.rule
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { RuleSummary(row) }
            SwitchRow(
                title = "Ask me first",
                subtitle = "Wait here for Add or Skip instead of adding it on the day",
                checked = !rule.autoAdd,
                onCheckedChange = onAskFirstChange
            )
            if (!row.isFinished) {
                SwitchRow(
                    title = "Paused",
                    subtitle = "Resuming picks up from the next date, without filling in the gap",
                    checked = rule.isPaused,
                    onCheckedChange = onPausedChange
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
            }
            Text(
                text = "Changes apply to what it adds from now on. Transactions it already added stay as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        // The whole row toggles, so TalkBack reads it once as "Ask me first, switch, on".
        Switch(checked = checked, onCheckedChange = null)
    }
}
