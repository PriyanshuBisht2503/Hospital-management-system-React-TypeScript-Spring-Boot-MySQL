import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { getPatient } from '../api/patients';
import { listAppointments } from '../api/appointments';
import { patientContactText } from '../lib/patientContact';
import { formatIstDateTime } from '../lib/time';
import { ApiError } from '../types/auth';
import type { Patient } from '../types/patient';
import type { Appointment } from '../types/appointment';
import type { PageResponse } from '../types/masterData';
import { StatusBadge } from '../components/StatusBadge';
import { PatientNotFound } from '../components/PatientNotFound';
import {
  Button,
  Card,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  Pagination,
  DEFAULT_PAGE_SIZE,
} from '@hms/ui';
import { ArrowLeft } from 'lucide-react';

export const PatientDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const role = user?.role ?? 'RECEPTIONIST';
  const patientId = Number(id);
  const validId = Number.isInteger(patientId) && patientId > 0;

  const [page, setPage] = useState(0);

  const patientQuery = useQuery<Patient>({
    queryKey: ['patient', patientId],
    queryFn: () => getPatient(patientId),
    enabled: validId,
  });

  const appointmentsQuery = useQuery<PageResponse<Appointment>>({
    queryKey: ['patient-appointments', patientId, page, DEFAULT_PAGE_SIZE],
    queryFn: () => listAppointments({ patientId, page, size: DEFAULT_PAGE_SIZE }),
    enabled: validId,
  });

  if (!validId) {
    return <PatientNotFound />;
  }

  if (patientQuery.isError) {
    if (patientQuery.error instanceof ApiError && patientQuery.error.status === 404) {
      return <PatientNotFound />;
    }
    return (
      <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
        <p>Failed to load patient: {(patientQuery.error as Error)?.message}</p>
        <Button variant="outline" size="sm" onClick={() => patientQuery.refetch()}>
          Retry
        </Button>
      </div>
    );
  }

  if (patientQuery.isLoading) {
    return <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>Loading patient...</div>;
  }

  const patient = patientQuery.data!;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '8px' }}>
        <Button variant="outline" size="sm" onClick={() => navigate('/patients')}>
          <ArrowLeft size={14} />
          Back to Patients
        </Button>
        {(role === 'ADMIN' || role === 'RECEPTIONIST') && (
          <Button variant="primary" size="sm" onClick={() => navigate(`/appointments/new?patientId=${patient.id}`)}>
            Book Appointment
          </Button>
        )}
      </div>

      <Card style={{ padding: '24px', marginBottom: '20px' }}>
        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a', margin: '0 0 12px' }}>{patient.fullName}</h1>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '24px', fontSize: '14px', color: '#334155' }}>
          <div>
            <span style={{ color: '#64748b' }}>Phone: </span>
            {patientContactText(patient.phone, role)}
          </div>
          <div>
            <span style={{ color: '#64748b' }}>Email: </span>
            {patientContactText(patient.email, role)}
          </div>
          <div>
            <span style={{ color: '#64748b' }}>Date of birth: </span>
            {patient.dateOfBirth ?? '—'}
          </div>
          <div>
            <span style={{ color: '#64748b' }}>Age: </span>
            {patient.age != null ? patient.age : '—'}
          </div>
          <div>
            <span style={{ color: '#64748b' }}>Gender: </span>
            {patient.gender ? patient.gender.toLowerCase() : '—'}
          </div>
        </div>
      </Card>

      <Card>
        <div style={{ padding: '16px' }}>
          <h2 style={{ fontSize: '16px', fontWeight: 700, color: '#0f172a', margin: 0 }}>Appointment History</h2>
        </div>
        {appointmentsQuery.isLoading ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>Loading appointments...</div>
        ) : appointmentsQuery.isError ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load appointments</p>
            <Button variant="outline" size="sm" onClick={() => appointmentsQuery.refetch()}>
              Retry
            </Button>
          </div>
        ) : !appointmentsQuery.data || appointmentsQuery.data.content.length === 0 ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>No appointments found.</div>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Date & Time (IST)</TableHead>
                    <TableHead>Doctor</TableHead>
                    <TableHead>Branch</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {appointmentsQuery.data.content.map((appt) => (
                    <TableRow key={appt.id}>
                      <TableCell style={{ fontSize: '13px' }}>{formatIstDateTime(appt.startTime)}</TableCell>
                      <TableCell style={{ fontSize: '13px' }}>{appt.doctorName ?? '—'}</TableCell>
                      <TableCell style={{ fontSize: '13px' }}>{appt.branchName ?? '—'}</TableCell>
                      <TableCell>
                        <StatusBadge status={appt.status} needsRescheduling={appt.needsRescheduling} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <Pagination
              pageNumber={appointmentsQuery.data.pageNumber}
              pageSize={appointmentsQuery.data.pageSize}
              totalPages={appointmentsQuery.data.totalPages}
              totalElements={appointmentsQuery.data.totalElements}
              isFirst={appointmentsQuery.data.isFirst}
              isLast={appointmentsQuery.data.isLast}
              onPageChange={setPage}
            />
          </>
        )}
      </Card>
    </div>
  );
};
