import React, { useState } from 'react';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { listAdminDoctors, searchDoctors } from '../api/doctors';
import type { PageResponse } from '../types/masterData';
import type { Specialty, Branch } from '../types/masterData';
import { Button, Card, Input, Table, TableHeader, TableBody, TableRow, TableHead, TableCell, Pagination, DEFAULT_PAGE_SIZE } from '@hms/ui';
import { Search, Eye } from 'lucide-react';

interface ScheduleDoctorRow {
  id: number;
  fullName: string;
  specialties: Specialty[];
  branches: Branch[];
}

export const SchedulesPage: React.FC = () => {
  useDocumentTitle('Schedules');
  const { user } = useAuth();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const role = user?.role;
  const isAdmin = role === 'ADMIN';
  const isDoctor = role === 'DOCTOR';

  if (isDoctor) {
    if (user?.doctorId != null) {
      return <Navigate to={`/schedules/${user.doctorId}`} replace />;
    }
    return (
      <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
        <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>No doctor profile is linked to your account</h1>
        <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>Ask an administrator to create a doctor profile for you to manage a schedule.</p>
      </Card>
    );
  }

  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<ScheduleDoctorRow>>({
    queryKey: ['schedule-doctors', role, page, search, DEFAULT_PAGE_SIZE],
    queryFn: async () => {
      const res = isAdmin
        ? await listAdminDoctors({ search, page, size: DEFAULT_PAGE_SIZE })
        : await searchDoctors({ search, page, size: DEFAULT_PAGE_SIZE });
      return {
        ...res,
        content: res.content.map((d) => ({ id: d.id, fullName: d.fullName, specialties: d.specialties, branches: d.branches })),
      };
    },
    enabled: role === 'ADMIN' || role === 'RECEPTIONIST',
  });

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(searchInput.trim());
  };

  return (
    <div>
      <div style={{ marginBottom: '24px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>Doctor Schedules</h1>
        <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
          {isAdmin ? 'Manage weekly schedule templates and exceptions for any doctor' : 'View doctor weekly schedules'}
        </p>
      </div>

      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '240px' }}>
            <Input placeholder="Search doctors by name..." value={searchInput} onChange={(e) => setSearchInput(e.target.value)} />
          </div>
          <Button type="submit" variant="secondary" size="md">
            <Search size={16} />
            Search
          </Button>
          {search && (
            <Button type="button" variant="outline" size="md" onClick={() => { setSearchInput(''); setSearch(''); setPage(0); }}>
              Clear
            </Button>
          )}
        </form>
      </Card>

      <Card>
        {isLoading ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>Loading doctors...</div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load doctors: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>Retry</Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            No doctors found{search ? ` matching "${search}"` : ''}.
          </div>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Doctor</TableHead>
                    <TableHead>Specialties</TableHead>
                    <TableHead>Branches</TableHead>
                    <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((doctor) => (
                    <TableRow key={doctor.id}>
                      <TableCell>
                        <div style={{ fontWeight: 600, color: '#0f172a' }}>{doctor.fullName}</div>
                      </TableCell>
                      <TableCell style={{ fontSize: '13px' }}>
                        {doctor.specialties.map((s) => s.name).join(', ') || '—'}
                      </TableCell>
                      <TableCell style={{ fontSize: '13px' }}>
                        {doctor.branches.map((b) => b.name).join(', ') || '—'}
                      </TableCell>
                      <TableCell style={{ textAlign: 'right' }}>
                        <Button variant="outline" size="sm" onClick={() => navigate(`/schedules/${doctor.id}`)} title="View schedule">
                          <Eye size={14} />
                          Schedule
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <Pagination
              pageNumber={data.pageNumber}
              pageSize={data.pageSize}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              isFirst={data.isFirst}
              isLast={data.isLast}
              onPageChange={setPage}
            />
          </>
        )}
      </Card>
    </div>
  );
};
