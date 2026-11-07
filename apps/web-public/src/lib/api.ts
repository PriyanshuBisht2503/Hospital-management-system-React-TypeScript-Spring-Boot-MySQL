import type { AvailableSlot, Branch, Doctor, DoctorSearch, PageResponse, Specialty } from './types';

export class ApiError extends Error {
  status: number;
  fieldErrors?: Record<string, string>;

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export type QueryParams = Record<string, string | number | boolean | null | undefined>;

/** Serializes params into `?a=1&b=2`, skipping null / undefined / blank values. */
export function buildQuery(params: QueryParams): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === null || value === undefined || value === '') return;
    search.set(key, String(value));
  });
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

async function apiGet<T>(path: string, params: QueryParams = {}): Promise<T> {
  const response = await fetch(`/api${path}${buildQuery(params)}`, {
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    let message = response.statusText || 'Request failed';
    let fieldErrors: Record<string, string> | undefined;
    try {
      const body = await response.json();
      message = body?.message ?? message;
      fieldErrors = body?.fieldErrors ?? undefined;
    } catch {
      // Keep the status-text fallback.
    }
    throw new ApiError(response.status, message, fieldErrors);
  }

  return (await response.json()) as T;
}

export const publicApi = {
  listSpecialties: (params: QueryParams = {}) => apiGet<PageResponse<Specialty>>('/specialties', params),
  listBranches: (params: QueryParams = {}) => apiGet<PageResponse<Branch>>('/branches', params),
  searchDoctors: (params: QueryParams = {}) => apiGet<PageResponse<DoctorSearch>>('/doctors/search', params),
  getDoctor: (id: number) => apiGet<Doctor>(`/doctors/${id}`),
  getDoctorSlots: (id: number, params: QueryParams = {}) => apiGet<AvailableSlot[]>(`/doctors/${id}/slots`, params),
};
