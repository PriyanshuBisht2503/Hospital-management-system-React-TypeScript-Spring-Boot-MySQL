import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { listPatients } from '../api/patients';
import { patientContactText } from '../lib/patientContact';
import type { PageResponse } from '../types/masterData';
import type { Patient } from '../types/patient';
import {
  Button,
  Card,
  Input,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  Pagination,
  DEFAULT_PAGE_SIZE,
} from '@hms/ui';
import { Search, Plus, Eye } from 'lucide-react';

export const PatientsPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const role = user?.role ?? 'RECEPTIONIST';
  const canCreate = role === 'ADMIN' || role === 'RECEPTIONIST';

  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<Patient>>({
    queryKey: ['patients', page, search, DEFAULT_PAGE_SIZE],
    queryFn: () => listPatients({ search, page, size: DEFAULT_PAGE_SIZE }),
  });

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(searchInput.trim());
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '24px',
          flexWrap: 'wrap',
          gap: '12px',
        }}
      >
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>Patients</h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            {role === 'DOCTOR' ? 'Patients you have appointments with' : 'Patient directory'}
          </p>
        </div>
        {canCreate && (
          <Button variant="primary" size="md" onClick={() => navigate('/patients/new')}>
            <Plus size={16} />
            New Patient
          </Button>
        )}
      </div>

      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '240px' }}>
            <Input
              type="text"
              placeholder="Search by name, phone, or email..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>
          <Button type="submit" variant="secondary" size="md">
            <Search size={16} />
            Search
          </Button>
          {search && (
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={() => {
                setSearchInput('');
                setSearch('');
                setPage(0);
              }}
            >
              Clear
            </Button>
          )}
        </form>
      </Card>

      <Card>
        {isLoading ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>Loading patients...</div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load patients: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Retry
            </Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            No patients found{search ? ` matching "${search}"` : ''}.
          </div>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Phone</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Age</TableHead>
                    <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((patient) => (
                    <TableRow key={patient.id}>
                      <TableCell>
                        <div style={{ fontWeight: 600, color: '#0f172a' }}>{patient.fullName}</div>
                        {patient.gender && (
                          <div style={{ fontSize: '12px', color: '#64748b' }}>{patient.gender.toLowerCase()}</div>
                        )}
                      </TableCell>
                      <TableCell style={{ fontSize: '13px' }}>{patientContactText(patient.phone, role)}</TableCell>
                      <TableCell style={{ fontSize: '13px' }}>{patientContactText(patient.email, role)}</TableCell>
                      <TableCell style={{ fontSize: '13px' }}>{patient.age != null ? patient.age : '—'}</TableCell>
                      <TableCell style={{ textAlign: 'right' }}>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => navigate(`/patients/${patient.id}`)}
                          title="View patient"
                        >
                          <Eye size={14} />
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
