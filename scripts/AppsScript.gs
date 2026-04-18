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
  var type = String(row[2] || "")
    .trim()
    .toLowerCase();

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

  if (
    type === "transfer" &&
    (!fromAccountName || !toAccountName) &&
    accountName
  ) {
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

    if (target === "transactions" && action === "migrate") {
      return jsonOut(migrateTransactionsSheetToV2());
    }

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
      var combinedAccountName = String(
        r.accountName || r.paymentMode || "",
      ).trim();
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
      .getRange(
        1,
        1,
        1,
        Math.max(txSheet.getLastColumn(), TRANSACTION_HEADERS_V2.length),
      )
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
