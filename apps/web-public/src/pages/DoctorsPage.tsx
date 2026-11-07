import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Input, Pagination, Select } from '@hms/ui';
import { publicApi } from '../lib/api';
import { DoctorCard } from '../components/DoctorCard';
import { applyFilterChange, parseDoctorFilters, serializeDoctorFilters } from '../lib/filters';
import type { DoctorFilters } from '../lib/filters';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export const PAGE_SIZE = 6;

export const DoctorsPage: React.FC = () => {
  useDocumentTitle('Doctors');
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => parseDoctorFilters(searchParams), [searchParams]);
  const [searchInput, setSearchInput] = useState(filters.search);
  const debouncedSearch = useDebouncedValue(searchInput, 400);

  // Keep the text box in sync when the URL changes externally (e.g. Home search).
  useEffect(() => {
    setSearchInput(filters.search);
  }, [filters.search]);

  // Push the debounced search into the URL, resetting to page 0.
  useEffect(() => {
    if (debouncedSearch === filters.search) return;
    const next = serializeDoctorFilters(applyFilterChange(filters, { search: debouncedSearch }));
    setSearchParams(next, { replace: true });
  }, [debouncedSearch, filters, setSearchParams]);

  const specialtiesQuery = useQuery({ queryKey: ['specialties', 'filters'], queryFn: () => publicApi.listSpecialties({ size: 100 }) });
  const branchesQuery = useQuery({ queryKey: ['branches', 'filters'], queryFn: () => publicApi.listBranches({ size: 100 }) });

  const doctorsQuery = useQuery({
    queryKey: ['doctors', filters.search, filters.specialtyId, filters.branchId, filters.page],
    queryFn: () =>
      publicApi.searchDoctors({
        search: filters.search || undefined,
        specialtyId: filters.specialtyId ?? undefined,
        branchId: filters.branchId ?? undefined,
        page: filters.page,
        size: PAGE_SIZE,
      }),
  });

  const setFilter = (patch: Partial<Omit<DoctorFilters, 'page'>>) => {
    setSearchParams(serializeDoctorFilters(applyFilterChange(filters, patch)));
  };

  return (
    <div>
      <h1 style={{ margin: '0 0 6px', fontSize: '30px', color: '#0f172a' }}>Doctors</h1>
      <p style={{ margin: '0 0 20px', color: '#64748b' }}>Search by name, specialty or branch.</p>

      <section aria-label="Doctor filters" style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: '24px' }}>
        <div style={{ flex: 2, minWidth: '220px' }}>
          <Input label="Search" value={searchInput} onChange={(e) => setSearchInput(e.target.value)} placeholder="Doctor name" />
        </div>
        <div style={{ flex: 1, minWidth: '180px' }}>
          <Select
            label="Specialty"
            placeholder="Any specialty"
            options={(specialtiesQuery.data?.content ?? []).map((s) => ({ value: s.id, label: s.name }))}
            value={filters.specialtyId ?? ''}
            onChange={(e) => setFilter({ specialtyId: e.target.value ? Number(e.target.value) : null })}
          />
        </div>
        <div style={{ flex: 1, minWidth: '180px' }}>
          <Select
            label="Branch"
            placeholder="Any branch"
            options={(branchesQuery.data?.content ?? []).map((b) => ({ value: b.id, label: b.name }))}
            value={filters.branchId ?? ''}
            onChange={(e) => setFilter({ branchId: e.target.value ? Number(e.target.value) : null })}
          />
        </div>
        <Button variant="outline" size="md" onClick={() => { setSearchInput(''); setSearchParams(new URLSearchParams()); }}>
          Clear
        </Button>
      </section>

      {doctorsQuery.isLoading ? (
        <p role="status">Loading doctors…</p>
      ) : doctorsQuery.isError ? (
        <div role="alert" style={{ color: '#b91c1c' }}>
          <p>Could not load doctors.</p>
          <Button variant="outline" size="sm" onClick={() => doctorsQuery.refetch()}>Retry</Button>
        </div>
      ) : (doctorsQuery.data?.content ?? []).length === 0 ? (
        <p>No doctors match your filters.</p>
      ) : (
        <>
          <div className="card-grid">
            {doctorsQuery.data!.content.map((doctor) => (
              <DoctorCard key={doctor.id} doctor={doctor} />
            ))}
          </div>
          <Pagination
            pageNumber={doctorsQuery.data!.pageNumber}
            pageSize={doctorsQuery.data!.pageSize}
            totalPages={doctorsQuery.data!.totalPages}
            totalElements={doctorsQuery.data!.totalElements}
            isFirst={doctorsQuery.data!.isFirst}
            isLast={doctorsQuery.data!.isLast}
            onPageChange={(page) => setSearchParams(serializeDoctorFilters({ ...filters, page }))}
          />
        </>
      )}
    </div>
  );
};
