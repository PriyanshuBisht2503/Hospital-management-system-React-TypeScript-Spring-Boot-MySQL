/**
 * Helpers for hospital-local schedule times (LocalTime strings like "09:00:00").
 * IMPORTANT: these are NOT UTC instants — do not run them through the IST<->UTC
 * conversion helpers in time.ts.
 */

/** Parses "HH:mm[:ss]" into minutes since midnight. */
export function timeToMinutes(time: string): number {
  const [h = 0, m = 0] = time.split(':').map((n) => Number(n) || 0);
  return h * 60 + m;
}

/** True when `a` is strictly before `b`. */
export function isBefore(a: string, b: string): boolean {
  return timeToMinutes(a) < timeToMinutes(b);
}

/** Strips seconds from a LocalTime string ("09:00:00" -> "09:00") for `<input type="time">`. */
export function stripSeconds(time: string): string {
  const [h = '00', m = '00'] = time.split(':');
  return `${h.padStart(2, '0')}:${m.padStart(2, '0')}`;
}
