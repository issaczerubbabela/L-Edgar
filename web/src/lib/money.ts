// Mirrors util/MoneyFormat.kt in the Android app: rupees with Indian digit grouping.

function groupIndian(digits: string): string {
  if (digits.length <= 3) return digits;
  const head = digits.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
  return `${head},${digits.slice(-3)}`;
}

/** Whole rupees: ₹1,20,000 or -₹940. */
export function rupees(amount: number): string {
  const rounded = Math.round(amount);
  return `${rounded < 0 ? "-" : ""}₹${groupIndian(String(Math.abs(rounded)))}`;
}

/** Exact, always two decimals, with a true minus sign and optionally a plus: +₹340.00. */
export function money(amount: number, signed = false): string {
  const paise = Math.round(amount * 100);
  const abs = Math.abs(paise);
  const text = `₹${groupIndian(String(Math.floor(abs / 100)))}.${String(abs % 100).padStart(2, "0")}`;
  if (paise < 0) return `−${text}`;
  return signed && paise > 0 ? `+${text}` : text;
}

/** Short axis labels: ₹950, ₹12k, ₹1.2L, ₹3Cr. */
export function compact(amount: number): string {
  const abs = Math.abs(amount);
  const sign = amount < 0 ? "-" : "";
  const trim = (n: number) => String(Number(n.toFixed(n < 10 ? 1 : 0)));
  if (abs >= 1e7) return `${sign}₹${trim(abs / 1e7)}Cr`;
  if (abs >= 1e5) return `${sign}₹${trim(abs / 1e5)}L`;
  if (abs >= 1e3) return `${sign}₹${trim(abs / 1e3)}k`;
  return `${sign}₹${Math.round(abs)}`;
}

export const toPaise = (amount: number) => Math.round(amount * 100);
export const fromPaise = (paise: number) => (paise === 0 ? 0 : paise / 100);
export const key = (name: string | null | undefined) => (name ?? "").trim().toLowerCase();
