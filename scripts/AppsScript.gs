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
var CYCLES_SHEET = "_cycles";
var BUCKETS_SHEET = "_buckets";
var BUCKET_CATEGORIES_SHEET = "_bucket_categories";

// Column indices (0-based) for TRANSACTION_HEADERS_V2
var COL_TIMESTAMP       = 0;
var COL_DATE            = 1;
var COL_TYPE            = 2;
var COL_EXP_CATEGORY    = 3;
var COL_INC_CATEGORY    = 4;
var COL_DESCRIPTION     = 5;  // <-- description lives here
var COL_AMOUNT          = 6;
var COL_ACCOUNT_NAME    = 7;
var COL_FROM_ACCOUNT    = 8;
var COL_TO_ACCOUNT      = 9;
var COL_REMARKS         = 10;
var COL_SYNCED_AT       = 11;
var COL_IS_BOOKMARKED   = 12;

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

function normalizeBudgetMonthYearValue(value, timeZone) {
  if (value === null || value === undefined) return "";
  var text = String(value).trim();
  if (!text) return "";

  if (/^\d{4}-\d{2}$/.test(text)) {
    return text;
  }

  var parsed = new Date(text);
  if (!isNaN(parsed.getTime())) {
    return Utilities.formatDate(parsed, timeZone, "yyyy-MM");
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

/**
 * Ensures the transactions sheet has the correct V2 headers.
 * Called on every insert/update so a legacy sheet is silently upgraded
 * (adds missing columns) without disturbing existing data rows.
 */
function ensureTransactionSheetHeaders(txSheet) {
  var lastCol = txSheet.getLastColumn();
  var lastRow = txSheet.getLastRow();

  // Empty sheet — write headers and return.
  if (lastRow === 0) {
    txSheet
      .getRange(1, 1, 1, TRANSACTION_HEADERS_V2.length)
      .setValues([TRANSACTION_HEADERS_V2]);
    return;
  }

  // Read existing header row.
  var headerRange = txSheet.getRange(1, 1, 1, Math.max(lastCol, TRANSACTION_HEADERS_V2.length));
  var existingHeaders = headerRange.getDisplayValues()[0];

  // Check if Description column is missing or shifted.
  var hasDescriptionAtCorrectPos = String(existingHeaders[COL_DESCRIPTION] || "").trim() === "Description";
  if (hasDescriptionAtCorrectPos) return; // Already correct — nothing to do.

  // Overwrite only the header row with the canonical V2 headers.
  // This does NOT touch data rows.
  if (txSheet.getMaxColumns() < TRANSACTION_HEADERS_V2.length) {
    txSheet.insertColumnsAfter(
      txSheet.getMaxColumns(),
      TRANSACTION_HEADERS_V2.length - txSheet.getMaxColumns()
    );
  }
  txSheet
    .getRange(1, 1, 1, TRANSACTION_HEADERS_V2.length)
    .setValues([TRANSACTION_HEADERS_V2]);
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

    // Defensive read for rows that were previously shifted by two columns.
    var looksShiftedByTwo =
      row.length >= 15 &&
      !String(row[8] || "").trim() &&
      !String(row[9] || "").trim() &&
      (String(row[10] || "").trim() ||
        String(row[11] || "").trim() ||
        String(row[12] || "").trim() ||
        String(row[13] || "").trim() ||
        String(row[14] || "").trim());
    if (looksShiftedByTwo) {
      fromAccountName = String(row[10] || "");
      toAccountName = String(row[11] || "");
      remarks = String(row[12] || "");
      syncedAt = row[13] ? String(row[13]) : "";
      isBookmarked = row[14] ? toBool(row[14]) : false;
    }
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

  // Normalize non-transfer rows so app import uses Account Name only.
  if (type !== "transfer") {
    if (!accountName && fromAccountName) {
      accountName = String(fromAccountName || "").trim();
    }
    fromAccountName = "";
    toAccountName = "";
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

function splitTransferAccounts(accountName) {
  var combined = String(accountName || "").trim();
  if (!combined) {
    return { fromAccountName: "", toAccountName: "" };
  }

  var parts = combined.split("->").map(function (part) {
    return String(part || "").trim();
  });

  return {
    fromAccountName: parts[0] || "",
    toAccountName: parts[1] || "",
  };
}

function createTransactionsBackupSheet(spreadsheet, txSheet) {
  var stamp = Utilities.formatDate(
    new Date(),
    Session.getScriptTimeZone(),
    "yyyyMMdd_HHmmss",
  );
  var baseName = TRANSACTIONS_SHEET + "_backup_" + stamp;
  var backupName = baseName;
  var suffix = 1;
  while (spreadsheet.getSheetByName(backupName)) {
    backupName = baseName + "_" + suffix;
    suffix++;
  }

  txSheet.copyTo(spreadsheet).setName(backupName);
  return backupName;
}

function normalizeTransactionRowToV2(row) {
  var rawType = String(row[2] || "").trim();
  var type = rawType.toLowerCase();

  var timestamp = String(row[0] || "");
  var date = String(row[1] || "");
  var expCategory = String(row[3] || "");
  var incCategory = String(row[4] || "");
  var description = String(row[5] || "");
  var amount = Number(row[6]) || 0;
  var accountName = String(row[7] || "").trim();

  var fromAccountName = String(row[8] || "").trim();
  var toAccountName = String(row[9] || "").trim();
  var remarks = String(row[10] || "");
  var syncedAt = row[11] ? String(row[11]) : "";
  var isBookmarkedRaw = row[12];

  var looksShiftedByTwo =
    row.length >= 15 &&
    !String(row[8] || "").trim() &&
    !String(row[9] || "").trim() &&
    (String(row[10] || "").trim() ||
      String(row[11] || "").trim() ||
      String(row[12] || "").trim() ||
      String(row[13] || "").trim() ||
      String(row[14] || "").trim());

  if (looksShiftedByTwo) {
    fromAccountName = String(row[10] || "").trim();
    toAccountName = String(row[11] || "").trim();
    remarks = String(row[12] || "");
    syncedAt = row[13] ? String(row[13]) : "";
    isBookmarkedRaw = row[14];
  }

  if (type === "transfer") {
    if (!fromAccountName || !toAccountName) {
      var split = splitTransferAccounts(accountName);
      if (!fromAccountName) fromAccountName = split.fromAccountName;
      if (!toAccountName) toAccountName = split.toAccountName;
    }
  } else {
    if (!accountName && fromAccountName) {
      accountName = fromAccountName;
    }
    fromAccountName = "";
    toAccountName = "";
  }

  var isBookmarked =
    isBookmarkedRaw === null ||
    isBookmarkedRaw === undefined ||
    String(isBookmarkedRaw).trim() === ""
      ? false
      : toBool(isBookmarkedRaw);

  return {
    row: [
      timestamp,
      date,
      rawType,
      expCategory,
      incCategory,
      description,
      amount,
      accountName,
      fromAccountName,
      toAccountName,
      remarks,
      syncedAt,
      isBookmarked,
    ],
    wasShiftedByTwo: looksShiftedByTwo,
    transferBackfilled:
      type === "transfer" && (fromAccountName !== "" || toAccountName !== ""),
    clearedNonTransferFromTo: type !== "transfer",
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
      backupSheet: null,
    };
  }
  var lastRow = txSheet.getLastRow();
  var lastColumn = txSheet.getLastColumn();
  var backupSheet = createTransactionsBackupSheet(spreadsheet, txSheet);

  var sourceRows = txSheet
    .getRange(
      1,
      1,
      lastRow,
      Math.max(lastColumn, TRANSACTION_HEADERS_V2.length),
    )
    .getDisplayValues();

  var normalizedRows = [TRANSACTION_HEADERS_V2];
  var shiftedRowsFixed = 0;
  var transferRowsBackfilled = 0;
  var nonTransferRowsCleared = 0;

  for (var i = 1; i < sourceRows.length; i++) {
    var source = sourceRows[i];
    var type = String(source[2] || "").trim();
    if (!type) continue;

    var normalized = normalizeTransactionRowToV2(source);
    normalizedRows.push(normalized.row);
    if (normalized.wasShiftedByTwo) shiftedRowsFixed++;
    if (normalized.transferBackfilled) transferRowsBackfilled++;
    if (normalized.clearedNonTransferFromTo) nonTransferRowsCleared++;
  }

  var maxColumns = txSheet.getMaxColumns();
  if (maxColumns < TRANSACTION_HEADERS_V2.length) {
    txSheet.insertColumnsAfter(
      maxColumns,
      TRANSACTION_HEADERS_V2.length - maxColumns,
    );
  } else if (maxColumns > TRANSACTION_HEADERS_V2.length) {
    txSheet.deleteColumns(
      TRANSACTION_HEADERS_V2.length + 1,
      maxColumns - TRANSACTION_HEADERS_V2.length,
    );
  }

  txSheet.clearContents();
  txSheet
    .getRange(1, 1, normalizedRows.length, TRANSACTION_HEADERS_V2.length)
    .setValues(normalizedRows);

  return {
    status: "ok",
    action: "migration_rewritten",
    message:
      "Created backup sheet and rewrote transactions into canonical v2 columns",
    rowsAffected: Math.max(normalizedRows.length - 1, 0),
    transferRowsBackfilled: transferRowsBackfilled,
    shiftedRowsFixed: shiftedRowsFixed,
    nonTransferRowsCleared: nonTransferRowsCleared,
    backupSheet: backupSheet,
  };
}

// =============================================================
// BUCKET BUDGETS (salary cycles, buckets and their categories)
// Sent and returned as nested cycles -> buckets -> categories, and stored as three flat sheets.
// =============================================================

/**
 * Writes one table, replacing whatever the sheet held. Columns in textColumns are formatted as
 * plain text BEFORE the values go in: otherwise Sheets turns ISO dates into Date cells and parses
 * any note or category name that starts with "=" as a formula.
 */
function writeTable(spreadsheet, name, headers, rows, textColumns) {
  var sheet = ensureSheet(spreadsheet, name);
  sheet.clear();
  var height = rows.length + 1;
  textColumns.forEach(function (col) {
    sheet.getRange(1, col, height, 1).setNumberFormat("@");
  });
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  if (rows.length > 0) {
    sheet.getRange(2, 1, rows.length, headers.length).setValues(rows);
  }
}

function backupBucketBudgets(spreadsheet, cycles, timeZone) {
  var backupAt = Utilities.formatDate(
    new Date(),
    timeZone,
    "M/d/yyyy HH:mm:ss",
  );
  var cycleRows = [];
  var bucketRows = [];
  var categoryRows = [];

  cycles.forEach(function (c) {
    cycleRows.push([
      c.id,
      c.startDate || "",
      c.endDate || "",
      Number(c.spendableAmount) || 0,
      c.closedAt || "",
      backupAt,
    ]);
    (c.buckets || []).forEach(function (b) {
      bucketRows.push([
        b.id,
        c.id,
        b.name || "",
        b.note || "",
        Number(b.colorIndex) || 0,
        b.emoji || "",
        Number(b.allocatedAmount) || 0,
        Number(b.sortOrder) || 0,
      ]);
      (b.categories || []).forEach(function (category) {
        categoryRows.push([c.id, b.id, String(category)]);
      });
    });
  });

  writeTable(
    spreadsheet,
    CYCLES_SHEET,
    ["Cycle ID", "Start Date", "End Date", "Spendable Amount", "Closed At", "Last Backed Up"],
    cycleRows,
    [2, 3, 5, 6],
  );
  writeTable(
    spreadsheet,
    BUCKETS_SHEET,
    ["Bucket ID", "Cycle ID", "Name", "Note", "Color Index", "Emoji", "Allocated Amount", "Sort Order"],
    bucketRows,
    [3, 4, 6],
  );
  writeTable(
    spreadsheet,
    BUCKET_CATEGORIES_SHEET,
    ["Cycle ID", "Bucket ID", "Category"],
    categoryRows,
    [3],
  );
  return cycles.length;
}

// A plain yyyy-MM-dd string is returned as it is. formatDate would parse it as UTC midnight and
// then read the local day, which is the previous day anywhere behind UTC.
function isoDateValue(value) {
  if (
    typeof value === "string" &&
    value.trim().length === 10 &&
    /^\d{4}-\d{2}-\d{2}/.test(value.trim())
  ) {
    return value.trim();
  }
  return formatDate(value);
}

function readTableRows(spreadsheet, name) {
  var sheet = spreadsheet.getSheetByName(name);
  if (!sheet) return [];
  return sheet.getDataRange().getValues().slice(1);
}

function readBucketBudgets(spreadsheet) {
  var categoriesByBucket = {};
  readTableRows(spreadsheet, BUCKET_CATEGORIES_SHEET).forEach(function (r) {
    var category = String(r[2] || "");
    if (!category) return;
    var key = (Number(r[0]) || 0) + ":" + (Number(r[1]) || 0);
    (categoriesByBucket[key] = categoriesByBucket[key] || []).push(category);
  });

  var bucketsByCycle = {};
  readTableRows(spreadsheet, BUCKETS_SHEET).forEach(function (r) {
    var bucketId = Number(r[0]) || 0;
    var cycleId = Number(r[1]) || 0;
    (bucketsByCycle[cycleId] = bucketsByCycle[cycleId] || []).push({
      id: bucketId,
      name: String(r[2] || ""),
      note: String(r[3] || ""),
      colorIndex: Number(r[4]) || 0,
      emoji: String(r[5] || ""),
      allocatedAmount: Number(r[6]) || 0,
      sortOrder: Number(r[7]) || 0,
      categories: categoriesByBucket[cycleId + ":" + bucketId] || [],
    });
  });

  var cycles = [];
  readTableRows(spreadsheet, CYCLES_SHEET).forEach(function (r) {
    var cycleId = Number(r[0]) || 0;
    var startDate = r[1] ? isoDateValue(r[1]) : "";
    var endDate = r[2] ? isoDateValue(r[2]) : "";
    if (!startDate || !endDate) return;
    cycles.push({
      id: cycleId,
      startDate: startDate,
      endDate: endDate,
      spendableAmount: Number(r[3]) || 0,
      closedAt: r[4] ? isoDateValue(r[4]) : null,
      buckets: bucketsByCycle[cycleId] || [],
    });
  });
  return cycles;
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

    // =============================================================
    // DESCRIPTION REPAIR
    // Patches only the Description cell of existing rows matched by
    // Timestamp. Safe — no other column is touched.
    // Payload: { target: "transactions", action: "description_repair",
    //            records: [{ timestamp: "M/d/yyyy HH:mm:ss", description: "..." }, ...] }
    // =============================================================
    if (target === "transactions" && action === "description_repair") {
      var txSheetRepair = ensureSheet(spreadsheet, TRANSACTIONS_SHEET);
      if (txSheetRepair.getLastRow() < 2) {
        return jsonOut({
          status: "ok",
          action: "description_repair",
          updated: 0,
          notFound: records.length,
          message: "Sheet has no data rows",
        });
      }

      // Build a map: normalizedTimestamp -> 1-based sheet row index
      var sheetData = txSheetRepair.getDataRange().getDisplayValues();
      var tsToRowIndex = {};
      for (var ri = 1; ri < sheetData.length; ri++) {
        var sheetTs = normalizeTimestampKey(sheetData[ri][COL_TIMESTAMP], timeZone);
        if (sheetTs) tsToRowIndex[sheetTs] = ri + 1; // +1 because getRange is 1-based
      }

      var repairUpdated = 0;
      var repairNotFound = 0;
      records.forEach(function (r) {
        var incomingTs = normalizeTimestampKey(r.timestamp, timeZone);
        if (!incomingTs) { repairNotFound++; return; }
        var sheetRowNum = tsToRowIndex[incomingTs];
        if (!sheetRowNum) { repairNotFound++; return; }
        // Only write the Description cell (col COL_DESCRIPTION+1 in 1-based)
        txSheetRepair
          .getRange(sheetRowNum, COL_DESCRIPTION + 1)
          .setValue(String(r.description || ""));
        repairUpdated++;
      });

      return jsonOut({
        status: "ok",
        action: "description_repair",
        updated: repairUpdated,
        notFound: repairNotFound,
      });
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
    if (target === "bucket_budgets" && action === "backup") {
      // Cycles travel in payload.cycles, and records stays empty on purpose: a script deployed
      // before this target existed treats records as transactions and would append them to the
      // transaction sheet. With records empty that older script does nothing.
      var bucketCycles = payload.cycles || [];
      if (bucketCycles.length === 0 && !allowEmptyBackup) {
        return jsonOut({
          status: "ok",
          action: "backup_skipped",
          type: "bucket_budgets_backed_up",
          target: target,
          count: 0,
          message: "Skipped empty backup to prevent accidental sheet erase",
        });
      }
      return jsonOut({
        status: "ok",
        type: "bucket_budgets_backed_up",
        count: backupBucketBudgets(spreadsheet, bucketCycles, timeZone),
      });
    }

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
            normalizeBudgetMonthYearValue(r.monthYear, timeZone),
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
    // Ensure header row is always correct V2 (adds Description column if legacy sheet).
    ensureTransactionSheetHeaders(txSheet);

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

    if (target === "bucket_budgets") {
      // "type" tells the app this script understands the target; an older script would fall
      // through to the transaction list, which has no such marker.
      return jsonOut({
        status: "ok",
        type: "bucket_budgets",
        data: readBucketBudgets(spreadsheet),
      });
    }

    if (target === "budgets") {
      var budgetSheet = spreadsheet.getSheetByName(BUDGETS_SHEET);
      if (!budgetSheet) return jsonOut({ status: "ok", data: [] });
      var budgetData = budgetSheet.getDataRange().getValues();
      var budgets = [];
      for (var b = 1; b < budgetData.length; b++) {
        budgets.push({
          id: Number(budgetData[b][0]) || 0,
          monthYear: normalizeBudgetMonthYearValue(budgetData[b][1], timeZone),
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
      var normalizedTimestamp = row[0]
        ? normalizeTimestampKey(row[0], timeZone)
        : "";
      var parsed = parseTransactionRow(row, schemaMode);

      txRecords.push({
        timestamp: normalizedTimestamp,
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

// =============================================================
// MANUAL REPAIR HELPER — Run this from the Apps Script Editor
// (Extensions → Apps Script → select runDescriptionRepair → Run)
//
// This scans all rows in _responses that have a blank Description
// and logs them. Since the sheet doesn't have the descriptions
// itself, actual patching requires the app to send them via the
// description_repair action. Use this to verify which rows still
// need repair after editing + syncing transactions from the app.
// =============================================================
function runDescriptionRepair() {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var txSheet = spreadsheet.getSheetByName(TRANSACTIONS_SHEET);
  if (!txSheet) {
    Logger.log("Sheet '" + TRANSACTIONS_SHEET + "' not found.");
    return;
  }

  var data = txSheet.getDataRange().getDisplayValues();
  if (data.length < 2) {
    Logger.log("No data rows found.");
    return;
  }

  var blankDescriptionRows = [];
  for (var i = 1; i < data.length; i++) {
    var desc = String(data[i][COL_DESCRIPTION] || "").trim();
    var ts   = String(data[i][COL_TIMESTAMP] || "").trim();
    var type = String(data[i][COL_TYPE] || "").trim();
    if (!desc && type) {
      blankDescriptionRows.push("Row " + (i + 1) + " | Timestamp: " + ts + " | Type: " + type + " | Amount: " + data[i][COL_AMOUNT]);
    }
  }

  if (blankDescriptionRows.length === 0) {
    Logger.log("All rows already have a Description. Nothing to repair.");
  } else {
    Logger.log("Rows with blank Description (" + blankDescriptionRows.length + " total):");
    blankDescriptionRows.forEach(function (line) { Logger.log(line); });
    Logger.log(
      "\nTo fix these: edit each transaction in the app and sync." +
      "\nEach save triggers an UPDATE sync which writes the description to the sheet."
    );
  }
}
