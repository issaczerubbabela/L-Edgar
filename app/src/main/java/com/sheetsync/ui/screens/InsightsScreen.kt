package com.sheetsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeGreen
import com.sheetsync.viewmodel.InsightsViewModel

@Composable
fun InsightsScreen(innerPadding: PaddingValues, vm: InsightsViewModel = hiltViewModel()) {
    val summary by vm.summary.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("This Month", style = MaterialTheme.typography.headlineMedium) }

        // Summary cards row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Income", summary.totalIncome, IncomeGreen, Modifier.weight(1f))
                SummaryCard("Expense", summary.totalExpense, ExpenseRed, Modifier.weight(1f))
            }
        }
        item {
            val balanceColor = if (summary.balance >= 0) IncomeGreen else ExpenseRed
            SummaryCard("Balance", summary.balance, balanceColor, Modifier.fillMaxWidth())
        }

        // Category breakdown chart
        if (summary.categoryTotals.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("By Category", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        val values = summary.categoryTotals.values.map { it.toFloat() }
                        Chart(
                            chart = columnChart(),
                            model = entryModelOf(*values.toTypedArray()),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(),
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        summary.categoryTotals.entries.forEach { (cat, amt) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cat, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "₹${"%.0f".format(amt)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = ExpenseRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6-month trend
        if (summary.monthlyExpenses.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("6-Month Trend", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        val values = summary.monthlyExpenses.map { it.second.toFloat() }
                        Chart(
                            chart = columnChart(),
                            model = entryModelOf(*values.toTypedArray()),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(),
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
fun SummaryCard(title: String, amount: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "₹${"%.0f".format(kotlin.math.abs(amount))}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
    }
}
