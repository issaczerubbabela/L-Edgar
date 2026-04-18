package com.issaczerubbabel.ledgar.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.viewmodel.ConnectionTestState
import com.issaczerubbabel.ledgar.viewmodel.SettingsViewModel

private const val SCRIPT_URL_PREFIX = "https://script.google.com/macros/s/"

private val APPS_SCRIPT_CODE = """
/**
 * SheetSync — Google Apps Script (Transactions + Dropdowns + Budgets + Accounts)
 *
 * Transaction Date Output Contract:
 * - doGet(target=transactions) must emit `date` as `yyyy-MM-dd`.
 * - Android import uses this for month grouping in the Trans/History tab.
 */

var TRANSACTIONS_SHEET = "_responses";
var DROPDOWNS_SHEET = "_dropdowns";
var BUDGETS_SHEET = "_budgets";
var ACCOUNTS_SHEET = "_accounts";

var TRANSACTION_HEADERS_V2 = [
  "Timestamp",
  "Date",
  "Type",
  "Exp Category",
  "Inc Category",
  "Description",
  "Amount",
  "Account Name",
  "From Account Name",
  "To Account Name",
  "Remarks",
  "Synced At",
  "Is Bookmarked",
];

function jsonOut(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(
    ContentService.MimeType.JSON,
  );
}

function toBool(value) {
  if (typeof value === "boolean") return value;
  return String(value).toLowerCase() === "true";
}

function ensureSheet(spreadsheet, name) {
  return spreadsheet.getSheetByName(name) || spreadsheet.insertSheet(name);
}

function parsePayload(e) {
  var raw = e && e.postData && e.postData.contents ? e.postData.contents : "{}";
  return JSON.parse(raw);
}

function normalizeTimestampKey(value, timeZone) {
  if (value === null || value === undefined) return "";
  var text = String(value).trim();
  if (!text) return "";

  if (/^\d{1,2}\/\d{1,2}\/\d{4}\s\d{2}:\d{2}:\d{2}$/.test(text)) {
    return text;
  }

  var cleaned = text.replace(/\s+\([^)]*\)$/, "");
  var parsed = new Date(cleaned);
  if (!isNaN(parsed.getTime())) {
    return Utilities.formatDate(parsed, timeZone, "M/d/yyyy HH:mm:ss");
  }

  return text;
}

function boolish(value) {
  if (value === true || value === false) return true;
  var text = String(value || "")
    .trim()
    .toLowerCase();
  return text === "true" || text === "false" || text === "1" || text === "0";
}

function detectTransactionSchemaMode(headerRow) {
  if (!headerRow || !headerRow.length) return "legacy";
  var h = headerRow.map(function (v) {
    return String(v || "").trim();
  });
  var isV2Header =
    h[8] === "From Account Name" &&
    h[9] === "To Account Name" &&
    h[10] === "Remarks";
  return isV2Header ? "v2" : "legacy";
}

function parseTransactionRow(row, schemaMode) {
  var accountName = String(row[7] || "");
  var type = String(row[2] || "").trim().toLowerCase();

  var normalizedMode = schemaMode || "legacy";

  var fromAccountName = "";
  var toAccountName = "";
  var remarks = "";
  var syncedAt = "";
  var isBookmarked = false;

  if (normalizedMode === "v2") {
    fromAccountName = String(row[8] || "");
    toAccountName = String(row[9] || "");
    remarks = String(row[10] || "");
    syncedAt = row[11] ? String(row[11]) : "";
    isBookmarked = row[12] ? toBool(row[12]) : false;
  } else {
    // Legacy 11-column format.
    remarks = String(row[8] || "");
    syncedAt = row[9] ? String(row[9]) : "";
    isBookmarked = row[10] ? toBool(row[10]) : false;
  }

  if (type === "transfer" && (!fromAccountName || !toAccountName) && accountName) {
    var legacySplit = accountName.split("->").map(function (part) {
      return String(part || "").trim();
    });
    if (!fromAccountName) fromAccountName = legacySplit[0] || "";
    if (!toAccountName) toAccountName = legacySplit[1] || "";
  }

  return {
    accountName: accountName,
    fromAccountName: fromAccountName,
    toAccountName: toAccountName,
    remarks: remarks,
    syncedAt: syncedAt,
    isBookmarked: isBookmarked,
  };
}

function migrateTransactionsSheetToV2() {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var txSheet = ensureSheet(spreadsheet, TRANSACTIONS_SHEET);

  if (txSheet.getLastRow() === 0) {
    txSheet
      .getRange(1, 1, 1, TRANSACTION_HEADERS_V2.length)
      .setValues([TRANSACTION_HEADERS_V2]);
    return {
      status: "ok",
      action: "migration_initialized",
      message: "Created v2 headers on empty transactions sheet",
      rowsAffected: 0,
    };
  }

  var header = txSheet
    .getRange(
      1,
      1,
      1,
      Math.max(txSheet.getLastColumn(), TRANSACTION_HEADERS_V2.length),
    )
    .getDisplayValues()[0]
    .map(function (v) {
      return String(v || "").trim();
    });

  var alreadyV2 =
    header[8] === "From Account Name" &&
    header[9] === "To Account Name" &&
    header[10] === "Remarks";

  if (!alreadyV2) {
    // Insert between "Account Name" and "Remarks" so old row values shift right safely.
    txSheet.insertColumnsAfter(8, 2);
  }

  txSheet
    .getRange(1, 1, 1, TRANSACTION_HEADERS_V2.length)
    .setValues([TRANSACTION_HEADERS_V2]);

  return {
    status: "ok",
    action: alreadyV2 ? "migration_noop" : "migration_applied",
    message: alreadyV2
      ? "Sheet is already using v2 transaction columns"
      : "Inserted From/To Account Name columns and updated header",
    rowsAffected: Math.max(txSheet.getLastRow() - 1, 0),
  };
}

function doPost(e) {
  try {
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var payload = parsePayload(e);
    var target = String(payload.target || "transactions").toLowerCase();
    var action = String(payload.action || "insert").toLowerCase();
    var records = payload.records || [];
    var allowEmptyBackup = toBool(payload.allowEmptyBackup);
    var timeZone = Session.getScriptTimeZone();

    if (
      action === "backup" &&
      (target === "accounts" ||
        target === "dropdowns" ||
        target === "budgets") &&
      records.length === 0 &&
      !allowEmptyBackup
    ) {
      return jsonOut({
        status: "ok",
        action: "backup_skipped",
        target: target,
        count: 0,
        message: "Skipped empty backup to prevent accidental sheet erase",
      });
    }

    // =============================================================
    // ACCOUNTS BACKUP (Updated with Current Balance)
    // =============================================================
    if (target === "accounts" && action === "backup") {
      var accountSheet = ensureSheet(spreadsheet, ACCOUNTS_SHEET);
      accountSheet.clear();

      var backupTime = Utilities.formatDate(
        new Date(),
        timeZone,
        "M/d/yyyy HH:mm:ss",
      );

      // Build batch of rows
      var rows = [
        [
          "Account ID",
          "Group",
          "Account Name",
          "Description",
          "Initial Balance",
          "Initial Balance Date",
          "Current Balance",
          "Is Hidden",
          "Include In Totals",
          "Display Order",
          "Last Backed Up",
        ],
      ];

      records.forEach(function (r) {
        var incTotals =
          r.includeInTotals !== undefined ? r.includeInTotals : true;
        rows.push([
          r.id,
          (r.groupName !== undefined ? r.groupName : r.group) || "",
          (r.accountName !== undefined ? r.accountName : r.name) || "",
          r.description || "",
          Number(r.initialBalance) || 0,
          r.initialBalanceDate || "",
          Number(
            r.currentBalance !== undefined
              ? r.currentBalance
              : r.initialBalance,
          ) || 0,
          toBool(r.isHidden),
          toBool(incTotals),
          Number(r.displayOrder) || 0,
          backupTime,
        ]);
      });

      // Batch insert all rows at once
      if (rows.length > 1) {
        accountSheet
          .getRange(1, 1, rows.length, rows[0].length)
          .setValues(rows);
      }

      return jsonOut({
        status: "ok",
        type: "accounts_backed_up",
        count: records.length,
      });
    }

    // =============================================================
    // DROPDOWNS & BUDGETS BACKUP
    // =============================================================
    if (
      (target === "dropdowns" || target === "budgets") &&
      action === "backup"
    ) {
      var sheetName = target === "dropdowns" ? DROPDOWNS_SHEET : BUDGETS_SHEET;
      var backupSheet = ensureSheet(spreadsheet, sheetName);
      backupSheet.clear();

      var backupAt = Utilities.formatDate(
        new Date(),
        timeZone,
        "M/d/yyyy HH:mm:ss",
      );

      // Build batch of rows
      var rows = [];
      if (target === "dropdowns") {
        rows.push([
          "ID",
          "Option Type",
          "Name",
          "Display Order",
          "Last Backed Up",
        ]);
      } else {
        rows.push(["ID", "MonthYear", "Category", "Amount", "Last Backed Up"]);
      }

      records.forEach(function (r) {
        if (target === "dropdowns") {
          rows.push([
            r.id,
            r.optionType,
            r.name,
            Number(r.displayOrder) || 0,
            backupAt,
          ]);
        } else {
          rows.push([
            r.id,
            r.monthYear,
            r.category,
            Number(r.amount) || 0,
            backupAt,
          ]);
        }
      });

      // Batch insert all rows at once
      if (rows.length > 1) {
        backupSheet.getRange(1, 1, rows.length, rows[0].length).setValues(rows);
      } else {
        // Header only
        backupSheet.getRange(1, 1, 1, rows[0].length).setValues(rows);
      }

      return jsonOut({
        status: "ok",
        type: target + "_backed_up",
        count: records.length,
      });
    }

    // =============================================================
    // TRANSACTIONS LOGIC
    // =============================================================
    var txSheet = ensureSheet(spreadsheet, TRANSACTIONS_SHEET);
    if (txSheet.getLastRow() === 0) {
      txSheet.appendRow(TRANSACTION_HEADERS_V2);
    }

    if (action === "delete") {
      var targetTimestamp = String(payload.targetTimestamp || "");
      if (!targetTimestamp)
        return jsonOut({
          status: "error",
          message: "targetTimestamp is required for delete",
        });

      var normalizedTargetTimestamp = normalizeTimestampKey(
        targetTimestamp,
        timeZone,
      );

      var displayData = txSheet.getDataRange().getDisplayValues();
      for (var i = displayData.length - 1; i >= 1; i--) {
        if (
          normalizeTimestampKey(displayData[i][0], timeZone) ===
          normalizedTargetTimestamp
        ) {
          txSheet.deleteRow(i + 1);
          return jsonOut({ status: "ok", action: "deleted", count: 1 });
        }
      }
      return jsonOut({ status: "ok", action: "deleted", count: 0 });
    }

    var now = new Date();
    var generatedTimestamp = Utilities.formatDate(
      now,
      timeZone,
      "M/d/yyyy HH:mm:ss",
    );
    var syncedAtIso = now.toISOString();

    records.forEach(function (r) {
      var txDateObj = new Date(r.date);
      var formattedTxDate = isNaN(txDateObj.getTime())
        ? String(r.date || "")
        : Utilities.formatDate(txDateObj, timeZone, "M/d/yyyy");
      var normalizedRecordTimestamp = normalizeTimestampKey(
        r.timestamp,
        timeZone,
      );

      var fromAccountName = String(r.fromAccountName || "").trim();
      var toAccountName = String(r.toAccountName || "").trim();
      var combinedAccountName = String(r.accountName || r.paymentMode || "").trim();
      if ((!fromAccountName || !toAccountName) && combinedAccountName) {
        var legacyParts = combinedAccountName.split("->").map(function (part) {
          return String(part || "").trim();
        });
        if (!fromAccountName) fromAccountName = legacyParts[0] || "";
        if (!toAccountName) toAccountName = legacyParts[1] || "";
      }

      var rowData = [
        normalizedRecordTimestamp || generatedTimestamp,
        formattedTxDate,
        r.type || "",
        r.expCategory || "",
        r.incCategory || "",
        r.description || "",
        Number(r.amount) || 0,
        combinedAccountName,
        fromAccountName,
        toAccountName,
        r.remarks || "",
        syncedAtIso,
        toBool(r.isBookmarked),
      ];

      if (action === "update") {
        var rows = txSheet.getDataRange().getDisplayValues();
        var found = false;
        for (var j = 1; j < rows.length; j++) {
          if (
            normalizeTimestampKey(rows[j][0], timeZone) ===
            normalizedRecordTimestamp
          ) {
            txSheet.getRange(j + 1, 1, 1, rowData.length).setValues([rowData]);
            found = true;
            break;
          }
        }
        if (!found) txSheet.appendRow(rowData);
      } else {
        txSheet.appendRow(rowData);
      }
    });

    return jsonOut({ status: "ok", count: records.length, action: action });
  } catch (err) {
    return jsonOut({ status: "error", message: err.message });
  }
}

function doGet(e) {
  try {
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var timeZone = Session.getScriptTimeZone();
    var target = String(
      (e && e.parameter && e.parameter.target) || "transactions",
    ).toLowerCase();

    // =============================================================
    // ACCOUNTS FETCH (Indices shifted to accommodate Current Balance)
    // =============================================================
    if (target === "accounts") {
      var accountSheet = spreadsheet.getSheetByName(ACCOUNTS_SHEET);
      if (!accountSheet) return jsonOut({ status: "ok", data: [] });
      var accountData = accountSheet.getDataRange().getValues();
      var accounts = [];
      for (var i = 1; i < accountData.length; i++) {
        accounts.push({
          id: Number(accountData[i][0]) || 0,
          groupName: String(accountData[i][1] || ""),
          accountName: String(accountData[i][2] || ""),
          description: String(accountData[i][3] || ""),
          initialBalance: Number(accountData[i][4]) || 0,
          initialBalanceDate: String(accountData[i][5] || ""),
          currentBalance: Number(accountData[i][6]) || 0, // <-- FETCH MAPPED
          isHidden: toBool(accountData[i][7]), // Index shifted from 6 -> 7
          includeInTotals:
            accountData[i][8] === "" ? true : toBool(accountData[i][8]), // Shifted 7 -> 8
          displayOrder: Number(accountData[i][9]) || 0, // Shifted 8 -> 9
        });
      }
      return jsonOut({ status: "ok", data: accounts });
    }

    if (target === "dropdowns") {
      var dropdownSheet = spreadsheet.getSheetByName(DROPDOWNS_SHEET);
      if (!dropdownSheet) return jsonOut({ status: "ok", data: [] });
      var dropdownData = dropdownSheet.getDataRange().getValues();
      var dropdowns = [];
      for (var d = 1; d < dropdownData.length; d++) {
        dropdowns.push({
          id: Number(dropdownData[d][0]) || 0,
          optionType: String(dropdownData[d][1] || ""),
          name: String(dropdownData[d][2] || ""),
          displayOrder: Number(dropdownData[d][3]) || 0,
        });
      }
      return jsonOut({ status: "ok", data: dropdowns });
    }

    if (target === "budgets") {
      var budgetSheet = spreadsheet.getSheetByName(BUDGETS_SHEET);
      if (!budgetSheet) return jsonOut({ status: "ok", data: [] });
      var budgetData = budgetSheet.getDataRange().getValues();
      var budgets = [];
      for (var b = 1; b < budgetData.length; b++) {
        budgets.push({
          id: Number(budgetData[b][0]) || 0,
          monthYear: String(budgetData[b][1] || ""),
          category: String(budgetData[b][2] || ""),
          amount: Number(budgetData[b][3]) || 0,
        });
      }
      return jsonOut({ status: "ok", data: budgets });
    }

    // =============================================================
    // TRANSACTIONS FETCH
    // =============================================================
    var txSheet = spreadsheet.getSheetByName(TRANSACTIONS_SHEET);
    if (!txSheet) return jsonOut({ status: "ok", count: 0, data: [] });

    var headerRow = txSheet
      .getRange(1, 1, 1, Math.max(txSheet.getLastColumn(), TRANSACTION_HEADERS_V2.length))
      .getDisplayValues()[0];
    var schemaMode = detectTransactionSchemaMode(headerRow);

    var txData = txSheet.getDataRange().getValues();
    var txRecords = [];
    for (var t = 1; t < txData.length; t++) {
      var row = txData[t];
      if (!row[2]) continue;
      var parsed = parseTransactionRow(row, schemaMode);

      txRecords.push({
        timestamp: row[0] ? normalizeTimestampKey(row[0], timeZone) : "",
        date: formatDate(row[1]),
        type: String(row[2] || ""),
        expCategory: String(row[3] || ""),
        incCategory: String(row[4] || ""),
        description: String(row[5] || ""),
        amount: Number(row[6]) || 0,
        accountName: parsed.accountName,
        fromAccountName: parsed.fromAccountName,
        toAccountName: parsed.toAccountName,
        remarks: parsed.remarks,
        syncedAt: parsed.syncedAt,
        isBookmarked: parsed.isBookmarked,
      });
    }

    return jsonOut({ status: "ok", count: txRecords.length, data: txRecords });
  } catch (err) {
    return jsonOut({ status: "error", message: err.message, data: [] });
  }
}

function formatDate(value) {
  if (!value) return "";
  if (value instanceof Date) {
    var y = value.getFullYear();
    var m = String(value.getMonth() + 1).padStart(2, "0");
    var d = String(value.getDate()).padStart(2, "0");
    return y + "-" + m + "-" + d;
  }
  var parsed = new Date(value);
  if (!isNaN(parsed.getTime())) {
    var py = parsed.getFullYear();
    var pm = String(parsed.getMonth() + 1).padStart(2, "0");
    var pd = String(parsed.getDate()).padStart(2, "0");
    return py + "-" + pm + "-" + pd;
  }
  return String(value);
}
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScriptSetupScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
  val configuredUrl by vm.scriptUrl.collectAsStateWithLifecycle()
  val connectionState by vm.connectionTestState.collectAsStateWithLifecycle()

    var urlInput by remember(configuredUrl) { mutableStateOf(configuredUrl.orEmpty()) }
    var showValidationError by remember { mutableStateOf(false) }

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Database Setup") },
                colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
                .padding(scaffoldPadding)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Step 1: The Code", style = MaterialTheme.typography.titleMedium)
            Text(
              "Deploy a private Google Apps Script as your sync backend. Copy this script and paste it into Apps Script.",
              style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = {
              clipboardManager.setText(AnnotatedString(APPS_SCRIPT_CODE))
              Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
            }) {
              Text("Copy Script Code")
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(6.dp))

            Text("Step 2: Deployment", style = MaterialTheme.typography.titleMedium)
            Text(
              "- Create a new Google Sheet\n- Go to Extensions > Apps Script\n- Paste the copied code\n- Click Deploy > New Deployment > Web App (Execute as: Me, Access: Anyone)\n- Copy the resulting Web App URL",
              style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/spreadsheets"))
              context.startActivity(intent)
            }) {
              Text("Open Google Sheets")
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(6.dp))

            Text("Step 3: Connect", style = MaterialTheme.typography.titleMedium)
            Text("Paste your deployed Web App URL and save it.", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
              value = urlInput,
              onValueChange = {
                urlInput = it
                showValidationError = false
                vm.resetConnectionTestState()
              },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              label = { Text("Web App URL") },
              isError = showValidationError,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            if (showValidationError) {
              Text(
                text = "URL must start with $SCRIPT_URL_PREFIX",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
              )
            }

            Button(
              onClick = {
                val normalized = urlInput.trim()
                val isValid = normalized.startsWith(SCRIPT_URL_PREFIX)
                if (!isValid) {
                  showValidationError = true
                  return@Button
                }
                vm.updateScriptUrl(normalized)
                Toast.makeText(context, "Connected", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.padding(top = 4.dp)
            ) {
              Text("Save & Connect")
            }

            OutlinedButton(
              onClick = {
                val normalized = urlInput.trim()
                val isValid = normalized.startsWith(SCRIPT_URL_PREFIX)
                if (!isValid) {
                  showValidationError = true
                  vm.resetConnectionTestState()
                  return@OutlinedButton
                }
                vm.testScriptConnection(normalized)
              }
            ) {
              Text("Test Connection")
            }

            when (val state = connectionState) {
              ConnectionTestState.Idle -> Unit
              ConnectionTestState.Testing -> Text(
                text = "Testing...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
              )
              ConnectionTestState.Success -> Text(
                text = "Connected: URL is reachable",
                color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                style = MaterialTheme.typography.bodySmall
              )
              is ConnectionTestState.Error -> Text(
                text = "Connection failed: ${state.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
              )
            }
        }
    }
}
