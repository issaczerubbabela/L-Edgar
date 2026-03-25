package com.sheetsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.ui.components.TransactionCard
import com.sheetsync.viewmodel.HistoryViewModel
import java.time.Month

val MONTHS = Month.values().map { it.name.take(3) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(innerPadding: PaddingValues, vm: HistoryViewModel = hiltViewModel()) {
    val transactions by vm.transactions.collectAsState()
    val filterType by vm.filterType.collectAsState()
    val filterMonth by vm.filterMonth.collectAsState()
    val filterYear by vm.filterYear.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(top = 16.dp)
    ) {
        Text(
            "History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))

        // Type filter tabs
        TabRow(selectedTabIndex = listOf("All", "Expense", "Income").indexOf(filterType)) {
            listOf("All", "Expense", "Income").forEachIndexed { idx, label ->
                Tab(
                    selected = filterType == label,
                    onClick = { vm.setFilterType(label) },
                    text = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Month chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(MONTHS.size) { idx ->
                val monthNum = idx + 1
                FilterChip(
                    selected = filterMonth == monthNum,
                    onClick = { vm.setFilterMonth(monthNum) },
                    label = { Text(MONTHS[idx]) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No records found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.id }) { record ->
                    TransactionCard(record = record)
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}
