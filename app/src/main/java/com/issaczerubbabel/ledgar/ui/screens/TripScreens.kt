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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.local.entity.TripRecord
import com.issaczerubbabel.ledgar.trip.PlannedPayment
import com.issaczerubbabel.ledgar.trip.SplitMode
import com.issaczerubbabel.ledgar.trip.TripMath
import com.issaczerubbabel.ledgar.trip.TripMember
import com.issaczerubbabel.ledgar.trip.UpiLink
import com.issaczerubbabel.ledgar.ui.components.SingleDatePickerDialog
import com.issaczerubbabel.ledgar.viewmodel.ActiveTripSummary
import com.issaczerubbabel.ledgar.viewmodel.ActiveTripViewModel
import com.issaczerubbabel.ledgar.viewmodel.TripExpenseViewModel
import com.issaczerubbabel.ledgar.viewmodel.TripReviewViewModel
import com.issaczerubbabel.ledgar.viewmodel.TripScreenState
import com.issaczerubbabel.ledgar.viewmodel.TripViewModel
import com.issaczerubbabel.ledgar.viewmodel.TripsViewModel
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")
private val shortFormat = DateTimeFormatter.ofPattern("d MMM")
private val owedColor = Color(0xFF2E9E6A)
private val owesColor = Color(0xFFD84A3A)

private fun rupees(paise: Long) = TripMath.rupees(paise)
private fun day(date: String) = runCatching { LocalDate.parse(date).format(dayFormat) }.getOrDefault(date)
private fun tripDates(trip: TripRecord): String {
    val start = runCatching { LocalDate.parse(trip.startDate).format(shortFormat) }.getOrDefault(trip.startDate)
    val end = trip.endDate?.let { runCatching { LocalDate.parse(it).format(shortFormat) }.getOrDefault(it) }
    return if (end == null) "From $start" else "$start – $end"
}

// ---------------------------------------------------------------- Trips list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    vm: TripsViewModel = hiltViewModel()
) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (trips.isEmpty()) {
                item {
                    Text(
                        "Start a trip to log shared payments, see who owes whom, and post only your Share to your ledger.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(trips, key = { it.id }) { trip ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenTrip(trip.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(trip.name, fontWeight = FontWeight.SemiBold)
                            Text(tripDates(trip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (trip.isArchived) "Archived" else "Active", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                Text(
                    "Trip data stays on this phone. Only the Shares you post sync to your Sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = 1 }, modifier = Modifier.weight(1f)) { Text(start) }
                    OutlinedButton(onClick = { picking = 2 }, modifier = Modifier.weight(1f)) { Text(end.ifEmpty { "End date" }) }
                }
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { onOpenTrip(trip.tripId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(trip.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${rupees(trip.totalPaise)} · ${trip.expenseCount} expense${if (trip.expenseCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = { onAddExpense(trip.tripId) }) { Icon(Icons.Filled.Add, contentDescription = "Add trip expense") }
        }
    }
}

// ---------------------------------------------------------------- Trip screen

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
    var managingMembers by rememberSaveable { mutableStateOf(false) }
    var confirmUnarchive by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val s = state
    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(s?.detail?.trip?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        s?.let {
                            Text(
                                it.detail.members.joinToString(", ") { m -> m.name } + if (it.isArchived) " · Archived" else "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { vm.summaryText()?.let { shareText(context, it) } }) { Icon(Icons.Filled.Share, contentDescription = "Share summary") }
                    IconButton(onClick = { vm.csvText()?.let { shareCsv(context, s?.detail?.trip?.name ?: "trip", it) } }) {
                        Icon(Icons.Filled.Download, contentDescription = "Export CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            if (tab == 0 && s != null && !s.isArchived) {
                ExtendedFloatingActionButton(onClick = onAddExpense, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Expense") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = tab) {
                listOf("Expenses", "Balances", "Settle up").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            if (s == null) return@Column
            when (tab) {
                0 -> ExpensesTab(s, onEditExpense)
                1 -> BalancesTab(s, onManageMembers = { managingMembers = true })
                else -> SettleUpTab(
                    s,
                    onMarkPaid = vm::markPaid,
                    onUndo = vm::undoSettlement,
                    onPay = { payment -> openUpi(context, s.detail.members, payment) },
                    onReview = onReview,
                    onUnarchive = { confirmUnarchive = true }
                )
            }
        }
    }

    if (managingMembers && s != null) {
        MembersDialog(s.detail.members, s.isArchived, onDismiss = { managingMembers = false }, onAdd = vm::addMember, onUpdate = vm::updateMember, onRemove = vm::removeMember)
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

@Composable
private fun ExpensesTab(s: TripScreenState, onEditExpense: (Long) -> Unit) {
    val names = s.detail.members.associate { it.id to it.name }
    val byDay = s.detail.expenses.sortedWith(compareBy({ it.date }, { it.id })).groupBy { it.date }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Trip total", style = MaterialTheme.typography.labelMedium)
                        Text(rupees(s.totalPaise), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Your Share", style = MaterialTheme.typography.labelMedium)
                        Text(rupees(s.myShareTotalPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (byDay.isEmpty()) item { Text("No expenses yet. Tap + Expense to log the first one.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        byDay.forEach { (date, expenses) ->
            item(key = "day-$date") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(day(date).uppercase(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(rupees(expenses.sumOf { it.amountPaise }), style = MaterialTheme.typography.labelMedium)
                }
            }
            items(expenses, key = { it.id }) { e ->
                val mine = s.myShares[e.id] ?: 0L
                val split = when (e.mode) {
                    SplitMode.EXACT -> "Exact · ${e.memberIds.size} people"
                    SplitMode.ADJUST -> "Adjusted · ${e.memberIds.size} people"
                    SplitMode.EQUAL -> if (e.memberIds.size == s.detail.members.size) "Equal · everyone" else "Equal · " + e.memberIds.joinToString(", ") { names[it].orEmpty() }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !s.isArchived) { onEditExpense(e.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.purpose, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            "${names[e.payerId]} paid · $split" + if (e.category.isBlank()) " · no Category" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (e.category.isBlank()) owesColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rupees(e.amountPaise), fontWeight = FontWeight.Medium)
                        Text(if (mine > 0) "you ${rupees(mine)}" else "not in it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BalancesTab(s: TripScreenState, onManageMembers: () -> Unit) {
    val names = s.detail.members.associate { it.id to it.name }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.balances, key = { it.memberId }) { b ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(names[b.memberId].orEmpty(), fontWeight = FontWeight.SemiBold)
                        Text(
                            "paid ${rupees(b.paidPaise)} · Share ${rupees(b.sharePaise)}" +
                                if (b.settledPaise != 0L) " · settled ${rupees(kotlin.math.abs(b.settledPaise))}" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val net = b.netPaise
                        Text(
                            (if (net > 0) "+" else if (net < 0) "−" else "") + rupees(kotlin.math.abs(net)),
                            color = if (net > 0) owedColor else if (net < 0) owesColor else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(if (net > 0) "gets back" else if (net < 0) "owes" else "settled", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Column {
                TextButton(onClick = onManageMembers) { Text(if (s.isArchived) "Members" else "Add or edit Members") }
                Text("Net = paid − Share ± Settlements. Positive means the group owes them.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettleUpTab(
    s: TripScreenState,
    onMarkPaid: (PlannedPayment) -> Unit,
    onUndo: (Long) -> Unit,
    onPay: (PlannedPayment) -> Unit,
    onReview: () -> Unit,
    onUnarchive: () -> Unit
) {
    val members = s.detail.members.associateBy { it.id }
    val selfId = s.detail.self?.id
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.plan.isEmpty()) {
            item { Text("Everyone is settled.", fontWeight = FontWeight.SemiBold) }
        } else {
            item { Text("${s.plan.size} payment${if (s.plan.size == 1) "" else "s"} settle everything", style = MaterialTheme.typography.bodySmall) }
            items(s.plan) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${members[p.fromId]?.name} → ${members[p.toId]?.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(rupees(p.amountPaise), fontWeight = FontWeight.SemiBold)
                        }
                        if (!s.isArchived) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (p.fromId == selfId && !members[p.toId]?.upiId.isNullOrBlank()) {
                                    TextButton(onClick = { onPay(p) }) { Text("Pay via UPI") }
                                }
                                TextButton(onClick = { onMarkPaid(p) }) { Text("Mark paid") }
                            }
                        }
                    }
                }
            }
        }
        if (s.detail.settlements.isNotEmpty()) {
            item { Text("Settlements", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(s.detail.settlements, key = { it.id }) { st ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${members[st.fromMemberId]?.name} paid ${members[st.toMemberId]?.name}", modifier = Modifier.weight(1f))
                    Text(rupees(st.amountPaise))
                    if (!s.isArchived) TextButton(onClick = { onUndo(st.id) }) { Text("Undo") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.isArchived) {
                        Text("Posted ${s.detail.postedCount} transactions", fontWeight = FontWeight.SemiBold)
                        Text("This trip is archived and read-only. Un-archiving deletes those transactions so you can fix the trip and post again.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onUnarchive) { Text("Un-archive") }
                    } else {
                        Text("Post your Shares", fontWeight = FontWeight.SemiBold)
                        Text("Adds your ${rupees(s.myShareTotalPaise)} to your ledger as one transaction per expense, then archives the trip.", style = MaterialTheme.typography.bodySmall)
                        if (s.plan.isNotEmpty()) {
                            Text("${s.plan.size} payment${if (s.plan.size == 1) "" else "s"} still open. You can post anyway; posting only records your Shares.", style = MaterialTheme.typography.bodySmall, color = owesColor)
                        }
                        Button(onClick = onReview, enabled = s.detail.expenses.isNotEmpty()) { Text("Review & Post") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersDialog(
    members: List<TripMember>,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (Long, String, String?) -> Unit,
    onRemove: (Long) -> Unit
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<TripMember?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Members") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                members.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !readOnly) { editing = m }.padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name)
                            Text(m.upiId ?: "No UPI ID", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!readOnly) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newName, { newName = it }, label = { Text("Add someone") }, singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onAdd(newName); newName = "" }, enabled = newName.isNotBlank()) { Icon(Icons.Filled.Add, contentDescription = "Add") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
    editing?.let { m ->
        var name by remember(m.id) { mutableStateOf(m.name) }
        var upi by remember(m.id) { mutableStateOf(m.upiId.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit ${m.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!m.isSelf) OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(upi, { upi = it }, label = { Text("UPI ID") }, placeholder = { Text("name@okaxis") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { onUpdate(m.id, name, upi); editing = null }) { Text("Save") } },
            dismissButton = {
                if (!m.isSelf) TextButton(onClick = { onRemove(m.id); editing = null }) { Text("Remove", color = owesColor) }
            }
        )
    }
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(send, "Share summary"))
}

private fun shareCsv(context: Context, tripName: String, csv: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, tripName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "trip" } + ".csv")
    file.writeText(csv)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export CSV"))
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

// ---------------------------------------------------------------- Trip expense form

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripExpenseScreen(
    innerPadding: PaddingValues,
    onDone: () -> Unit,
    vm: TripExpenseViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) onDone() }
    val detail = s.detail

    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (s.isEditing) "Edit expense" else "Trip expense")
                        Text(detail?.trip?.name.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                actions = { if (s.isEditing) TextButton(onClick = vm::delete) { Text("Delete", color = owesColor) } }
            )
        }
    ) { padding ->
        if (detail == null) return@Scaffold
        val names = detail.members.associate { it.id to it.name }
        val shares = s.shares
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    s.form.amount, vm::setAmount,
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(s.form.purpose, vm::setPurpose, label = { Text("Purpose") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Paid by", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.members.forEach { m ->
                        FilterChip(selected = s.form.payerId == m.id, onClick = { vm.setPayer(m.id) }, label = { Text(m.name) })
                    }
                }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Split", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(SplitMode.EQUAL to "Equal", SplitMode.ADJUST to "Adjust", SplitMode.EXACT to "Exact")
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.form.mode == mode,
                            onClick = { vm.setMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size)
                        ) { Text(label) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.members.forEach { m ->
                        FilterChip(selected = m.id in s.form.memberIds, onClick = { vm.toggleMember(m.id) }, label = { Text(m.name) })
                    }
                }
                }
            }
            items(detail.members.filter { it.id in s.form.memberIds }, key = { "share-${it.id}" }) { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(names[m.id].orEmpty(), modifier = Modifier.weight(1f))
                    when (s.form.mode) {
                        SplitMode.EQUAL -> Unit
                        SplitMode.ADJUST -> OutlinedTextField(
                            s.form.inputs[m.id].orEmpty(), { vm.setInput(m.id, it) },
                            label = { Text("+ ₹") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp)
                        )
                        SplitMode.EXACT -> OutlinedTextField(
                            s.form.inputs[m.id].orEmpty(), { vm.setInput(m.id, it) },
                            label = { Text("₹") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                    if (s.form.mode != SplitMode.EXACT) {
                        Spacer(Modifier.width(12.dp))
                        Text(rupees(shares[m.id] ?: 0L), modifier = Modifier.width(84.dp))
                    }
                }
            }
            item {
                CategoryField(
                    value = s.form.category,
                    options = s.categories,
                    label = "Category (optional now, needed to post)",
                    allowNone = true,
                    onSelect = vm::setCategory
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) { Text("Date: ${day(s.form.date)}") }
                if (s.outsideTrip) {
                    Text("This date is outside the trip. Save anyway if that's right.", style = MaterialTheme.typography.bodySmall, color = owesColor)
                }
                if (s.fromCapture) {
                    Text("From an auto-capture alert. It leaves the inbox when you save.", style = MaterialTheme.typography.bodySmall)
                }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val problem = s.problem
                    Text(
                        problem ?: "Your Share: ${rupees(detail.self?.let { shares[it.id] } ?: 0L)}",
                        color = if (problem != null) owesColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = vm::save, enabled = problem == null) { Text("Save") }
                }
            }
        }
    }

    if (pickingDate) {
        SingleDatePickerDialog(
            initialDate = runCatching { LocalDate.parse(s.form.date) }.getOrDefault(LocalDate.now()),
            onDismiss = { pickingDate = false },
            onConfirm = { vm.setDate(it.toString()); pickingDate = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(value: String, options: List<String>, label: String, allowNone: Boolean, onSelect: (String) -> Unit, isError: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value.ifEmpty { if (allowNone) "None yet" else "Pick Category" },
            onValueChange = {},
            readOnly = true,
            isError = isError,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("None yet") }, onClick = { onSelect(""); open = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false }) }
        }
    }
}

// ---------------------------------------------------------------- Review & Post

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
    val accountNames = accounts.associate { it.id to it.accountName }

    Scaffold(
        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Review & Post")
                        Text("${s.included.size} transactions · ${rupees(s.totalPaise)}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PickerField(
                    label = "Post from Account",
                    value = s.defaultAccountId?.let { accountNames[it] }.orEmpty(),
                    options = accounts.map { it.id to it.accountName },
                    onSelect = vm::setDefaultAccount
                )
            }
            items(s.rows, key = { it.tripExpenseId }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = row.include, onCheckedChange = { vm.setInclude(row.tripExpenseId, it) })
                            Column(Modifier.weight(1f)) {
                                Text(row.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text("${day(row.date)} · of ${rupees(row.ofAmountPaise)} paid by ${row.payerName}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                row.amount, { vm.setAmount(row.tripExpenseId, it) },
                                singleLine = true,
                                enabled = row.include,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(104.dp)
                            )
                        }
                        if (row.include) {
                            CategoryField(
                                value = row.category,
                                options = categories,
                                label = "Category",
                                allowNone = false,
                                isError = row.category.isBlank(),
                                onSelect = { vm.setCategory(row.tripExpenseId, it) }
                            )
                            PickerField(
                                label = "Account",
                                value = row.accountId?.let { accountNames[it] }.orEmpty(),
                                options = accounts.map { it.id to it.accountName },
                                onSelect = { vm.setAccount(row.tripExpenseId, it) }
                            )
                        }
                        if (row.edited) Text("Amount edited", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (s.skippedCount > 0) {
                item { Text("${s.skippedCount} expense${if (s.skippedCount == 1) "" else "s"} where your Share is ₹0 won't be posted.", style = MaterialTheme.typography.bodySmall) }
            }
            if (s.openPayments > 0) {
                item { Text("${s.openPayments} payment${if (s.openPayments == 1) "" else "s"} still open. Posting only records your Shares; until everyone settles, the account you paid from won't match your bank.", style = MaterialTheme.typography.bodySmall) }
            }
            if (s.missingCategory > 0) {
                item { Text("${s.missingCategory} row${if (s.missingCategory == 1) " needs" else "s need"} a Category before you can post.", color = owesColor, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Button(onClick = vm::post, enabled = s.canPost) { Text("Post ${s.included.size} & archive") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(label: String, value: String, options: List<Pair<Long, String>>, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false }) }
        }
    }
}
