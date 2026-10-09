package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ui.components.TransactionRow
import com.issaczerubbabel.ledgar.ui.components.TransactionSheetHost
import com.issaczerubbabel.ledgar.viewmodel.BookmarksViewModel
import com.issaczerubbabel.ledgar.viewmodel.TransactionActionsViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onCopyTransaction: (id: Long, useToday: Boolean) -> Unit,
    vm: BookmarksViewModel = hiltViewModel(),
    actionsVm: TransactionActionsViewModel = hiltViewModel()
) {
    val transactions by vm.bookmarkedTransactions.collectAsStateWithLifecycle()
    val accounts by actionsVm.accounts.collectAsStateWithLifecycle()
    val rows = remember(transactions, accounts) { transactions.map { Ledger.row(it, accounts) } }
    var sheetTransactionId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        val combinedPadding = PaddingValues(
            start = 0.dp,
            top = scaffoldPadding.calculateTopPadding(),
            end = 0.dp,
            bottom = innerPadding.calculateBottomPadding()
        )

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(combinedPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No bookmarked transactions",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(combinedPadding)
            ) {
                items(rows, key = { it.id }) { row ->
                    TransactionRow(
                        row = row,
                        onClick = { sheetTransactionId = row.id },
                        trailing = {
                            IconButton(onClick = { vm.removeBookmark(row.id) }) {
                                Icon(Icons.Filled.Star, contentDescription = "Remove bookmark")
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }

    transactions.firstOrNull { it.id == sheetTransactionId }?.let { record ->
        TransactionSheetHost(
            transaction = record,
            onDismiss = { sheetTransactionId = null },
            onEdit = onEditTransaction,
            onCopy = onCopyTransaction,
            vm = actionsVm
        )
    }
}
