import React from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getAppointment } from '../api/appointments';
import { ApiError } from '../types/auth';
import { formatIstDateTime } from '../lib/time';
import { StatusBadge } from '../components/StatusBadge';
import { Button, Card, Badge } from '@hms/ui';
import { ArrowLeft } from 'lucide-react';

const fmt = (v: string | null) => (v ? formatIstDateTime(v) : '—');

export const AppointmentDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const appointmentId = Number(id);
  const validId = Number.isInteger(appointmentId) && appointmentId > 0;

  const query = useQuery({
    queryKey: ['appointment', appointmentId],
    queryFn: () => getAppointment(appointmentId),
    enabled: validId,
  });

  if (!validId) {
    return (
      <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
        <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>Appointment not found</h1>
      </Card>
    );
  }

  if (query.isError) {
    if (query.error instanceof ApiError && query.error.status === 404) {
      return (
        <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
          <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>Appointment not found</h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>This appointment does not exist or you do not have access to it.</p>
        </Card>
      );
    }
    return (
      <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
        <p>Failed to load appointment: {(query.error as Error)?.message}</p>
        <Button variant="outline" size="sm" onClick={() => query.refetch()}>Retry</Button>
      </div>
    );
  }

  if (query.isLoading) {
    return <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>Loading appointment...</div>;
  }

  const a = query.data!;

  const field = (label: string, value: React.ReactNode) => (
    <div style={{ fontSize: '14px', color: '#334155' }}>
      <span style={{ color: '#64748b' }}>{label}: </span>
      {value}
    </div>
  );

  return (
    <div>
      <Button variant="outline" size="sm" onClick={() => navigate('/appointments')} style={{ marginBottom: '16px' }}>
        <ArrowLeft size={14} />
        Back to Appointments
      </Button>

      <Card style={{ padding: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
          <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a', margin: 0 }}>Appointment #{a.id}</h1>
          <div style={{ display: 'inline-flex', gap: '8px' }}>
            {a.needsRescheduling && <Badge variant="warning">Needs rescheduling</Badge>}
            <StatusBadge status={a.status} />
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '12px' }}>
          {field('Patient', a.patient.fullName)}
          {field('Doctor', a.doctorName ?? '—')}
          {field('Branch', a.branchName ?? '—')}
          {field('Type', a.type.replace('_', ' '))}
          {field('Start (IST)', fmt(a.startTime))}
          {field('End (IST)', fmt(a.endTime))}
          {field('Reason for visit', a.reasonForVisit ?? '—')}
          {field('Cancel reason', a.cancelReason ?? '—')}
          {field('Cancelled by', a.cancelledBy ?? '—')}
          {field('Created by', a.createdBy ?? '—')}
          {field('Rescheduled from', a.rescheduledFromId ? <Link to={`/appointments/${a.rescheduledFromId}`}>#{a.rescheduledFromId}</Link> : '—')}
          {field('Confirmed at (IST)', fmt(a.confirmedAt))}
          {field('Checked in at (IST)', fmt(a.checkedInAt))}
          {field('Started at (IST)', fmt(a.startedAt))}
          {field('Completed at (IST)', fmt(a.completedAt))}
          {field('Cancelled at (IST)', fmt(a.cancelledAt))}
          {field('Created at (IST)', fmt(a.createdAt))}
        </div>
      </Card>
    </div>
  );
};
