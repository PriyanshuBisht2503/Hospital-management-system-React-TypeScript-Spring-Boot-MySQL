const DEFAULT_PATIENT_APP_URL = 'http://localhost:3001';

export function patientAppBaseUrl(): string {
  const configured = import.meta.env?.VITE_PATIENT_APP_URL;
  const base = configured && configured.trim() !== '' ? configured : DEFAULT_PATIENT_APP_URL;
  return base.replace(/\/$/, '');
}

export type PatientAppParams = Record<string, string | number | null | undefined>;

/** Builds an absolute URL into the patient portal (e.g. /book?doctorId=1&branchId=2). */
export function buildPatientAppUrl(path: string, params: PatientAppParams = {}): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === null || value === undefined || value === '') return;
    search.set(key, String(value));
  });
  const qs = search.toString();
  return `${patientAppBaseUrl()}${normalizedPath}${qs ? `?${qs}` : ''}`;
}
