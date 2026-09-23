// Tests the bucket-budget backup/restore in the Apps Script, run with: node --test scripts/tests/bucket-budgets.test.js
//
// The script is plain JavaScript, so it is loaded as-is into a sandbox with a fake spreadsheet.
// The fake reproduces the two ways Google Sheets corrupts values written with setValues():
//   - a "yyyy-MM-dd" string becomes a Date cell, and
//   - a string starting with "=" becomes a formula,
// unless the column was formatted as plain text ("@") first.
//
// Both copies of the script are tested: scripts/AppsScript.gs and the copy embedded in
// AppsScriptSetupScreen.kt that users paste into Apps Script. If they drift apart, this fails.

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..", "..");
const gsSource = fs.readFileSync(path.join(root, "scripts", "AppsScript.gs"), "utf8");
const ktSource = fs.readFileSync(
  path.join(root, "app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/AppsScriptSetupScreen.kt"),
  "utf8",
);
const embedded = /private val APPS_SCRIPT_CODE = """\r?\n([\s\S]*?)\r?\n""".trimIndent\(\)/.exec(ktSource)[1];

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

class FakeSheet {
  constructor() { this.clear(); }
  clear() { this.cells = {}; this.formats = {}; }
  getLastRow() { return Math.max(0, ...Object.keys(this.cells).map((k) => Number(k.split(",")[0]))); }
  appendRow(values) {
    const row = this.getLastRow() + 1;
    values.forEach((v, c) => { this.cells[`${row},${c + 1}`] = v; });
  }
  getRange(row, col, numRows = 1, numCols = 1) {
    const sheet = this;
    return {
      setNumberFormat(fmt) {
        for (let r = 0; r < numRows; r++) for (let c = 0; c < numCols; c++) sheet.formats[`${row + r},${col + c}`] = fmt;
        return this;
      },
      setValues(values) {
        values.forEach((line, r) => line.forEach((value, c) => {
          const key = `${row + r},${col + c}`;
          let stored = value;
          if (sheet.formats[key] !== "@" && typeof value === "string") {
            if (ISO_DATE.test(value)) stored = new Date(`${value}T00:00:00`); // Sheets coerces to a date
            else if (value.startsWith("=")) stored = { formula: value };      // ...or a formula
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
    Utilities: { formatDate: () => "9/24/2026 12:00:00" },
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

const CYCLES = [
  {
    id: 1, startDate: "2026-08-26", endDate: "2026-09-24", spendableAmount: 68000, closedAt: "2026-09-25",
    buckets: [
      { id: 10, name: "Essentials", note: "Rent, power", colorIndex: 4, emoji: "🏠", allocatedAmount: 18000, sortOrder: 0,
        categories: ["Rent", "Utilities"] },
      { id: 11, name: "Eating Out", note: "", colorIndex: 1, emoji: "", allocatedAmount: 6000, sortOrder: 1,
        categories: ["Food & Snacks"] },
    ],
  },
  { id: 2, startDate: "2026-09-25", endDate: "2026-10-24", spendableAmount: 70000, closedAt: null, buckets: [] },
];

// Every test runs twice: once in India (ahead of UTC) and once in Los Angeles (behind it). Date
// handling in Apps Script depends on the script's time zone, and behind UTC is where a plain
// new Date("2026-08-26") reads back as the 25th. The zone is set in-process because Node on
// Windows ignores a TZ variable set from the shell.
const ZONES = ["Asia/Calcutta", "America/Los_Angeles"];
const originalZone = process.env.TZ;

for (const zone of ZONES) for (const [label, source] of [["AppsScript.gs", gsSource], ["copy embedded in the app", embedded]]) {
  test.describe(`${label} in ${zone}`, () => {
    test.before(() => { process.env.TZ = zone; });
    test.after(() => { if (originalZone === undefined) delete process.env.TZ; else process.env.TZ = originalZone; });

    test("the time zone really is the one under test", () => {
      assert.equal(Intl.DateTimeFormat().resolvedOptions().timeZone, zone);
    });

    test("a backup restores to exactly what was sent, nested and in order", () => {
      const s = loadScript(source);
      const backup = s.post({ target: "bucket_budgets", action: "backup", cycles: CYCLES });
      assert.equal(backup.status, "ok");
      assert.equal(backup.type, "bucket_budgets_backed_up"); // the marker the app trusts
      assert.equal(backup.count, 2);
      assert.deepEqual(s.get("bucket_budgets").data, CYCLES);
    });

    test("dates are stored as text so Sheets cannot turn them into Date cells", () => {
      const s = loadScript(source);
      s.post({ target: "bucket_budgets", action: "backup", cycles: CYCLES });
      const rows = s.sheets["_cycles"].getDataRange().getValues();
      assert.equal(rows[1][1], "2026-08-26");
      assert.equal(typeof rows[1][1], "string");
      assert.equal(rows[1][4], "2026-09-25");
    });

    test("a note or category starting with = is kept as text, not run as a formula", () => {
      const s = loadScript(source);
      const hostile = [{ id: 1, startDate: "2026-09-01", endDate: "2026-09-30", spendableAmount: 1, closedAt: null,
        buckets: [{ id: 5, name: "=1+1", note: "=HYPERLINK(\"http://evil\")", colorIndex: 0, emoji: "", allocatedAmount: 0, sortOrder: 0,
          categories: ["=SUM(A1:A9)"] }] }];
      s.post({ target: "bucket_budgets", action: "backup", cycles: hostile });
      const restored = s.get("bucket_budgets").data[0].buckets[0];
      assert.equal(restored.name, "=1+1");
      assert.equal(restored.note, "=HYPERLINK(\"http://evil\")");
      assert.deepEqual(restored.categories, ["=SUM(A1:A9)"]);
    });

    test("an empty backup is skipped so a fresh install cannot erase the sheets", () => {
      const s = loadScript(source);
      s.post({ target: "bucket_budgets", action: "backup", cycles: CYCLES });
      const skipped = s.post({ target: "bucket_budgets", action: "backup", cycles: [] });
      assert.equal(skipped.action, "backup_skipped");
      assert.equal(skipped.type, "bucket_budgets_backed_up");
      assert.equal(s.get("bucket_budgets").data.length, 2);
    });

    test("an empty backup is honoured only when explicitly allowed", () => {
      const s = loadScript(source);
      s.post({ target: "bucket_budgets", action: "backup", cycles: CYCLES });
      s.post({ target: "bucket_budgets", action: "backup", cycles: [], allowEmptyBackup: true });
      assert.deepEqual(s.get("bucket_budgets").data, []);
    });

    test("a spreadsheet that has never been backed up returns no cycles instead of failing", () => {
      const result = loadScript(source).get("bucket_budgets");
      assert.equal(result.status, "ok");
      assert.equal(result.type, "bucket_budgets"); // marks a script that understands the target
      assert.deepEqual(result.data, []);
    });

    test("a second backup replaces the first rather than appending", () => {
      const s = loadScript(source);
      s.post({ target: "bucket_budgets", action: "backup", cycles: CYCLES });
      s.post({ target: "bucket_budgets", action: "backup", cycles: [CYCLES[1]] });
      assert.deepEqual(s.get("bucket_budgets").data, [CYCLES[1]]);
      assert.equal(s.sheets["_buckets"].getDataRange().getValues().length, 1); // header only
    });

    test("a cycle row with no dates is dropped rather than returned half-formed", () => {
      const s = loadScript(source);
      s.post({ target: "bucket_budgets", action: "backup", cycles: CYCLES });
      s.sheets["_cycles"].cells["3,2"] = ""; // blank out the second cycle's start date
      assert.deepEqual(s.get("bucket_budgets").data.map((c) => c.id), [1]);
    });

    test("a date edited by hand into a real Date cell is still read back correctly", () => {
      const s = loadScript(source);
      s.post({ target: "bucket_budgets", action: "backup", cycles: CYCLES });
      s.sheets["_cycles"].cells["2,2"] = new Date(2026, 7, 26); // Sheets Date cell, local midnight
      assert.equal(s.get("bucket_budgets").data[0].startDate, "2026-08-26");
    });

    test("the existing budgets backup still works and is not mistaken for the new target", () => {
      const s = loadScript(source);
      const res = s.post({ target: "budgets", action: "backup",
        records: [{ id: 1, monthYear: "2026-09", category: "Food", amount: 100 }] });
      assert.equal(res.status, "ok");
      assert.equal(res.type, "budgets_backed_up");
      assert.equal(res.action, undefined);
      assert.equal(s.get("budgets").data.length, 1);
      assert.equal(s.sheets["_cycles"], undefined);
    });
  });
}

test("the two copies of the script define the bucket-budget code identically", () => {
  const block = (src) => src.slice(src.indexOf("// BUCKET BUDGETS"), src.indexOf("function doPost(e)")).replace(/\r\n/g, "\n");
  assert.equal(block(gsSource), block(embedded));
});

// ---------------------------------------------------------------------------------------------
// Compatibility: a user who updates the app but has not yet redeployed the script in their sheet.
// The fixture is the real script from before bucket budgets existed.
// ---------------------------------------------------------------------------------------------
const oldSource = fs.readFileSync(path.join(__dirname, "fixtures", "AppsScript.before-bucket-budgets.gs"), "utf8");

test.describe("a script deployed before bucket budgets existed", () => {
  const transactionRows = (s) => (s.sheets["_responses"] ? s.sheets["_responses"].getDataRange().getValues().length : 0);

  test("receiving the app's backup appends nothing to the transaction sheet", () => {
    const s = loadScript(oldSource);
    const res = s.post({ target: "bucket_budgets", action: "backup", records: [], cycles: CYCLES });
    assert.equal(res.status, "ok");
    assert.equal(res.type, undefined, "no marker, so the app knows nothing was backed up");
    assert.equal(transactionRows(s), 1, "only the header row; no junk transactions");
    assert.equal(s.sheets["_cycles"], undefined);
  });

  test("this is why cycles are not sent as records: the old script would file them as transactions", () => {
    const s = loadScript(oldSource);
    s.post({ target: "bucket_budgets", action: "backup", records: CYCLES });
    assert.equal(transactionRows(s), 1 + CYCLES.length, "one junk transaction row per cycle");
  });

  test("a restore request gets no marker, so the app knows to ignore the reply", () => {
    const s = loadScript(oldSource);
    const res = s.get("bucket_budgets");
    assert.notEqual(res.type, "bucket_budgets");
  });
});

test("the new script never files cycles as transactions", () => {
  const s = loadScript(gsSource);
  s.post({ target: "bucket_budgets", action: "backup", records: [], cycles: CYCLES });
  assert.equal(s.sheets["_responses"], undefined, "the transaction sheet is not even created");
});

// ---------------------------------------------------------------------------------------------
// Contract with the app. The Android unit test BucketBudgetContractTest checks that the app
// serialises to exactly bucket-budgets-request.json and can parse bucket-budgets-response.json.
// Here the script must turn that same request into that same response, so a renamed field on
// either side fails one of the two.
// ---------------------------------------------------------------------------------------------
test.describe("the contract with the app", () => {
  const fixture = (name) => JSON.parse(fs.readFileSync(path.join(__dirname, "fixtures", name), "utf8"));

  for (const [label, source] of [["AppsScript.gs", gsSource], ["copy embedded in the app", embedded]]) {
    test(`${label}: the app's exact request produces the response the app expects`, () => {
      const s = loadScript(source);
      const backup = s.post(fixture("bucket-budgets-request.json"));
      assert.equal(backup.type, "bucket_budgets_backed_up");
      assert.deepEqual(s.get("bucket_budgets"), fixture("bucket-budgets-response.json"));
    });
  }
});
