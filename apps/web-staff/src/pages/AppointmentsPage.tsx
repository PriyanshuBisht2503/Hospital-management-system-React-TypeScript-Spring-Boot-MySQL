import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { listAppointments } from '../api/appointments';
import { searchDoctors } from '../api/doctors';
import { listBranches } from '../api/branches';
import { PatientSearch } from '../components/PatientSearch';
import { StatusBadge } from '../components/StatusBadge';
import { endOfIstDayUtc, formatIstDateTime, startOfIstDayUtc } from '../lib/time';
import { Button, Card, Input, Select, Table, TableHeader, TableBody, TableRow, TableHead, TableCell, Badge, Pagination, DEFAULT_PAGE_SIZE } from '@hms/ui';
import { Plus, Eye } from 'lucide-react';
import type { Appointment, AppointmentStatus } from '../types/appointment';
import type { PageResponse } from '../types/masterData';

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  { value: 'BOOKED', label: 'Booked' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'CHECKED_IN', label: 'Checked in' },
  { value: 'IN_CONSULTATION', label: 'In consultation' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'NO_SHOW', label: 'No-show' },
  { value: 'RESCHEDULED', label: 'Rescheduled' },
];

export const AppointmentsPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const isDoctor = user?.role === 'DOCTOR';
  const canCreate = user?.role === 'ADMIN' || user?.role === 'RECEPTIONIST';

  const from = searchParams.get('from') ?? '';
  const to = searchParams.get('to') ?? '';
  const doctorId = searchParams.get('doctorId') ? Number(searchParams.get('doctorId')) : undefined;
  const patientId = searchParams.get('patientId') ? Number(searchParams.get('patientId')) : undefined;
  const status = searchParams.get('status') ?? '';
  const branchId = searchParams.get('branchId') ? Number(searchParams.get('branchId')) : undefined;
  const needsRescheduling = searchParams.get('needsRescheduling') === 'true' ? true : searchParams.get('needsRescheduling') === 'false' ? false : undefined;
  const page = searchParams.get('page') ? Number(searchParams.get('page')) : 0;

  const setParam = (key: string, value: string | undefined) => {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    next.delete('page');
    setSearchParams(next);
  };

  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<Appointment>>({
    queryKey: ['appointments', from, to, doctorId, patientId, status, branchId, needsRescheduling, page],
    queryFn: () => listAppointments({
      doctorId: isDoctor ? undefined : doctorId,
      branchId,
      status: (status || undefined) as AppointmentStatus | undefined,
      patientId,
      needsRescheduling,
      from: from ? startOfIstDayUtc(from) : undefined,
      to: to ? endOfIstDayUtc(to) : undefined,
      page,
      size: DEFAULT_PAGE_SIZE,
    }),
  });

  const doctorsQuery = useQuery({ queryKey: ['appt-doctors'], queryFn: () => searchDoctors({ size: 100 }), enabled: !isDoctor });
  const branchesQuery = useQuery({ queryKey: ['appt-branches'], queryFn: () => listBranches({ size: 100 }) });

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>Appointments</h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>Search and review appointments</p>
        </div>
        {canCreate && (
          <Button variant="primary" size="md" onClick={() => navigate('/appointments/new')}>
            <Plus size={16} />
            New Appointment
          </Button>
        )}
      </div>

      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div style={{ width: '170px' }}>
            <Input label="From (IST)" type="date" value={from} onChange={(e) => setParam('from', e.target.value)} />
          </div>
          <div style={{ width: '170px' }}>
            <Input label="To (IST)" type="date" value={to} onChange={(e) => setParam('to', e.target.value)} />
          </div>
          {!isDoctor && (
            <div style={{ width: '200px' }}>
              <Select label="Doctor" placeholder="All doctors" options={(doctorsQuery.data?.content ?? []).map((d) => ({ value: d.id, label: d.fullName }))} value={doctorId ?? ''} onChange={(e) => setParam('doctorId', e.target.value || undefined)} />
            </div>
          )}
          <div style={{ width: '180px' }}>
            <Select label="Branch" placeholder="All branches" options={(branchesQuery.data?.content ?? []).map((b) => ({ value: b.id, label: b.name }))} value={branchId ?? ''} onChange={(e) => setParam('branchId', e.target.value || undefined)} />
          </div>
          <div style={{ width: '180px' }}>
            <Select label="Status" options={STATUS_OPTIONS} value={status} onChange={(e) => setParam('status', e.target.value || undefined)} />
          </div>
          <div style={{ width: '170px' }}>
            <Select label="Rescheduling" options={[{ value: '', label: 'Any' }, { value: 'true', label: 'Needs rescheduling' }, { value: 'false', label: 'Not flagged' }]} value={needsRescheduling === undefined ? '' : String(needsRescheduling)} onChange={(e) => setParam('needsRescheduling', e.target.value || undefined)} />
          </div>
          <div style={{ flex: 1, minWidth: '200px' }}>
            <label style={{ fontSize: '13px', fontWeight: 600, color: '#334155', display: 'block', marginBottom: '6px' }}>Patient</label>
            <PatientSearch placeholder="Search patient..." onSelect={(p) => setParam('patientId', String(p.id))} />
          </div>
          {(from || to || doctorId || patientId || status || branchId || needsRescheduling !== undefined) && (
            <Button variant="outline" size="md" onClick={() => setSearchParams(new URLSearchParams())}>Clear</Button>
          )}
        </div>
      </Card>

      <Card>
        {isLoading ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>Loading appointments...</div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load appointments: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>Retry</Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>No appointments found.</div>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Patient</TableHead>
                    <TableHead>Doctor</TableHead>
                    <TableHead>Branch</TableHead>
                    <TableHead>Date & Time (IST)</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((a) => (
                    <TableRow key={a.id}>
                      <TableCell>
                        <div style={{ fontWeight: 600, color: '#0f172a' }}>{a.patient.fullName}</div>
                        {a.needsRescheduling && <Badge variant="warning">Needs rescheduling</Badge>}
                      </TableCell>
                      <TableCell style={{ fontSize: '13px' }}>{a.doctorName ?? '—'}</TableCell>
                      <TableCell style={{ fontSize: '13px' }}>{a.branchName ?? '—'}</TableCell>
                      <TableCell style={{ fontSize: '13px' }}>{formatIstDateTime(a.startTime)}</TableCell>
                      <TableCell><StatusBadge status={a.status} /></TableCell>
                      <TableCell style={{ textAlign: 'right' }}>
                        <Button variant="outline" size="sm" title="View appointment" onClick={() => navigate(`/appointments/${a.id}`)}>
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
              onPageChange={(p) => setParam('page', String(p))}
            />
          </>
        )}
      </Card>
    </div>
  );
};
