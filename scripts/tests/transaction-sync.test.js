// Tests the Apps Script's transaction sync contract. Run with: node --test scripts/tests/
//
// The script is plain JavaScript, so it is loaded as-is into a sandbox with a fake spreadsheet that
// reproduces how Sheets coerces strings written with setValues(): "yyyy-MM-dd" becomes a date and a
// string starting with "=" becomes a formula, unless the cell is formatted as plain text ("@").
//
// Both copies of the script are checked: scripts/AppsScript.gs and the copy embedded in
// AppsScriptSetupScreen.kt that users paste into Apps Script. If they drift apart, this fails.

process.env.TZ = "UTC";

const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..", "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8").replace(/\r\n/g, "\n");
const SCRIPT = read("scripts/AppsScript.gs");
const SCRIPT_V1 = read("scripts/tests/fixtures/AppsScript.v1.gs");
const EMBEDDED = /private val APPS_SCRIPT_CODE = """\n([\s\S]*?)\n""".trimIndent\(\)/.exec(
  read("app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/AppsScriptSetupScreen.kt"),
)[1];

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

class FakeSheet {
  constructor() {
    this.cells = {};
    this.formats = {};
  }
  key(r, c) {
    return `${r},${c}`;
  }
  getLastRow() {
    return Math.max(0, ...Object.keys(this.cells).map((k) => Number(k.split(",")[0])));
  }
  getLastColumn() {
    return Math.max(0, ...Object.keys(this.cells).map((k) => Number(k.split(",")[1])));
  }
  getMaxColumns() {
    return Math.max(26, this.getLastColumn());
  }
  getMaxRows() {
    return Math.max(1000, this.getLastRow());
  }
  insertColumnsAfter() {}
  clear() {
    this.cells = {};
  }
  clearContents() {
    this.cells = {};
  }
  appendRow(values) {
    const row = this.getLastRow() + 1;
    values.forEach((v, c) => this.write(row, c + 1, v));
  }
  write(r, c, value) {
    let stored = value;
    if (this.formats[this.key(r, c)] !== "@" && typeof value === "string") {
      if (ISO_DATE.test(value)) stored = new Date(`${value}T00:00:00Z`);
      else if (value.startsWith("=")) stored = { formula: value };
    }
    if (stored === "" || stored === undefined) delete this.cells[this.key(r, c)];
    else this.cells[this.key(r, c)] = stored;
  }
  cell(r, c) {
    const v = this.cells[this.key(r, c)];
    return v === undefined ? "" : v;
  }
  deleteRow(row) {
    const next = {};
    for (const [k, v] of Object.entries(this.cells)) {
      const [r, c] = k.split(",").map(Number);
      if (r < row) next[k] = v;
      else if (r > row) next[this.key(r - 1, c)] = v;
    }
    this.cells = next;
  }
  getRange(row, col, numRows = 1, numCols = 1) {
    const sheet = this;
    const grid = (fn) =>
      Array.from({ length: numRows }, (_, r) => Array.from({ length: numCols }, (_, c) => fn(row + r, col + c)));
    return {
      getValues: () => grid((r, c) => sheet.cell(r, c)),
      getDisplayValues: () => grid((r, c) => String(sheet.cell(r, c))),
      setValues(values) {
        values.forEach((line, r) => line.forEach((v, c) => sheet.write(row + r, col + c, v)));
        return this;
      },
      setNumberFormat(fmt) {
        // Formatting a whole column: remember it for the rows the tests use.
        for (let r = 0; r < Math.min(numRows, 200); r++) {
          for (let c = 0; c < numCols; c++) sheet.formats[sheet.key(row + r, col + c)] = fmt;
        }
        return this;
      },
    };
  }
  getDataRange() {
    return this.getRange(1, 1, Math.max(1, this.getLastRow()), Math.max(1, this.getLastColumn()));
  }
  /** Rows as plain values (header included), for assertions. */
  rows() {
    return this.getDataRange().getValues();
  }
}

function pad(n) {
  return String(n).padStart(2, "0");
}

function loadScript(source) {
  const sheets = {};
  const lock = { acquired: 0, released: 0 };
  const spreadsheet = {
    getSheetByName: (name) => sheets[name] || null,
    insertSheet: (name) => (sheets[name] = new FakeSheet()),
  };
  const sandbox = {
    SpreadsheetApp: { getActiveSpreadsheet: () => spreadsheet },
    Session: { getScriptTimeZone: () => "UTC" },
    LockService: {
      getScriptLock: () => ({
        waitLock: () => lock.acquired++,
        releaseLock: () => lock.released++,
      }),
    },
    Utilities: {
      getUuid: () => crypto.randomUUID(),
      formatDate(date, _tz, pattern) {
        const d = new Date(date);
        return pattern
          .replace("yyyy", d.getUTCFullYear())
          .replace("MM", pad(d.getUTCMonth() + 1))
          .replace(/\bM\b/, d.getUTCMonth() + 1)
          .replace("dd", pad(d.getUTCDate()))
          .replace(/\bd\b/, d.getUTCDate())
          .replace("HH", pad(d.getUTCHours()))
          .replace("mm", pad(d.getUTCMinutes()))
          .replace("ss", pad(d.getUTCSeconds()));
      },
    },
    ContentService: {
      MimeType: { JSON: "json" },
      createTextOutput: (text) => ({ setMimeType: () => JSON.parse(text) }),
    },
  };
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox);
  return {
    post: (payload) => sandbox.doPost({ postData: { contents: JSON.stringify(payload) } }),
    get: (target = "transactions") => sandbox.doGet({ parameter: { target } }),
    sheet: (name = "_responses") => sheets[name] || spreadsheet.insertSheet(name),
    lock,
  };
}

function transaction(id, overrides = {}) {
  return {
    id,
    timestamp: "9/24/2026 10:00:00",
    date: "2026-09-24",
    type: "Expense",
    expCategory: "Food",
    incCategory: "",
    description: "Lunch",
    amount: 120,
    accountName: "Cash",
    remarks: "",
    isBookmarked: false,
    ...overrides,
  };
}

const HEADERS = [
  "Timestamp", "Date", "Type", "Exp Category", "Inc Category", "Description", "Amount",
  "Account Name", "From Account Name", "To Account Name", "Remarks", "Synced At", "Is Bookmarked",
];

test("both copies of the script are identical", () => {
  assert.equal(EMBEDDED, SCRIPT.replace(/\n+$/, ""));
});

test("every reply carries the script version and every request takes the lock", () => {
  const app = loadScript(SCRIPT);
  assert.equal(app.post({ target: "transactions", action: "upsert", transactions: [] }).scriptVersion, 2);
  assert.equal(app.get().scriptVersion, 2);
  assert.equal(app.lock.acquired, 2);
  assert.equal(app.lock.released, 2);
});

test("sending the same upsert twice leaves one row per ID", () => {
  const app = loadScript(SCRIPT);
  const batch = [transaction("a"), transaction("b", { amount: 50 })];

  const first = app.post({ target: "transactions", action: "upsert", transactions: batch });
  const second = app.post({ target: "transactions", action: "upsert", transactions: batch });

  assert.deepEqual([first.inserted, first.updated], [2, 0]);
  assert.deepEqual([second.inserted, second.updated], [0, 2]);
  const data = app.get().data;
  assert.deepEqual(data.map((r) => r.id).sort(), ["a", "b"]);
});

test("an upsert overwrites the row with that ID and keeps its timestamp", () => {
  const app = loadScript(SCRIPT);
  app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] });

  app.post({
    target: "transactions",
    action: "upsert",
    transactions: [transaction("a", { amount: 999, timestamp: "1/1/2030 00:00:00" })],
  });

  const [row] = app.get().data;
  assert.equal(row.amount, 999);
  assert.equal(row.timestamp, "9/24/2026 10:00:00");
});

test("the same ID twice in one batch is written once", () => {
  const app = loadScript(SCRIPT);
  const reply = app.post({
    target: "transactions",
    action: "upsert",
    transactions: [transaction("a", { amount: 1 }), transaction("a", { amount: 2 })],
  });
  assert.equal(reply.inserted, 1);
  assert.deepEqual(app.get().data.map((r) => r.amount), [2]);
});

test("reading gives hand-typed rows an ID once, and it never changes", () => {
  const app = loadScript(SCRIPT);
  const sheet = app.sheet();
  sheet.appendRow(HEADERS);
  sheet.appendRow(["9/1/2026 08:00:00", "9/1/2026", "Expense", "Travel", "", "Bus", 30, "Cash"]);

  const first = app.get();
  const second = app.get();

  assert.equal(first.idsAssigned, 1);
  assert.equal(second.idsAssigned, 0);
  assert.match(first.data[0].id, /^[0-9a-f-]{36}$/);
  assert.equal(second.data[0].id, first.data[0].id);
  assert.equal(sheet.cell(1, 14), "ID");
});

test("a row pasted with a copied ID gets its own ID; the original keeps it", () => {
  const app = loadScript(SCRIPT);
  app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] });
  const sheet = app.sheet();
  const copy = sheet.getRange(2, 1, 1, 14).getValues()[0];
  sheet.appendRow(copy);

  const data = app.get().data;

  assert.equal(data.length, 2);
  assert.equal(data[0].id, "a");
  assert.notEqual(data[1].id, "a");
  assert.match(data[1].id, /^[0-9a-f-]{36}$/);
});

test("the ID column never trips the old shifted-row repair", () => {
  const app = loadScript(SCRIPT);
  app.post({
    target: "transactions",
    action: "upsert",
    transactions: [transaction("a", { accountName: "HDFC", remarks: "note" })],
  });

  const [row] = app.get().data;
  assert.equal(row.accountName, "HDFC");
  assert.equal(row.remarks, "note");
  assert.equal(row.fromAccountName, "");
});

test("transfers keep their from and to accounts", () => {
  const app = loadScript(SCRIPT);
  app.post({
    target: "transactions",
    action: "upsert",
    transactions: [
      transaction("t", { type: "Transfer", expCategory: "", accountName: "Bank -> Cash", fromAccountName: "Bank", toAccountName: "Cash" }),
    ],
  });
  const [row] = app.get().data;
  assert.deepEqual([row.fromAccountName, row.toAccountName], ["Bank", "Cash"]);
});

test("text fields are stored as text, not turned into dates or formulas", () => {
  const app = loadScript(SCRIPT);
  app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] });
  app.post({
    target: "transactions",
    action: "upsert",
    transactions: [transaction("b", { description: "2026-01-02", remarks: "=1+1" })],
  });

  const row = app.get().data.find((r) => r.id === "b");
  assert.equal(row.description, "2026-01-02");
  assert.equal(row.remarks, "=1+1");
});

test("a row's revision is stable, and changes when its content is edited by hand", () => {
  const app = loadScript(SCRIPT);
  app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] });

  const first = app.get().data[0].revision;
  assert.equal(app.get().data[0].revision, first);

  app.sheet().write(2, 7, 999); // someone types a new amount into the Sheet
  assert.notEqual(app.get().data[0].revision, first);
});

test("the Synced At column doesn't change a row's revision", () => {
  const app = loadScript(SCRIPT);
  app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] });
  const before = app.get().data[0].revision;

  app.sheet().write(2, 12, "2030-01-01T00:00:00.000Z");

  assert.equal(app.get().data[0].revision, before);
});

test("an upsert reports each written row's stored revision", () => {
  const app = loadScript(SCRIPT);

  const reply = app.post({ target: "transactions", action: "upsert", transactions: [transaction("a"), transaction("b")] });

  const data = app.get().data;
  assert.equal(reply.revisions.a, data.find((r) => r.id === "a").revision);
  assert.equal(reply.revisions.b, data.find((r) => r.id === "b").revision);
});

test("an upsert based on the current revision is written", () => {
  const app = loadScript(SCRIPT);
  const base = app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] }).revisions.a;

  const reply = app.post({ target: "transactions", action: "upsert", transactions: [transaction("a", { amount: 5, base })] });

  assert.deepEqual(reply.stale, []);
  assert.equal(app.get().data[0].amount, 5);
});

test("an upsert based on an older revision is refused, so a hand edit isn't overwritten", () => {
  const app = loadScript(SCRIPT);
  const base = app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] }).revisions.a;
  app.sheet().write(2, 7, 999);

  const reply = app.post({
    target: "transactions",
    action: "upsert",
    transactions: [transaction("a", { amount: 5, base }), transaction("b")],
  });

  assert.deepEqual(reply.stale, ["a"]);
  assert.equal(reply.revisions.a, undefined);
  const data = app.get().data;
  assert.equal(data.find((r) => r.id === "a").amount, 999);
  assert.ok(data.find((r) => r.id === "b"), "the rest of the batch is still written");
});

test("an upsert for a row deleted in the Sheet puts it back", () => {
  const app = loadScript(SCRIPT);
  const base = app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] }).revisions.a;
  app.post({ target: "transactions", action: "delete_ids", ids: ["a"] });

  const reply = app.post({ target: "transactions", action: "upsert", transactions: [transaction("a", { base })] });

  assert.deepEqual(reply.stale, []);
  assert.equal(app.get().data.length, 1);
});

test("deleting by ID is safe to repeat", () => {
  const app = loadScript(SCRIPT);
  app.post({ target: "transactions", action: "upsert", transactions: [transaction("a"), transaction("b")] });

  const first = app.post({ target: "transactions", action: "delete_ids", ids: ["a"] });
  const again = app.post({ target: "transactions", action: "delete_ids", ids: ["a"] });

  assert.deepEqual([first.status, first.deleted], ["ok", 1]);
  assert.deepEqual([again.status, again.deleted, again.missing], ["ok", 0, ["a"]]);
  assert.deepEqual(app.get().data.map((r) => r.id), ["b"]);
});

test("deleting several rows removes exactly those rows", () => {
  const app = loadScript(SCRIPT);
  const ids = ["a", "b", "c", "d"];
  app.post({ target: "transactions", action: "upsert", transactions: ids.map((id) => transaction(id)) });

  app.post({ target: "transactions", action: "delete_ids", ids: ["b", "d"] });

  assert.deepEqual(app.get().data.map((r) => r.id), ["a", "c"]);
});

test("older app versions keep working: a legacy update leaves the ID alone", () => {
  const app = loadScript(SCRIPT);
  app.post({ target: "transactions", action: "upsert", transactions: [transaction("a")] });

  app.post({
    target: "transactions",
    action: "update",
    records: [{ ...transaction("ignored"), timestamp: "9/24/2026 10:00:00", amount: 7 }],
  });

  const data = app.get().data;
  assert.equal(data.length, 1);
  assert.deepEqual([data[0].id, data[0].amount], ["a", 7]);
});

test("a version-1 script ignores version-2 requests instead of writing rows", () => {
  const old = loadScript(SCRIPT_V1);

  const upsert = old.post({ target: "transactions", action: "upsert", records: [], transactions: [transaction("a")] });
  const remove = old.post({ target: "transactions", action: "delete_ids", records: [], ids: ["a"] });
  const read = old.get();

  assert.equal(upsert.scriptVersion, undefined);
  assert.equal(remove.scriptVersion, undefined);
  assert.equal(read.scriptVersion, undefined);
  assert.equal(read.data.length, 0);
});
