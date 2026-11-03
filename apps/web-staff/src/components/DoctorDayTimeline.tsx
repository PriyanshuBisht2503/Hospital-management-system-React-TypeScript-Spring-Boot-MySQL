import React from 'react';
import { Badge, Card } from '@hms/ui';
import { StatusBadge } from './StatusBadge';
import { AppointmentActions } from './AppointmentActions';
import { formatIstTime } from '../lib/time';
import { patientContactText } from '../lib/patientContact';
import type { Appointment } from '../types/appointment';
import type { Role } from '../types/auth';

export interface DoctorDayTimelineProps {
  appointments: Appointment[];
  role: Role;
  now: Date;
  hideCancelled: boolean;
  queryKey: unknown[];
}

/** Time-ordered IST timeline of a doctor's day, with per-row allowed actions. */
export const DoctorDayTimeline: React.FC<DoctorDayTimelineProps> = ({
  appointments,
  role,
  now,
  hideCancelled,
  queryKey,
}) => {
  const rows = [...appointments]
    .filter((a) => !hideCancelled || (a.status !== 'CANCELLED' && a.status !== 'RESCHEDULED'))
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());

  if (rows.length === 0) {
    return (
      <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
        No appointments for this day.
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {rows.map((a) => (
        <Card key={a.id} style={{ padding: '16px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px', flexWrap: 'wrap' }}>
            <div style={{ minWidth: '110px', fontWeight: 700, color: '#0f172a' }}>
              {formatIstTime(a.startTime)} – {formatIstTime(a.endTime)}
            </div>
            <div style={{ flex: 1, minWidth: '180px' }}>
              <div style={{ fontWeight: 600, color: '#0f172a' }}>{a.patient.fullName}</div>
              <div style={{ fontSize: '13px', color: '#64748b' }}>
                {patientContactText(a.patient.phone, role)}
              </div>
              <div style={{ fontSize: '13px', color: '#334155', marginTop: '4px' }}>
                {a.type.replace(/_/g, ' ')} · {a.reasonForVisit ?? 'No reason given'}
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-end' }}>
              <StatusBadge status={a.status} />
              {a.needsRescheduling && <Badge variant="warning">Needs rescheduling</Badge>}
            </div>
          </div>
          <div style={{ marginTop: '12px' }}>
            <AppointmentActions appointment={a} role={role} now={now} queryKey={queryKey} />
          </div>
        </Card>
      ))}
    </div>
  );
};

export interface DaySummaryStripProps {
  appointments: Appointment[];
}

/** Counts for the day: total, checked in, in consultation, completed, cancelled/no-show. */
export const DaySummaryStrip: React.FC<DaySummaryStripProps> = ({ appointments }) => {
  const items = [
    { label: 'Total', value: appointments.length },
    { label: 'Checked in', value: appointments.filter((a) => a.status === 'CHECKED_IN').length },
    { label: 'In consultation', value: appointments.filter((a) => a.status === 'IN_CONSULTATION').length },
    { label: 'Completed', value: appointments.filter((a) => a.status === 'COMPLETED').length },
    {
      label: 'Cancelled / no-show',
      value: appointments.filter((a) => a.status === 'CANCELLED' || a.status === 'NO_SHOW').length,
    },
  ];

  return (
    <div role="group" aria-label="Day summary" style={{ display: 'flex', flexWrap: 'wrap', gap: '12px' }}>
      {items.map((item) => (
        <Card key={item.label} style={{ padding: '12px 16px', minWidth: '120px' }}>
          <div style={{ fontSize: '12px', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            {item.label}
          </div>
          <div style={{ fontSize: '22px', fontWeight: 700, color: '#0f172a' }}>{item.value}</div>
        </Card>
      ))}
    </div>
  );
};
