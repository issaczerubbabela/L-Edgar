package com.sheetsync.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.viewmodel.StatsTimeframe
import com.sheetsync.viewmodel.StatsViewModel

@Composable
fun InsightsScreen(innerPadding: PaddingValues, vm: StatsViewModel = hiltViewModel()) {
    val filterState by vm.filterState.collectAsState()
    val accountGroupOptions by vm.accountGroupOptions.collectAsState()
    val filteredTransactions by vm.filteredTransactions.collectAsState()
    val expenseByCategory by vm.expenseByCategory.collectAsState()
    val cashFlowOverTime by vm.cashFlowOverTime.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Stats",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            FilterRow(
                selectedTimeframe = filterState.timeframe,
                selectedAccountGroup = filterState.accountGroupId,
                accountGroups = accountGroupOptions,
                onTimeframeSelected = vm::updateTimeframe,
                onAccountGroupSelected = vm::updateAccountGroup
            )
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (filteredTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Data for this period",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Expense Breakdown", style = MaterialTheme.typography.titleMedium)
                        ExpenseDonutChart(
                            categoryTotals = expenseByCategory,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Cash Flow", style = MaterialTheme.typography.titleMedium)
                        CashFlowBarChart(
                            cashFlowByPeriod = cashFlowOverTime,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun FilterRow(
    selectedTimeframe: StatsTimeframe,
    selectedAccountGroup: String?,
    accountGroups: List<String>,
    onTimeframeSelected: (StatsTimeframe) -> Unit,
    onAccountGroupSelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsTimeframe.entries.forEach { timeframe ->
                FilterChip(
                    selected = timeframe == selectedTimeframe,
                    onClick = { onTimeframeSelected(timeframe) },
                    label = { Text(timeframe.label()) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedAccountGroup == null,
                onClick = { onAccountGroupSelected(null) },
                label = { Text("All Accounts") }
            )

            accountGroups.forEach { group ->
                FilterChip(
                    selected = selectedAccountGroup == group,
                    onClick = { onAccountGroupSelected(group) },
                    label = { Text(group) }
                )
            }
        }
    }
}

private fun StatsTimeframe.label(): String {
    return when (this) {
        StatsTimeframe.THIS_MONTH -> "This Month"
        StatsTimeframe.LAST_MONTH -> "Last Month"
        StatsTimeframe.LAST_3_MONTHS -> "Last 3 Months"
        StatsTimeframe.YTD -> "YTD"
        StatsTimeframe.ALL_TIME -> "All Time"
    }
}
