type QueryValue = string | number | boolean | undefined | null;

/** Builds a "?key=value&..." query string, skipping empty/undefined values. */
export function buildQueryString(params: Record<string, QueryValue>): string {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      qs.set(key, String(value));
    }
  }
  const s = qs.toString();
  return s ? `?${s}` : '';
}
