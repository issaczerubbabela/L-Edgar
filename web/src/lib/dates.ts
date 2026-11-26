// Dates are plain yyyy-MM-dd strings everywhere: they sort and compare as text, and never pick up
// a time zone. Only this file turns them into Date objects, always at local noon.

export type Day = string;

const pad = (n: number) => String(n).padStart(2, "0");
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export const toDay = (d: Date): Day => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const fromDay = (day: Day): Date => {
  const [y, m, d] = day.split("-").map(Number);
  return new Date(y, m - 1, d, 12);
};
export const today = (): Day => toDay(new Date());
export const isDay = (text: string) => /^\d{4}-\d{2}-\d{2}$/.test(text);

export const addDays = (day: Day, n: number): Day => {
  const d = fromDay(day);
  d.setDate(d.getDate() + n);
  return toDay(d);
};
export const addMonths = (day: Day, n: number): Day => {
  const d = fromDay(day);
  const target = new Date(d.getFullYear(), d.getMonth() + n, 1, 12);
  const last = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
  target.setDate(Math.min(d.getDate(), last));
  return toDay(target);
};
export const daysBetween = (a: Day, b: Day): number =>
  Math.round((fromDay(b).getTime() - fromDay(a).getTime()) / 86_400_000);

export const monthOf = (day: Day) => day.slice(0, 7);
export const monthStart = (month: string): Day => `${month}-01`;
export const monthEnd = (month: string): Day => {
  const [y, m] = month.split("-").map(Number);
  return `${month}-${pad(new Date(y, m, 0).getDate())}`;
};
export const shiftMonth = (month: string, n: number) => monthOf(addMonths(monthStart(month), n));
/** Monday of the week [day] is in. */
export const weekStart = (day: Day): Day => addDays(day, -((fromDay(day).getDay() + 6) % 7));
export const maxDay = (a: Day, b: Day) => (a > b ? a : b);
export const minDay = (a: Day, b: Day) => (a < b ? a : b);

export const eachDay = (start: Day, end: Day): Day[] => {
  const days: Day[] = [];
  for (let d = start; d <= end; d = addDays(d, 1)) days.push(d);
  return days;
};

export const monthShort = (month: string) => MONTHS[Number(month.slice(5, 7)) - 1];
export const monthLabel = (month: string) => `${monthShort(month)} ${month.slice(0, 4)}`;
export const dayMonth = (day: Day) => `${Number(day.slice(8))} ${monthShort(day)}`;
export const longDay = (day: Day) =>
  isDay(day)
    ? fromDay(day).toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short", year: "numeric" })
    : day;

/**
 * Reads the date forms a Sheet cell can arrive in: yyyy-MM-dd, M/d/yyyy, an ISO timestamp, or the
 * text of a JavaScript Date ("Mon Jan 01 2024 00:00:00 GMT+0530 ..."). Returns "" when none fit.
 */
export function parseDay(raw: unknown): Day | "" {
  const text = String(raw ?? "").trim();
  if (!text) return "";
  let m = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(text);
  if (m) return `${m[1]}-${pad(+m[2])}-${pad(+m[3])}`;
  m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})/.exec(text);
  if (m) return `${m[3]}-${pad(+m[1])}-${pad(+m[2])}`;
  m = /^[A-Za-z]{3},? ([A-Za-z]{3}) (\d{1,2}),? (\d{4})/.exec(text);
  if (m) {
    const month = MONTHS.findIndex((name) => name.toLowerCase() === m![1].toLowerCase());
    if (month >= 0) return `${m[3]}-${pad(month + 1)}-${pad(+m[2])}`;
  }
  const parsed = new Date(text);
  return isNaN(parsed.getTime()) ? "" : toDay(parsed);
}

/** "M/d/yyyy HH:mm:ss", the Sheet's Timestamp column format. */
export const timestampNow = (now = new Date()) =>
  `${now.getMonth() + 1}/${now.getDate()}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
