const IST_OFFSET_MINUTES = 330;
const MS_PER_MINUTE = 60_000;

function parseDate(date: string): [number, number, number] {
  const parts = date.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) {
    throw new Error(`Invalid date "${date}"; expected YYYY-MM-DD`);
  }
  return [parts[0], parts[1], parts[2]];
}

/** Formats a UTC instant as "YYYY-MM-DD HH:mm IST". */
export function formatIstDateTime(instant: string): string {
  const ist = new Date(new Date(instant).getTime() + IST_OFFSET_MINUTES * MS_PER_MINUTE);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${ist.getUTCFullYear()}-${pad(ist.getUTCMonth() + 1)}-${pad(ist.getUTCDate())} ${pad(ist.getUTCHours())}:${pad(ist.getUTCMinutes())} IST`;
}

/** Formats a UTC instant as "HH:mm" in IST. */
export function formatIstTime(instant: string): string {
  const ist = new Date(new Date(instant).getTime() + IST_OFFSET_MINUTES * MS_PER_MINUTE);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(ist.getUTCHours())}:${pad(ist.getUTCMinutes())}`;
}

/** IST calendar date (YYYY-MM-DD) that a UTC instant falls on. */
export function toIstDateString(instant: string | Date): string {
  const d = typeof instant === 'string' ? new Date(instant) : instant;
  const ist = new Date(d.getTime() + IST_OFFSET_MINUTES * MS_PER_MINUTE);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${ist.getUTCFullYear()}-${pad(ist.getUTCMonth() + 1)}-${pad(ist.getUTCDate())}`;
}

/** Adds whole days to an IST calendar date (YYYY-MM-DD). */
export function addIstDays(date: string, days: number): string {
  const [year, month, day] = parseDate(date);
  const dt = new Date(Date.UTC(year, month - 1, day + days));
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${dt.getUTCFullYear()}-${pad(dt.getUTCMonth() + 1)}-${pad(dt.getUTCDate())}`;
}

/** The next `count` IST calendar dates starting today. */
export function nextIstDates(count: number, now: Date = new Date()): string[] {
  const today = toIstDateString(now);
  return Array.from({ length: count }, (_, i) => addIstDays(today, i));
}
