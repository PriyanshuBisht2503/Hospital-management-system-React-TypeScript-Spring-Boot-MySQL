export interface DoctorFilters {
  search: string;
  specialtyId: number | null;
  branchId: number | null;
  page: number;
}

export const DEFAULT_DOCTOR_FILTERS: DoctorFilters = {
  search: '',
  specialtyId: null,
  branchId: null,
  page: 0,
};

export function parseDoctorFilters(params: URLSearchParams): DoctorFilters {
  const rawSpecialty = params.get('specialtyId');
  const rawBranch = params.get('branchId');
  const rawPage = params.get('page');
  const page = rawPage ? Number(rawPage) : 0;

  return {
    search: params.get('search') ?? '',
    specialtyId: rawSpecialty ? Number(rawSpecialty) : null,
    branchId: rawBranch ? Number(rawBranch) : null,
    page: Number.isFinite(page) && page > 0 ? Math.floor(page) : 0,
  };
}

export function serializeDoctorFilters(filters: DoctorFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.search.trim()) params.set('search', filters.search.trim());
  if (filters.specialtyId != null) params.set('specialtyId', String(filters.specialtyId));
  if (filters.branchId != null) params.set('branchId', String(filters.branchId));
  if (filters.page > 0) params.set('page', String(filters.page));
  return params;
}

/** Any filter change resets pagination to the first page. */
export function applyFilterChange(filters: DoctorFilters, patch: Partial<Omit<DoctorFilters, 'page'>>): DoctorFilters {
  return { ...filters, ...patch, page: 0 };
}
