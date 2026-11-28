package com.issaczerubbabel.ledgar.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.capture.source.BankEmails
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.components.PickerOption
import com.issaczerubbabel.ledgar.ui.theme.ExpenseRed
import com.issaczerubbabel.ledgar.ui.theme.IncomeGreen
import com.issaczerubbabel.ledgar.viewmodel.CaptureSettingsViewModel
import com.issaczerubbabel.ledgar.viewmodel.bankEmailStatus
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.hasNotificationAccess(): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

private fun humanize(canonical: String): String =
    canonical.replace('_', ' ').replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onNavigateToUnparsed: () -> Unit,
    vm: CaptureSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val unparsedCount by vm.unparsedCount.collectAsStateWithLifecycle()
    val readBankEmails by vm.readBankEmails.collectAsStateWithLifecycle()
    val lastBankEmailReadAt by vm.lastBankEmailReadAt.collectAsStateWithLifecycle()
    val bankEmailLabel by vm.bankEmailLabel.collectAsStateWithLifecycle()
    val mapping by vm.categoryMapping.collectAsStateWithLifecycle()
    val categories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    var mappingFor by remember { mutableStateOf<String?>(null) }

    // The user grants access on a system screen, so look again each time we come back.
    var hasAccess by remember { mutableStateOf(context.hasNotificationAccess()) }
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted || NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = context.hasNotificationAccess()
                notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Auto-capture") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Capture bank and UPI alerts", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Alerts become suggestions you confirm. Nothing is saved as a transaction until you do. Turning this off clears suggestions waiting for review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            vm.setEnabled(on)
                            if (on && !notificationsAllowed) askForNotifications()
                        }
                    )
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notification access", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hasAccess) "Granted" else "Needed to read bank and UPI alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasAccess) IncomeGreen else ExpenseRed
                        )
                    }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text(if (hasAccess) "Manage" else "Grant") }
                }
                Text(
                    "Reads only HDFC, Axis, City Union Bank, Google Pay, Paytm and Messages, and Gmail while Read bank emails is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                ReadBankEmails(
                    on = readBankEmails,
                    lastReadAt = lastBankEmailReadAt,
                    label = bankEmailLabel,
                    onChange = vm::setReadBankEmails,
                    onLabelChange = vm::setBankEmailLabel
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Confirm from a notification", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (notificationsAllowed) "On. Only a high-confidence capture with a known account asks. The lock screen hides the amount, and Confirm needs your phone unlocked."
                            else "Off. Allow notifications to confirm high-confidence captures without opening the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!notificationsAllowed) TextButton(onClick = ::askForNotifications) { Text("Allow") }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToUnparsed)
                        .padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alerts we couldn't read", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (unparsedCount == 0) "None. If a bank changes its wording, unreadable alerts show up here."
                            else "$unparsedCount waiting. Copy one to get its wording supported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unparsedCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else ExpenseRed
                        )
                    }
                    Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Categories", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Match the kinds of merchant auto-capture recognises, and cashback, to your own categories. Anything left unmapped shows up as needing a category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(vm.canonicalKeys.size) { index ->
                val key = vm.canonicalKeys[index]
                val mapped = mapping[key]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mappingFor = key }
                        .padding(vertical = 12.dp)
                ) {
                    Text(humanize(key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        mapped ?: "Not mapped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mapped == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    mappingFor?.let { key ->
        OptionPickerSheet(
            title = humanize(key),
            options = listOf(PickerOption("", "Not mapped")) +
                (if (vm.isIncomeKind(key)) incomeCategories else categories).map { PickerOption(it) },
            selectedKey = mapping[key] ?: "",
            onSelect = { vm.mapCategory(key, it.ifEmpty { null }); mappingFor = null },
            onDismiss = { mappingFor = null }
        )
    }
}

/**
 * The Read bank emails switch and, while it is on, the Gmail setup it needs: three steps, the
 * sender addresses with Copy, the label, and when a bank email was last read. One column, so TalkBack
 * reads the switch, step 1, the addresses, Copy, step 2, the label, step 3 and the status in that order.
 */
@Composable
private fun ReadBankEmails(
    on: Boolean,
    lastReadAt: Long?,
    label: String,
    onChange: (Boolean) -> Unit,
    onLabelChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Read bank emails", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Reads HDFC's and Amazon Pay's alert emails from Gmail's notifications. Emails from anyone else are ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = on, onCheckedChange = null)
        }
        if (on) {
            Column(
                modifier = Modifier.padding(top = 8.dp, start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Set up Gmail",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
                SetupStep("1. In Gmail on the web, search for these senders, then choose Create filter from the search options:")
                SenderAddresses()
                SetupStep("2. Choose Apply the label, pick or make a label, and create the filter. Only emails under this label are read:")
                LabelField(label, onLabelChange)
                SetupStep(
                    "3. In the Gmail app, open Settings, your account, Manage labels, then that label, and turn on " +
                        "Label notifications with Notify for every message."
                )
                Text(
                    bankEmailStatus(lastReadAt, ZoneId.systemDefault()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lastReadAt == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

/** The label typed here is kept as the user types; a bank email under any other label is ignored. */
@Composable
private fun LabelField(label: String, onLabelChange: (String) -> Unit) {
    var text by remember { mutableStateOf(label) }
    // The saved label arrives after the first frame; take it until the user starts typing.
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(label) { if (!edited) text = label }
    OutlinedTextField(
        value = text,
        onValueChange = {
            edited = true
            text = it
            onLabelChange(it)
        },
        label = { Text("Gmail label") },
        singleLine = true,
        isError = text.isBlank(),
        supportingText = if (text.isBlank()) ({ Text("Type the label your filter applies") }) else null,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SetupStep(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SenderAddresses() {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            BankEmails.FILTER_QUERY,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Bank alert senders", BankEmails.FILTER_QUERY)))
                    copied = true
                }
            },
            modifier = Modifier.semantics {
                contentDescription = if (copied) "Copied" else "Copy the sender addresses"
                liveRegion = LiveRegionMode.Polite
            }
        ) { Text(if (copied) "Copied" else "Copy") }
    }
}
