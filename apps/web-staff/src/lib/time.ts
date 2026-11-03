/** Fixed offset of Asia/Kolkata (IST). India does not observe daylight saving time. */
export const IST_OFFSET_MINUTES = 330;

const MS_PER_MINUTE = 60_000;

function parseDate(date: string): [number, number, number] {
  const parts = date.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) {
    throw new Error(`Invalid date "${date}"; expected YYYY-MM-DD`);
  }
  return [parts[0], parts[1], parts[2]];
}

/** UTC ISO instant of the start of the IST day (00:00 IST) for the given IST calendar date. */
export function startOfIstDayUtc(date: string): string {
  const [year, month, day] = parseDate(date);
  return new Date(Date.UTC(year, month - 1, day) - IST_OFFSET_MINUTES * MS_PER_MINUTE).toISOString();
}

/** UTC ISO instant of the end of the IST day (24:00 IST, i.e. next day 00:00 IST). */
export function endOfIstDayUtc(date: string): string {
  const [year, month, day] = parseDate(date);
  return new Date(Date.UTC(year, month - 1, day + 1) - IST_OFFSET_MINUTES * MS_PER_MINUTE).toISOString();
}

/** Converts an IST date + time-of-day (HH:mm[:ss]) into a UTC ISO instant. */
export function istTimeToUtc(date: string, time: string): string {
  const [year, month, day] = parseDate(date);
  const [hour = 0, minute = 0, second = 0] = time.split(':').map(Number);
  return new Date(Date.UTC(year, month - 1, day, hour, minute, second) - IST_OFFSET_MINUTES * MS_PER_MINUTE).toISOString();
}

/** IST calendar date (YYYY-MM-DD) that a given UTC instant falls on. */
export function toIstDateString(instant: string | Date): string {
  const d = typeof instant === 'string' ? new Date(instant) : instant;
  const ist = new Date(d.getTime() + IST_OFFSET_MINUTES * MS_PER_MINUTE);
  const year = ist.getUTCFullYear();
  const month = String(ist.getUTCMonth() + 1).padStart(2, '0');
  const day = String(ist.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
