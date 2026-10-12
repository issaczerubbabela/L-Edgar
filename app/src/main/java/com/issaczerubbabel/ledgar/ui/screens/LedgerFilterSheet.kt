package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.ledger.FilterOptionUi
import com.issaczerubbabel.ledgar.ledger.FilterOptions
import com.issaczerubbabel.ledgar.ledger.FilterOptionsSection
import com.issaczerubbabel.ledgar.ledger.LedgerFilter

/**
 * "Filter Oct 2026": categories (Expense or Income) and Accounts for the month on screen, each with
 * its amount this month. Nothing applies until "Show n transactions".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerFilterSheet(
    monthLabel: String,
    options: FilterOptions,
    draft: LedgerFilter,
    onToggleExpense: (String) -> Unit,
    onToggleIncome: (String) -> Unit,
    onToggleAccount: (Long) -> Unit,
    onAnyCategory: (income: Boolean) -> Unit,
    onAnyAccount: () -> Unit,
    onClear: () -> Unit,
    onShow: () -> Unit,
    onDismiss: () -> Unit
) {
    var showIncome by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter $monthLabel", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Clear") }
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                item {
                    val listShown = if (showIncome) draft.incomeCategories else draft.expenseCategories
                    SectionHeader("Categories", isAny = listShown.isEmpty(), onAny = { onAnyCategory(showIncome) })
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        SegmentedButton(
                            selected = !showIncome,
                            onClick = { showIncome = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("Expense") }
                        SegmentedButton(
                            selected = showIncome,
                            onClick = { showIncome = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Income") }
                    }
                }
                if (showIncome) {
                    optionList("income", options.income) { onToggleIncome(it.key) }
                } else {
                    optionList("expense", options.expense) { onToggleExpense(it.key) }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SectionHeader("Accounts", isAny = !draft.hasAccounts, onAny = onAnyAccount)
                }
                optionList("accounts", options.accounts) { onToggleAccount(it.key.toLong()) }
            }
            Button(
                onClick = onShow,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Text(if (options.matchCount == 1) "Show 1 transaction" else "Show ${options.matchCount} transactions")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, isAny: Boolean, onAny: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        // "Any" turns every choice in the section off; it's ticked while none is on.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // One toggle for TalkBack: "Any, checked".
            modifier = Modifier.toggleable(value = isAny, role = Role.Checkbox, onValueChange = { onAny() }).heightIn(min = 48.dp)
        ) {
            Checkbox(checked = isAny, onCheckedChange = null)
            Text("Any", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The used options, then the unused ones under "Not used this month (n)". */
private fun LazyListScope.optionList(key: String, section: FilterOptionsSection, onToggle: (FilterOptionUi) -> Unit) {
    items(section.used, key = { "$key-${it.key}" }) { OptionRow(it, onToggle) }
    if (section.unused.isNotEmpty()) {
        item(key = "$key-fold") {
            var expanded by remember { mutableStateOf(false) }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.heightIn(min = 48.dp)
                ) {
                    Text(
                        "Not used this month (${section.unused.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
                if (expanded) section.unused.forEach { OptionRow(it, onToggle) }
            }
        }
    }
}

@Composable
private fun OptionRow(option: FilterOptionUi, onToggle: (FilterOptionUi) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // One toggle for TalkBack: "Food, ₹1,400, checked".
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = option.isSelected, role = Role.Checkbox, onValueChange = { onToggle(option) })
            .heightIn(min = 48.dp)
    ) {
        Checkbox(checked = option.isSelected, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge)
            if (option.isLeftover) {
                Text(
                    "No longer in list",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(option.amount, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}
