import type { AppointmentStatus } from '../types/appointment';
import type { Role } from '../types/auth';
import { toIstDateString } from './time';

export type BadgeTone = 'success' | 'danger' | 'warning' | 'info' | 'neutral';

export interface StatusMeta {
  label: string;
  tone: BadgeTone;
  description: string;
}

export const APPOINTMENT_STATUS_META: Record<AppointmentStatus, StatusMeta> = {
  BOOKED: { label: 'Booked', tone: 'info', description: 'Booking received, awaiting confirmation' },
  CONFIRMED: { label: 'Confirmed', tone: 'success', description: 'Appointment confirmed' },
  CHECKED_IN: { label: 'Checked in', tone: 'info', description: 'Patient has arrived' },
  IN_CONSULTATION: { label: 'In consultation', tone: 'warning', description: 'Consultation in progress' },
  COMPLETED: { label: 'Completed', tone: 'success', description: 'Consultation completed' },
  CANCELLED: { label: 'Cancelled', tone: 'danger', description: 'Appointment cancelled' },
  NO_SHOW: { label: 'No-show', tone: 'danger', description: 'Patient did not attend' },
  RESCHEDULED: { label: 'Rescheduled', tone: 'neutral', description: 'Moved to a new slot' },
};

/**
 * Mirrors the backend `AppointmentStateMachine.ALLOWED_TRANSITIONS`.
 * Terminal statuses (COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED) have no outgoing transitions.
 */
export const ALLOWED_TRANSITIONS: Record<AppointmentStatus, AppointmentStatus[]> = {
  BOOKED: ['CONFIRMED', 'CANCELLED', 'RESCHEDULED'],
  CONFIRMED: ['CHECKED_IN', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED'],
  CHECKED_IN: ['IN_CONSULTATION', 'CANCELLED'],
  IN_CONSULTATION: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: [],
  NO_SHOW: [],
  RESCHEDULED: [],
};

export type AppointmentActionKey =
  | 'CONFIRM'
  | 'CHECK_IN'
  | 'START_CONSULTATION'
  | 'COMPLETE'
  | 'NO_SHOW'
  | 'CANCEL'
  | 'RESCHEDULE';

export type TimeHint = 'check-in-day' | 'after-start';

export interface AppointmentAction {
  key: AppointmentActionKey;
  label: string;
  toStatus: AppointmentStatus;
  roles: Role[];
  timeHint?: TimeHint;
}

/** Action → allowed roles, matching the backend endpoint @PreAuthorize rules. */
export const APPOINTMENT_ACTIONS: AppointmentAction[] = [
  { key: 'CONFIRM', label: 'Confirm', toStatus: 'CONFIRMED', roles: ['ADMIN', 'RECEPTIONIST'] },
  { key: 'CHECK_IN', label: 'Check in', toStatus: 'CHECKED_IN', roles: ['ADMIN', 'RECEPTIONIST'], timeHint: 'check-in-day' },
  { key: 'START_CONSULTATION', label: 'Start consultation', toStatus: 'IN_CONSULTATION', roles: ['ADMIN', 'DOCTOR'] },
  { key: 'COMPLETE', label: 'Complete', toStatus: 'COMPLETED', roles: ['ADMIN', 'DOCTOR'] },
  { key: 'NO_SHOW', label: 'No-show', toStatus: 'NO_SHOW', roles: ['ADMIN', 'RECEPTIONIST', 'DOCTOR'], timeHint: 'after-start' },
  { key: 'CANCEL', label: 'Cancel', toStatus: 'CANCELLED', roles: ['ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT'] },
  { key: 'RESCHEDULE', label: 'Reschedule', toStatus: 'RESCHEDULED', roles: ['ADMIN', 'RECEPTIONIST', 'PATIENT'] },
];

export function getStatusMeta(status: AppointmentStatus): StatusMeta {
  return APPOINTMENT_STATUS_META[status];
}

/** Actions permitted by the transition table AND the caller's role (time hints not applied). */
export function getAllowedActions(status: AppointmentStatus, role: Role): AppointmentAction[] {
  const reachable = ALLOWED_TRANSITIONS[status] ?? [];
  return APPOINTMENT_ACTIONS.filter(
    (action) => reachable.includes(action.toStatus) && action.roles.includes(role),
  );
}

export function isCheckInDay(startTime: string, now: Date = new Date()): boolean {
  return toIstDateString(startTime) === toIstDateString(now);
}

export function isAfterStart(startTime: string, now: Date = new Date()): boolean {
  return now.getTime() >= new Date(startTime).getTime();
}

export interface ActionDisabledState {
  disabled: boolean;
  tooltip: string | null;
}

/**
 * Time hint state for an allowed action. The button is disabled with a tooltip,
 * but the server still decides and its error message is shown on a 409.
 */
export function getActionDisabledState(
  action: AppointmentAction,
  startTime: string,
  now: Date = new Date(),
): ActionDisabledState {
  if (action.timeHint === 'check-in-day' && !isCheckInDay(startTime, now)) {
    return { disabled: true, tooltip: 'Check-in is only available on the appointment date (IST)' };
  }
  if (action.timeHint === 'after-start' && !isAfterStart(startTime, now)) {
    return { disabled: true, tooltip: 'No-show can only be marked after the appointment start time' };
  }
  return { disabled: false, tooltip: null };
}

export interface AppointmentBadge {
  status: AppointmentStatus;
  label: string;
  tone: BadgeTone;
  needsRescheduling: boolean;
}

export function getAppointmentBadge(appointment: {
  status: AppointmentStatus;
  needsRescheduling: boolean;
}): AppointmentBadge {
  const meta = getStatusMeta(appointment.status);
  return {
    status: appointment.status,
    label: meta.label,
    tone: meta.tone,
    needsRescheduling: appointment.needsRescheduling,
  };
}
