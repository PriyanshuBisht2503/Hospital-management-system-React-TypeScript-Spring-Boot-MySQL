import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Table, TableHeader, TableBody, TableRow, TableHead, TableCell, Badge } from '@hms/ui';
import { listAppointments } from '../api/appointments';
import { endOfIstDayUtc, formatIstDateTime, istTimeToUtc, startOfIstDayUtc } from '../lib/time';
import { getStatusMeta } from '../lib/appointmentStatus';

export interface ForceExceptionDialogProps {
  doctorId: number;
  date: string;
  startTime?: string | null;
  endTime?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
  isSubmitting: boolean;
}

/**
 * Shown when creating an exception returns 409. Fetches the appointments that
 * would be affected and asks for confirmation; confirming re-sends with force=true.
 */
export const ForceExceptionDialog: React.FC<ForceExceptionDialogProps> = ({
  doctorId,
  date,
  startTime,
  endTime,
  onConfirm,
  onCancel,
  isSubmitting,
}) => {
  const from = startTime ? istTimeToUtc(date, startTime) : startOfIstDayUtc(date);
  const to = endTime ? istTimeToUtc(date, endTime) : endOfIstDayUtc(date);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['affected-appointments', doctorId, from, to],
    queryFn: () => listAppointments({ doctorId, from, to, size: 100 }),
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <p style={{ margin: 0, fontSize: '14px', color: '#334155', lineHeight: 1.5 }}>
        These appointments fall in the requested window. Confirming will flag them{' '}
        <strong>“needs rescheduling”</strong> — it will <strong>not</strong> cancel them.
      </p>

      {isLoading ? (
        <div style={{ padding: '24px', textAlign: 'center', color: '#64748b' }}>Loading affected appointments...</div>
      ) : isError ? (
        <div style={{ padding: '24px', textAlign: 'center', color: '#ef4444' }}>Failed to load affected appointments.</div>
      ) : !data || data.content.length === 0 ? (
        <div style={{ padding: '24px', textAlign: 'center', color: '#64748b' }}>No affected appointments found.</div>
      ) : (
        <div style={{ maxHeight: '240px', overflowY: 'auto' }}>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Patient</TableHead>
                <TableHead>Time (IST)</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((a) => (
                <TableRow key={a.id}>
                  <TableCell style={{ fontSize: '13px' }}>{a.patient.fullName}</TableCell>
                  <TableCell style={{ fontSize: '13px' }}>{formatIstDateTime(a.startTime)}</TableCell>
                  <TableCell>
                    <Badge variant={getStatusMeta(a.status).tone}>{getStatusMeta(a.status).label}</Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
        <Button type="button" variant="outline" size="md" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="button" variant="primary" size="md" onClick={onConfirm} disabled={isSubmitting}>
          {isSubmitting ? 'Saving...' : 'Confirm & Mark for Rescheduling'}
        </Button>
      </div>
    </div>
  );
};
