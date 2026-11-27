// Tests the Funds backup/restore in the Apps Script, run with: node --test scripts/tests/funds.test.js
//
// Funds go to three tabs (_funds, _fund_holdings, _fund_plans). Holdings name their Account and
// plans their cycle's start date, because Import rebuilds Account and cycle ids (ADR-0011).
// The script is loaded as-is into a sandbox with a fake spreadsheet that coerces values the way
// Sheets does: a "yyyy-MM-dd" string becomes a Date cell and "=..." a formula, unless the column
// was formatted as plain text ("@") first. Both copies of the script are tested.

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..", "..");
const overridePath = process.env.APPS_SCRIPT_PATH;
const gsSource = fs.readFileSync(overridePath || path.join(root, "scripts", "AppsScript.gs"), "utf8");
const ktSource = fs.readFileSync(
  path.join(root, "app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/AppsScriptSetupScreen.kt"),
  "utf8",
);
const embedded = /private val APPS_SCRIPT_CODE = """\r?\n([\s\S]*?)\r?\n""".trimIndent\(\)/.exec(ktSource)[1];
// The script as deployed before Funds: SCRIPT_VERSION 2, which knows bucket budgets but not Funds.
const beforeFunds = fs.readFileSync(path.join(__dirname, "fixtures", "AppsScript.before-funds.gs"), "utf8");
const fixture = (name) => JSON.parse(fs.readFileSync(path.join(__dirname, "fixtures", name), "utf8"));

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

class FakeSheet {
  constructor() { this.clear(); }
  clear() { this.cells = {}; this.formats = {}; }
  getLastRow() { return Math.max(0, ...Object.keys(this.cells).map((k) => Number(k.split(",")[0]))); }
  getLastColumn() { return Math.max(0, ...Object.keys(this.cells).map((k) => Number(k.split(",")[1]))); }
  getMaxColumns() { return 26; }
  insertColumnsAfter() {}
  appendRow(values) {
    const row = this.getLastRow() + 1;
    values.forEach((v, c) => { this.cells[`${row},${c + 1}`] = v; });
  }
  getRange(row, col, numRows = 1, numCols = 1) {
    const sheet = this;
    return {
      getDisplayValues() {
        return Array.from({ length: numRows }, (_, r) =>
          Array.from({ length: numCols }, (_, c) => {
            const v = sheet.cells[`${row + r},${col + c}`];
            return v === undefined ? "" : String(v);
          }));
      },
      setNumberFormat(fmt) {
        for (let r = 0; r < numRows; r++) for (let c = 0; c < numCols; c++) sheet.formats[`${row + r},${col + c}`] = fmt;
        return this;
      },
      setValues(values) {
        values.forEach((line, r) => line.forEach((value, c) => {
          const key = `${row + r},${col + c}`;
          let stored = value;
          if (sheet.formats[key] !== "@" && typeof value === "string") {
            if (ISO_DATE.test(value)) stored = new Date(`${value}T00:00:00`);
            else if (value.startsWith("=")) stored = { formula: value };
          }
          sheet.cells[key] = stored;
        }));
        return this;
      },
    };
  }
  getDataRange() {
    const sheet = this;
    return {
      getValues() {
        const keys = Object.keys(sheet.cells).map((k) => k.split(",").map(Number));
        const rows = Math.max(0, ...keys.map(([r]) => r));
        const cols = Math.max(0, ...keys.map(([, c]) => c));
        return Array.from({ length: rows }, (_, r) =>
          Array.from({ length: cols }, (_, c) => {
            const v = sheet.cells[`${r + 1},${c + 1}`];
            return v === undefined ? "" : v;
          }));
      },
    };
  }
}

function loadScript(source) {
  const sheets = {};
  const spreadsheet = {
    getSheetByName: (name) => sheets[name] || null,
    insertSheet: (name) => (sheets[name] = new FakeSheet()),
  };
  const sandbox = {
    ContentService: {
      MimeType: { JSON: "json" },
      createTextOutput: (text) => ({ text, setMimeType() { return this; } }),
    },
    SpreadsheetApp: { getActiveSpreadsheet: () => spreadsheet },
    Session: { getScriptTimeZone: () => "UTC" },
    LockService: { getScriptLock: () => ({ waitLock() {}, releaseLock() {} }) },
    Utilities: { formatDate: () => "11/27/2026 12:00:00" },
    Logger: { log() {} },
    console,
  };
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox);
  const parse = (out) => JSON.parse(out.text);
  return {
    sheets,
    post: (payload) => parse(sandbox.doPost({ postData: { contents: JSON.stringify(payload) } })),
    get: (target) => parse(sandbox.doGet({ parameter: { target } })),
  };
}

const rows = (s, name) => s.sheets[name].getDataRange().getValues();

const FUNDS = [
  {
    id: 1, name: "Emergency", emoji: "🛟", colorIndex: 2, targetAmount: 100000, dueDate: "2027-03-31", archivedAt: null,
    sortOrder: 0, createdDate: "2026-09-01",
    holdings: [
      { accountName: "Emergency Fund RD", kind: "WHOLE", amount: 0, openingAmount: 30000, startDate: "2026-09-01" },
      { accountName: "HDFC", kind: "FIXED", amount: 70000, openingAmount: 70000, startDate: "2026-09-01" },
    ],
    plans: [
      { cycleStartDate: "2026-08-26", plannedAmount: 15000 },
      { cycleStartDate: "2026-09-25", plannedAmount: 15000 },
    ],
  },
  {
    id: 2, name: "Ministry", emoji: "", colorIndex: 5, targetAmount: null, dueDate: null, archivedAt: "2026-10-01",
    sortOrder: 1, createdDate: "2026-09-01",
    holdings: [{ accountName: "Ministry Fund", kind: "WHOLE", amount: 0, openingAmount: 0, startDate: "2026-09-15" }],
    plans: [],
  },
];

const ZONES = ["Asia/Calcutta", "America/Los_Angeles"];
const originalZone = process.env.TZ;

for (const zone of ZONES) for (const [label, source] of [["AppsScript.gs", gsSource], ["copy embedded in the app", embedded]]) {
  test.describe(`${label} in ${zone}`, () => {
    test.before(() => { process.env.TZ = zone; });
    test.after(() => { if (originalZone === undefined) delete process.env.TZ; else process.env.TZ = originalZone; });

    test("a backup restores to exactly what was sent, nested and in order", () => {
      const s = loadScript(source);
      const backup = s.post({ target: "funds", action: "backup", records: [], funds: FUNDS });
      assert.equal(backup.status, "ok");
      assert.equal(backup.type, "funds_backed_up"); // the marker the app trusts
      assert.equal(backup.count, 2);
      assert.equal(backup.scriptVersion, 3);
      const restored = s.get("funds");
      assert.equal(restored.type, "funds");
      assert.deepEqual(restored.data, FUNDS);
    });

    test("holdings are keyed by Account name and plans by cycle start date, as text", () => {
      const s = loadScript(source);
      s.post({ target: "funds", action: "backup", funds: FUNDS });
      assert.deepEqual(rows(s, "_fund_holdings")[0], ["Fund ID", "Account Name", "Kind", "Amount", "Opening Amount", "Start Date"]);
      assert.deepEqual(rows(s, "_fund_holdings")[2], [1, "HDFC", "FIXED", 70000, 70000, "2026-09-01"]);
      assert.deepEqual(rows(s, "_fund_plans")[0], ["Fund ID", "Cycle Start Date", "Planned Amount"]);
      assert.deepEqual(rows(s, "_fund_plans")[1], [1, "2026-08-26", 15000]);
      assert.equal(typeof rows(s, "_funds")[1][5], "string", "the due date stays text");
    });

    test("a name starting with = is kept as text, not run as a formula", () => {
      const s = loadScript(source);
      const hostile = [{ ...FUNDS[1], name: "=1+1", emoji: "=X", holdings: [{ ...FUNDS[1].holdings[0], accountName: "=A1" }] }];
      s.post({ target: "funds", action: "backup", funds: hostile });
      const fund = s.get("funds").data[0];
      assert.equal(fund.name, "=1+1");
      assert.equal(fund.emoji, "=X");
      assert.equal(fund.holdings[0].accountName, "=A1");
    });

    test("an empty backup is skipped unless explicitly allowed", () => {
      const s = loadScript(source);
      s.post({ target: "funds", action: "backup", funds: FUNDS });
      const skipped = s.post({ target: "funds", action: "backup", funds: [] });
      assert.equal(skipped.action, "backup_skipped");
      assert.equal(skipped.type, "funds_backed_up");
      assert.equal(s.get("funds").data.length, 2);

      s.post({ target: "funds", action: "backup", funds: [], allowEmptyBackup: true });
      assert.deepEqual(s.get("funds").data, []);
      assert.equal(rows(s, "_fund_holdings").length, 1, "header only");
    });

    test("a phone with no Funds never adds the Fund tabs to a Sheet that has none", () => {
      const s = loadScript(source);
      const res = s.post({ target: "funds", action: "backup", records: [], funds: [], allowEmptyBackup: true });
      assert.equal(res.type, "funds_backed_up");
      assert.equal(res.count, 0);
      assert.equal(s.sheets["_funds"], undefined);
    });

    test("a spreadsheet never backed up returns no Funds instead of failing", () => {
      const result = loadScript(source).get("funds");
      assert.equal(result.status, "ok");
      assert.equal(result.type, "funds");
      assert.deepEqual(result.data, []);
    });

    test("a date edited by hand into a Date cell, or a row with no name, is still read sensibly", () => {
      const s = loadScript(source);
      s.post({ target: "funds", action: "backup", funds: FUNDS });
      s.sheets["_fund_plans"].cells["2,2"] = new Date(2026, 7, 26);
      s.sheets["_funds"].cells["3,2"] = "";
      const data = s.get("funds").data;
      assert.deepEqual(data.map((f) => f.id), [1]);
      assert.equal(data[0].plans[0].cycleStartDate, "2026-08-26");
    });

    test("Funds never reach the transaction sheet, and other targets don't touch the Fund tabs", () => {
      const s = loadScript(source);
      s.post({ target: "funds", action: "backup", records: [], funds: FUNDS });
      assert.equal(s.sheets["_responses"], undefined);
      s.post({ target: "bucket_budgets", action: "backup", cycles: [] , allowEmptyBackup: true });
      assert.equal(s.get("funds").data.length, 2);
    });

    test("the app's exact request produces the response the app expects", () => {
      const s = loadScript(source);
      assert.equal(s.post(fixture("funds-request.json")).type, "funds_backed_up");
      assert.deepEqual(s.get("funds"), fixture("funds-response.json"));
    });
  });
}

test("the two copies of the script define the Funds code identically", { skip: Boolean(overridePath) }, () => {
  const block = (src) => src.slice(src.indexOf("// FUNDS ("), src.indexOf("function doPost(e)")).replace(/\r\n/g, "\n");
  assert.ok(block(gsSource).includes("function readFunds"));
  assert.equal(block(gsSource), block(embedded));
});

// A user who updates the app but hasn't redeployed: the script before Funds must not file them as
// transactions, and its replies carry no marker, so the app skips Funds and asks for a redeploy.
test.describe("a script deployed before Funds existed", () => {
  const transactionRows = (s) => (s.sheets["_responses"] ? s.sheets["_responses"].getDataRange().getValues().length : 0);

  test("receiving the app's backup appends nothing to the transaction sheet and gives no marker", () => {
    const s = loadScript(beforeFunds);
    const res = s.post(fixture("funds-request.json"));
    assert.equal(res.type, undefined);
    assert.equal(res.scriptVersion, 2);
    assert.ok(transactionRows(s) <= 1, "no junk transactions");
    assert.equal(s.sheets["_funds"], undefined);
  });

  test("a restore request gets no marker, so the app keeps its own Funds", () => {
    assert.notEqual(loadScript(beforeFunds).get("funds").type, "funds");
  });
});
