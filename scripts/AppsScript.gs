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
      return ContentService
        .createTextOutput(JSON.stringify([]))
        .setMimeType(ContentService.MimeType.JSON);
    }

    var data = sheet.getDataRange().getValues();
    var records = [];

    // Row 0 is the header; start from row 1
    for (var i = 1; i < data.length; i++) {
      var row = data[i];
      // Skip completely empty rows
      if (!row[2]) continue;
      records.push({
        date:        formatDate(row[1]),  // B: Date → YYYY-MM-DD string
        type:        row[2],              // C: Expense/Income
        expCategory: row[3] || "",        // D: Expense Category
        incCategory: row[4] || "",        // E: Income Category
        description: row[5] || "",        // F: Description
        amount:      Number(row[6]) || 0, // G: Amount
        paymentMode: row[7] || "",        // H: Payment Mode
        remarks:     row[8] || ""         // I: Remarks
      });
    }

    return ContentService
      .createTextOutput(JSON.stringify(records))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService
      .createTextOutput(JSON.stringify({ error: err.message }))
      .setMimeType(ContentService.MimeType.JSON);
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
        "Timestamp", "Date", "Expense/Income",
        "Expense Category", "Income Category",
        "Description", "Amount", "Payment Mode", "Remarks", "Synced At"
      ]);
    }

    var payload = JSON.parse(e.postData.contents);
    var records = payload.records;
    var timestamp = new Date().toISOString();

    records.forEach(function (r) {
      // date is sent as YYYY-MM-DD — Sheets parses this natively as a date value
      // expCategory is filled for Expense records, incCategory for Income records
      sheet.appendRow([
        timestamp,          // A: Timestamp
        r.date,             // B: Date (YYYY-MM-DD → Sheets auto-formats)
        r.type,             // C: Expense/Income
        r.expCategory,      // D: Expense Category (blank when Income)
        r.incCategory,      // E: Income Category  (blank when Expense)
        r.description,      // F: Description
        r.amount,           // G: Amount
        r.paymentMode,      // H: Payment Mode
        r.remarks,          // I: Remarks
        timestamp           // J: Synced At
      ]);
    });

    return ContentService
      .createTextOutput(JSON.stringify({ status: "ok", count: records.length }))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService
      .createTextOutput(JSON.stringify({ status: "error", message: err.message }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
