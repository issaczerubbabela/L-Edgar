package com.sheetsync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeBlue
import com.sheetsync.viewmodel.AccountListItemUi
import com.sheetsync.viewmodel.AccountsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    innerPadding: PaddingValues,
    onAddAccount: () -> Unit,
    onOpenAccountDetail: (Long) -> Unit,
    vm: AccountsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
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
                                onAddAccount()
                            }
                        )
                        DropdownMenuItem(text = { Text("Show/Hide") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Modify Orders") }, onClick = { showMenu = false })
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                state.groupedAccounts.forEach { (groupName, accounts) ->
                    item { GroupHeader(groupName) }
                    items(accounts.size) { index ->
                        AccountRow(accounts[index], onOpenAccountDetail)
                    }
                }
            }
        }
    }
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
private fun AccountRow(item: AccountListItemUi, onOpen: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen(item.id) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.accountName,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "₹ ${money(item.balance)}",
            color = if (item.balance < 0) ExpenseRed else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

private fun money(value: Double): String {
    val sign = if (value < 0) "-" else ""
    return sign + "%,.2f".format(kotlin.math.abs(value))
}
