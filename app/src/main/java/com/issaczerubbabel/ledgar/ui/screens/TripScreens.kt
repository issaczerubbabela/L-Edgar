package com.issaczerubbabel.ledgar.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.TripRecord
import com.issaczerubbabel.ledgar.data.repository.TripListItem
import com.issaczerubbabel.ledgar.trip.PlannedPayment
import com.issaczerubbabel.ledgar.trip.TripMember
import com.issaczerubbabel.ledgar.trip.UpiLink
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.viewmodel.ActiveTripSummary
import com.issaczerubbabel.ledgar.viewmodel.ActiveTripViewModel
import com.issaczerubbabel.ledgar.viewmodel.TripViewModel
import com.issaczerubbabel.ledgar.viewmodel.TripsViewModel
import com.issaczerubbabel.ledgar.viewmodel.tripDateRange
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------------------------------------------------------------- Trips list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    vm: TripsViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val active = items.filter { !it.trip.isArchived }
    val archived = items.filter { it.trip.isArchived }

    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New trip") })
        }
    ) { padding ->
        TripContentWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (items.isEmpty()) {
                    item {
                        TripCard {
                            Text("Split a trip with friends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Log shared payments, see who owes whom, and post only your Share to your ledger.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                if (active.isNotEmpty()) {
                    item { SectionLabel("Active") }
                    items(active, key = { it.trip.id }) { TripListCard(it, onOpenTrip) }
                }
                if (archived.isNotEmpty()) {
                    item { SectionLabel("Archived") }
                    items(archived, key = { it.trip.id }) { TripListCard(it, onOpenTrip) }
                }
                item {
                    Text(
                        "Trip data stays on this phone. Only the Shares you post sync to your Sheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }

    if (creating) {
        NewTripDialog(onDismiss = { creating = false }) { name, start, end, members ->
            creating = false
            vm.createTrip(name, start, end, members, onOpenTrip)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun TripListCard(item: TripListItem, onOpenTrip: (Long) -> Unit) {
    TripCard(onClick = { onOpenTrip(item.trip.id) }) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.trip.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(tripDateRange(item.trip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Tag(if (item.trip.isArchived) "Archived" else "Active")
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
            AvatarStack(item.members, size = 32.dp, step = 22.dp)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${item.expenseCount} expense${if (item.expenseCount == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rupees(item.totalPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NewTripDialog(onDismiss: () -> Unit, onCreate: (String, String, String?, List<String>) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var end by rememberSaveable { mutableStateOf("") }
    var members by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { picking = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Starts $start") }
                OutlinedButton(onClick = { picking = 2 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (end.isEmpty()) "Ends (optional)" else "Ends $end") }
                OutlinedTextField(
                    members, { members = it },
                    label = { Text("Who else is going?") },
                    supportingText = { Text("Names separated by commas. You're added automatically.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, start, end.ifEmpty { null }, members.split(",")) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking != 0) {
        val initial = runCatching { LocalDate.parse(if (picking == 1) start else end.ifEmpty { start }) }.getOrDefault(LocalDate.now())
        SingleDatePickerDialog(
            initialDate = initial,
            onDismiss = { picking = 0 },
            onConfirm = { date -> if (picking == 1) start = date.toString() else end = date.toString(); picking = 0 }
        )
    }
}

// ---------------------------------------------------------------- Trans tab banner

@Composable
fun ActiveTripBanner(onOpenTrip: (Long) -> Unit, onAddExpense: (Long) -> Unit, vm: ActiveTripViewModel = hiltViewModel()) {
    val active by vm.active.collectAsStateWithLifecycle()
    active?.let { ActiveTripBannerContent(it, onOpenTrip, onAddExpense) }
}

@Composable
private fun ActiveTripBannerContent(trip: ActiveTripSummary, onOpenTrip: (Long) -> Unit, onAddExpense: (Long) -> Unit) {
    TripCard(
        // Compact on the Ledger, so the list keeps its room.
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = { onOpenTrip(trip.tripId) },
        contentPadding = PaddingValues(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(trip.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "${rupees(trip.totalPaise)} · ${trip.expenseCount} expense${if (trip.expenseCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = { onAddExpense(trip.tripId) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add trip expense", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

// ---------------------------------------------------------------- Trip screen

private val TabLabels = listOf("Summary", "Expenses", "Balances", "Settle up")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onReview: () -> Unit,
    vm: TripViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var justMe by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<TripSheet?>(null) }
    var confirmUnarchive by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    fun share(block: () -> Unit) {
        sheet = null
        try {
            block()
        } catch (e: Exception) {
            scope.launch { snackbar.showSnackbar("Couldn't share: ${e.message ?: "something went wrong"}") }
        }
    }

    val s = state
    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(s?.detail?.trip?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        s?.let {
                            Text(
                                tripDateRange(it.detail.trip) + if (it.isArchived) " · Archived" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (s != null) {
                        val names = s.detail.members.joinToString(", ") { it.name }
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Show people", role = Role.Button) { sheet = TripSheet.People }
                                .semantics { contentDescription = "People: $names" }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) { AvatarStack(s.detail.members, size = 28.dp, step = 18.dp, ring = MaterialTheme.colorScheme.background) }
                        IconButton(onClick = { sheet = TripSheet.Export }) { Icon(Icons.Filled.IosShare, contentDescription = "Export") }
                    }
                }
            )
        },
        floatingActionButton = {
            if ((tab == 0 || tab == 1) && s != null && !s.isArchived) {
                ExtendedFloatingActionButton(onClick = onAddExpense, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Expense") })
            }
        }
    ) { padding ->
        TripContentWidth {
            Column(Modifier.fillMaxSize().padding(padding)) {
                PillTabs(TabLabels, tab) { tab = it }
                if (s != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (tab) {
                            0 -> summaryItems(s, justMe, onJustMe = { justMe = it }, onOpenTab = { tab = it })
                            1 -> expensesItems(s, onEditExpense)
                            2 -> balancesItems(s, onOpenLedger = { sheet = TripSheet.Ledger(it) }, onPeople = { sheet = TripSheet.People })
                            else -> settleItems(
                                s,
                                onCheck = { sheet = TripSheet.Check },
                                onExplain = { sheet = TripSheet.Explain(it) },
                                onMarkPaid = vm::markPaid,
                                onUndo = vm::undoSettlement,
                                onPay = { openUpi(context, s.detail.members, it) },
                                onReview = onReview,
                                onUnarchive = { confirmUnarchive = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (s != null) {
        val close = { sheet = null }
        val tripName = s.detail.trip.name
        when (val current = sheet) {
            null -> Unit
            TripSheet.Check -> CheckSheet(s, close)
            is TripSheet.Explain -> ExplainSheet(s, current.payment, close)
            is TripSheet.Ledger -> LedgerSheet(s, current.memberId, close)
            TripSheet.People -> PeopleSheet(
                members = s.detail.members,
                readOnly = s.isArchived,
                onDismiss = close,
                onAdd = vm::addMember,
                onUpdate = vm::updateMember,
                onRemove = vm::removeMember
            )
            TripSheet.Export -> ExportSheet(
                onDismiss = close,
                onSummary = { share { vm.summaryText()?.let { shareTripText(context, "Share summary", it) } } },
                onStatement = { sheet = TripSheet.PickPerson },
                onExpensesCsv = { share { vm.expensesCsv()?.let { shareTripFile(context, safeFileName(tripName, "expenses.csv"), "text/csv", it) } } },
                onBalancesCsv = { share { vm.balancesCsv()?.let { shareTripFile(context, safeFileName(tripName, "balances.csv"), "text/csv", it) } } },
                onPdf = { share { vm.reportBlocks()?.let { shareTripPdf(context, safeFileName(tripName, "report.pdf"), it) } } }
            )
            TripSheet.PickPerson -> PickPersonSheet(
                members = s.detail.members,
                onPick = { member -> share { vm.statementText(member.id)?.let { shareTripText(context, "Statement for ${member.name}", it) } } },
                onDismiss = close
            )
        }
    }

    if (confirmUnarchive && s != null) {
        AlertDialog(
            onDismissRequest = { confirmUnarchive = false },
            title = { Text("Un-archive this trip?") },
            text = { Text("This deletes the ${s.detail.postedCount} transactions this trip posted, including any you edited since, so you can fix the trip and post again.") },
            confirmButton = { TextButton(onClick = { confirmUnarchive = false; vm.unarchive() }) { Text("Un-archive") } },
            dismissButton = { TextButton(onClick = { confirmUnarchive = false }) { Text("Cancel") } }
        )
    }
}

private fun openUpi(context: Context, members: List<TripMember>, payment: PlannedPayment) {
    val payee = members.firstOrNull { it.id == payment.toId } ?: return
    val vpa = payee.upiId ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpiLink.build(vpa, payee.name, payment.amountPaise))))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
    }
}
