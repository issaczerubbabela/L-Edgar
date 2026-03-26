package com.sheetsync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.theme.ExpenseOrange
import com.sheetsync.ui.theme.IncomeBlue
import com.sheetsync.viewmodel.AccountDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: AccountDetailViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.accountName, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = vm::prevMonth) { Icon(Icons.Filled.ChevronLeft, null) }
                    Text(state.periodLabel, modifier = Modifier.padding(top = 14.dp))
                    IconButton(onClick = vm::nextMonth) { Icon(Icons.Filled.ChevronRight, null) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Statement", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.BarChart, null)
                    Spacer(Modifier.padding(4.dp))
                    Icon(Icons.Filled.Edit, null)
                }
                SummaryRow("Deposit", state.deposit, IncomeBlue)
                SummaryRow("Withdrawal", state.withdrawal, ExpenseOrange)
                SummaryRow("Total", state.total, MaterialTheme.colorScheme.onBackground)
                SummaryRow("Balance", state.currentBalance, if (state.currentBalance < 0) ExpenseOrange else MaterialTheme.colorScheme.onBackground)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }

            items(state.entries.size) { i ->
                val entry = state.entries[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(entry.category, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        Text(entry.description.ifBlank { "-" }, color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp)
                        Text(entry.paymentMode, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "₹ ${money(entry.amount)}",
                            color = if (entry.type == "Income" || (entry.type == "Transfer" && entry.amount > 0)) IncomeBlue else ExpenseOrange,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "(Balance ${money(entry.runningBalance)})",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("₹ ${money(amount)}", color = color, fontSize = 28.sp)
    }
}

private fun money(value: Double): String {
    val sign = if (value < 0) "-" else ""
    return sign + "%,.2f".format(kotlin.math.abs(value))
}
