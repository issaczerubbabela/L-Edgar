package com.issaczerubbabel.ledgar.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.viewmodel.TripReviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripReviewScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onPosted: () -> Unit,
    vm: TripReviewViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val posted by vm.posted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(posted) {
        posted?.let {
            Toast.makeText(context, "Posted $it transactions · trip archived", Toast.LENGTH_SHORT).show()
            onPosted()
        }
    }
    val accountOptions = accounts.map { it.id to it.accountName }
    val accountNames = accountOptions.toMap()
    val categoryOptions = categories.map { it to it }

    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Review & Post", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${s.included.size} transactions · ${rupees(s.totalPaise)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        TripContentWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    TripDropdown(
                        label = "Post from Account",
                        selectedText = s.defaultAccountId?.let { accountNames[it] }.orEmpty(),
                        options = accountOptions,
                        onSelect = vm::setDefaultAccount
                    )
                }
                items(s.rows, key = { it.tripExpenseId }) { row ->
                    TripCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = row.include, onCheckedChange = { vm.setInclude(row.tripExpenseId, it) })
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(row.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.payer?.let { MemberAvatar(it, 18.dp) }
                                    Text(
                                        "${dayLabel(row.date)} · of ${rupees(row.ofAmountPaise)} paid by ${row.payer?.name.orEmpty()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (row.include) {
                            OutlinedTextField(
                                value = row.amount,
                                onValueChange = { vm.setAmount(row.tripExpenseId, it) },
                                label = { Text(if (row.edited) "Your Share (edited)" else "Your Share") },
                                prefix = { Text("₹") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start),
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            )
                            TripDropdown(
                                label = "Category",
                                selectedText = row.category.ifEmpty { "Pick a Category" },
                                options = categoryOptions,
                                onSelect = { vm.setCategory(row.tripExpenseId, it) },
                                isError = row.category.isBlank(),
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            TripDropdown(
                                label = "Account",
                                selectedText = row.accountId?.let { accountNames[it] }.orEmpty(),
                                options = accountOptions,
                                onSelect = { vm.setAccount(row.tripExpenseId, it) },
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
                if (s.skippedCount > 0) {
                    item {
                        Text(
                            "${s.skippedCount} expense${if (s.skippedCount == 1) "" else "s"} where your Share is ₹0 won't be posted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                if (s.openPayments > 0) {
                    item {
                        Text(
                            "${s.openPayments} payment${if (s.openPayments == 1) "" else "s"} still open. Posting only records your Shares; until everyone settles, the account you paid from won't match your bank.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                if (s.missingCategory > 0) {
                    item {
                        Text(
                            "${s.missingCategory} row${if (s.missingCategory == 1) " needs" else "s need"} a Category before you can post.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                item {
                    Button(onClick = vm::post, enabled = s.canPost, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("Post ${s.included.size} & archive")
                    }
                }
            }
        }
    }
}
