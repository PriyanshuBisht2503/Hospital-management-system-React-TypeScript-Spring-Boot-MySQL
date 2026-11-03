import React from 'react';
import { Badge } from '@hms/ui';
import { getStatusMeta } from '../lib/appointmentStatus';
import type { AppointmentStatus } from '../types/appointment';

export interface StatusBadgeProps {
  status: AppointmentStatus;
  needsRescheduling?: boolean;
}

/** Renders the appointment status label (text + tone) and a "Needs rescheduling" badge when flagged. */
export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, needsRescheduling = false }) => {
  const meta = getStatusMeta(status);

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
      <Badge variant={meta.tone}>{meta.label}</Badge>
      {needsRescheduling && <Badge variant="warning">Needs rescheduling</Badge>}
    </span>
  );
};
