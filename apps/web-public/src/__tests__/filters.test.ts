import { describe, it, expect } from 'vitest';
import { applyFilterChange, parseDoctorFilters, serializeDoctorFilters } from '../lib/filters';

describe('doctor filters <-> URL', () => {
  it('round-trips parse and serialize', () => {
    const params = new URLSearchParams('search=Sarah&specialtyId=3&branchId=2&page=4');
    const filters = parseDoctorFilters(params);
    expect(filters).toEqual({ search: 'Sarah', specialtyId: 3, branchId: 2, page: 4 });
    expect(serializeDoctorFilters(filters).toString()).toBe('search=Sarah&specialtyId=3&branchId=2&page=4');
  });

  it('treats a missing or invalid page as page 0', () => {
    expect(parseDoctorFilters(new URLSearchParams('')).page).toBe(0);
    expect(parseDoctorFilters(new URLSearchParams('page=-3')).page).toBe(0);
  });

  it('resets the page to 0 on any filter change', () => {
    const current = { search: '', specialtyId: null, branchId: null, page: 5 };
    expect(applyFilterChange(current, { specialtyId: 9 })).toEqual({ search: '', specialtyId: 9, branchId: null, page: 0 });
  });
});
