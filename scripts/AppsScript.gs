/**
 * SheetSync — Google Apps Script
 * ─────────────────────────────────────────────────────────────────────────────
 * HOW TO DEPLOY (do this every time you update the script):
 *
 *   1. Paste this code in the Apps Script editor (Extensions → Apps Script).
 *   2. Click  Deploy → New Deployment
 *   3. Type: Web App
 *   4. Execute as: Me
 *   5. Who has access: Anyone
 *   6. Click Deploy → Copy the new URL (it changes on every new deployment).
 *   7. Paste the new URL in local.properties:
 *        APPS_SCRIPT_URL=https://script.google.com/macros/s/YOUR_NEW_ID/exec
 *   8. Rebuild the Android app.
 *
 * NOTE: "Test Deployments" share a URL that works only with your own account.
 *       Always use "New Deployment" for the app.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Column layout (matches the original Google Form fields exactly):
 *   A: Timestamp  B: Date  C: Expense/Income  D: Expense Category
 *   E: Income Category  F: Description  G: Amount  H: Payment Mode
 *   I: Remarks  J: Synced At
 */

var SHEET_NAME = "Form Responses 1"; // Change to match your actual sheet tab name

/**
 * GET — returns all sheet rows as a JSON array for the Android "Import Data" feature.
 * The app calls this once to seed the local Room database with historical records.
 */
function doGet(e) {
  try {
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = spreadsheet.getSheetByName(SHEET_NAME);
    if (!sheet) {
      return ContentService.createTextOutput(
        JSON.stringify({ status: "ok", count: 0, data: [] }),
      ).setMimeType(ContentService.MimeType.JSON);
    }

    var data = sheet.getDataRange().getValues();
    var records = [];

    // Row 0 is the header; start from row 1
    for (var i = 1; i < data.length; i++) {
      var row = data[i];
      // Skip completely empty rows
      if (!row[2]) continue;
      records.push({
        timestamp: row[0] ? String(row[0]) : "", // A: Timestamp (remote key)
        date: formatDate(row[1]), // B: Date → YYYY-MM-DD string
        type: row[2], // C: Expense/Income
        expCategory: row[3] || "", // D: Expense Category
        incCategory: row[4] || "", // E: Income Category
        description: row[5] || "", // F: Description
        amount: Number(row[6]) || 0, // G: Amount
        paymentMode: row[7] || "", // H: Payment Mode
        remarks: row[8] || "", // I: Remarks
        syncedAt: row[9] ? String(row[9]) : "", // J: Synced At
      });
    }

    return ContentService.createTextOutput(
      JSON.stringify({ status: "ok", count: records.length, data: records }),
    ).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(
      JSON.stringify({ status: "error", message: err.message, data: [] }),
    ).setMimeType(ContentService.MimeType.JSON);
  }
}

/** Formats a Sheets date value to a YYYY-MM-DD string the Android app expects. */
function formatDate(value) {
  if (!value) return "";
  // If Sheets already stored it as a Date object, format it
  if (value instanceof Date) {
    var y = value.getFullYear();
    var m = String(value.getMonth() + 1).padStart(2, "0");
    var d = String(value.getDate()).padStart(2, "0");
    return y + "-" + m + "-" + d;
  }
  // Already a string — return as-is
  return String(value);
}

function doPost(e) {
  try {
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = spreadsheet.getSheetByName(SHEET_NAME);

    // Create the sheet with headers if it doesn't exist yet
    if (!sheet) {
      sheet = spreadsheet.insertSheet(SHEET_NAME);
      sheet.appendRow([
        "Timestamp",
        "Date",
        "Expense/Income",
        "Expense Category",
        "Income Category",
        "Description",
        "Amount",
        "Payment Mode",
        "Remarks",
        "Synced At",
      ]);
    }

    var payload = JSON.parse(e.postData.contents || "{}");
    var action = String(payload.action || "insert").toLowerCase();
    var records = payload.records || [];
    var targetTimestamp = payload.targetTimestamp || "";

    if (action === "delete") {
      if (!targetTimestamp) {
        return ContentService.createTextOutput(
          JSON.stringify({
            status: "error",
            message: "targetTimestamp is required for delete",
          }),
        ).setMimeType(ContentService.MimeType.JSON);
      }

      var deleted = deleteByTimestamp(sheet, targetTimestamp);
      return ContentService.createTextOutput(
        JSON.stringify({
          status: "ok",
          action: "delete",
          count: deleted ? 1 : 0,
        }),
      ).setMimeType(ContentService.MimeType.JSON);
    }

    if (action === "update") {
      var updated = 0;
      records.forEach(function (r) {
        var ts = r.remoteTimestamp || r.timestamp || "";
        if (!ts) return;
        if (updateByTimestamp(sheet, ts, r)) updated++;
      });

      return ContentService.createTextOutput(
        JSON.stringify({ status: "ok", action: "update", count: updated }),
      ).setMimeType(ContentService.MimeType.JSON);
    }

    // default: insert
    var inserted = 0;
    records.forEach(function (r) {
      var timestamp = new Date().toISOString();
      // date is sent as YYYY-MM-DD — Sheets parses this natively as a date value
      // expCategory is filled for Expense records, incCategory for Income records
      sheet.appendRow([
        timestamp, // A: Timestamp
        r.date, // B: Date (YYYY-MM-DD → Sheets auto-formats)
        r.type, // C: Expense/Income
        r.expCategory, // D: Expense Category (blank when Income)
        r.incCategory, // E: Income Category  (blank when Expense)
        r.description, // F: Description
        r.amount, // G: Amount
        r.paymentMode, // H: Payment Mode
        r.remarks, // I: Remarks
        timestamp, // J: Synced At
      ]);
      inserted++;
    });

    return ContentService.createTextOutput(
      JSON.stringify({ status: "ok", action: "insert", count: inserted }),
    ).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(
      JSON.stringify({ status: "error", message: err.message }),
    ).setMimeType(ContentService.MimeType.JSON);
  }
}

function findRowByTimestamp(sheet, timestamp) {
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) return -1;

  var values = sheet.getRange(2, 1, lastRow - 1, 1).getValues(); // Timestamp column
  for (var i = 0; i < values.length; i++) {
    if (String(values[i][0]) === String(timestamp)) {
      return i + 2;
    }
  }
  return -1;
}

function deleteByTimestamp(sheet, timestamp) {
  var row = findRowByTimestamp(sheet, timestamp);
  if (row < 0) return false;
  sheet.deleteRow(row);
  return true;
}

function updateByTimestamp(sheet, timestamp, record) {
  var row = findRowByTimestamp(sheet, timestamp);
  if (row < 0) return false;

  sheet.getRange(row, 2).setValue(record.date || "");
  sheet.getRange(row, 3).setValue(record.type || "");
  sheet.getRange(row, 4).setValue(record.expCategory || "");
  sheet.getRange(row, 5).setValue(record.incCategory || "");
  sheet.getRange(row, 6).setValue(record.description || "");
  sheet.getRange(row, 7).setValue(Number(record.amount) || 0);
  sheet.getRange(row, 8).setValue(record.paymentMode || "");
  sheet.getRange(row, 9).setValue(record.remarks || "");
  sheet.getRange(row, 10).setValue(new Date().toISOString());
  return true;
}
