import React, { useState } from 'react';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { getAppointment } from '../api/appointments';
import { ApiError } from '../types/auth';
import { formatIstDateTime } from '../lib/time';
import { StatusBadge } from '../components/StatusBadge';
import { AppointmentActions } from '../components/AppointmentActions';
import { Button, Card, Badge } from '@hms/ui';
import { ArrowLeft, AlertTriangle, CalendarClock } from 'lucide-react';

const fmt = (v: string | null) => (v ? formatIstDateTime(v) : '—');

export const AppointmentDetailPage: React.FC = () => {
  useDocumentTitle('Appointment');
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const appointmentId = Number(id);
  const validId = Number.isInteger(appointmentId) && appointmentId > 0;

  const queryKey = ['appointment', appointmentId];

  const query = useQuery({
    queryKey,
    queryFn: () => getAppointment(appointmentId),
    enabled: validId,
  });

  // Controlled open state for the reschedule dialog — the banner button also triggers it.
  const [rescheduleOpen, setRescheduleOpen] = useState(false);

  if (!validId) {
    return (
      <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
        <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>
          Appointment not found
        </h1>
      </Card>
    );
  }

  if (query.isError) {
    if (query.error instanceof ApiError && query.error.status === 404) {
      return (
        <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
          <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>
            Appointment not found
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            This appointment does not exist or you do not have access to it.
          </p>
        </Card>
      );
    }
    return (
      <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
        <p>Failed to load appointment: {(query.error as Error)?.message}</p>
        <Button variant="outline" size="sm" onClick={() => query.refetch()}>
          Retry
        </Button>
      </div>
    );
  }

  if (query.isLoading || !query.data) {
    return (
      <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
        Loading appointment...
      </div>
    );
  }

  const a = query.data;
  const role = user?.role ?? 'DOCTOR';
  const canReschedule = role === 'ADMIN' || role === 'RECEPTIONIST';

  const field = (label: string, value: React.ReactNode) => (
    <div style={{ fontSize: '14px', color: '#334155' }}>
      <span style={{ color: '#64748b' }}>{label}: </span>
      {value}
    </div>
  );

  return (
    <div>
      <Button
        variant="outline"
        size="sm"
        onClick={() => navigate('/appointments')}
        style={{ marginBottom: '16px' }}
      >
        <ArrowLeft size={14} />
        Back to Appointments
      </Button>

      {/* Needs-rescheduling banner */}
      {a.needsRescheduling && (
        <div
          role="alert"
          aria-label="Needs rescheduling warning"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            flexWrap: 'wrap',
            marginBottom: '16px',
            padding: '14px 18px',
            borderRadius: '10px',
            backgroundColor: '#fffbeb',
            border: '1.5px solid #fcd34d',
            color: '#92400e',
            fontSize: '14px',
            fontWeight: 500,
          }}
        >
          <AlertTriangle size={18} aria-hidden="true" style={{ flexShrink: 0 }} />
          <span style={{ flex: 1 }}>
            This appointment needs to be rescheduled because a doctor exception was added.
          </span>
          {canReschedule && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setRescheduleOpen(true)}
              style={{ borderColor: '#f59e0b', color: '#92400e' }}
            >
              <CalendarClock size={14} aria-hidden="true" />
              Reschedule now
            </Button>
          )}
        </div>
      )}

      <Card style={{ padding: '24px' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            flexWrap: 'wrap',
            gap: '12px',
            marginBottom: '20px',
          }}
        >
          <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a', margin: 0 }}>
            Appointment #{a.id}
          </h1>
          <div style={{ display: 'inline-flex', gap: '8px', alignItems: 'center' }}>
            {a.needsRescheduling && <Badge variant="warning">Needs rescheduling</Badge>}
            <StatusBadge status={a.status} />
          </div>
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
            gap: '12px',
            marginBottom: '20px',
          }}
        >
          {field('Patient', a.patient.fullName)}
          {field('Doctor', a.doctorName ?? '—')}
          {field('Branch', a.branchName ?? '—')}
          {field('Type', a.type.replace(/_/g, ' '))}
          {field('Start (IST)', fmt(a.startTime))}
          {field('End (IST)', fmt(a.endTime))}
          {field('Reason for visit', a.reasonForVisit ?? '—')}
          {a.cancelReason && field('Cancel reason', a.cancelReason)}
          {a.cancelledBy && field('Cancelled by', a.cancelledBy)}
          {field('Created by', a.createdBy ?? '—')}
          {a.rescheduledFromId &&
            field(
              'Rescheduled from',
              <Link to={`/appointments/${a.rescheduledFromId}`}>
                #{a.rescheduledFromId}
              </Link>,
            )}
          {a.confirmedAt && field('Confirmed at (IST)', fmt(a.confirmedAt))}
          {a.checkedInAt && field('Checked in at (IST)', fmt(a.checkedInAt))}
          {a.startedAt && field('Started at (IST)', fmt(a.startedAt))}
          {a.completedAt && field('Completed at (IST)', fmt(a.completedAt))}
          {a.cancelledAt && field('Cancelled at (IST)', fmt(a.cancelledAt))}
          {field('Created at (IST)', fmt(a.createdAt))}
        </div>

        {/* Action area */}
        <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: '16px' }}>
          <AppointmentActions
            appointment={a}
            role={role}
            now={new Date()}
            queryKey={queryKey}
            externalRescheduleOpen={rescheduleOpen}
            onExternalRescheduleClose={() => setRescheduleOpen(false)}
          />
        </div>
      </Card>
    </div>
  );
};
