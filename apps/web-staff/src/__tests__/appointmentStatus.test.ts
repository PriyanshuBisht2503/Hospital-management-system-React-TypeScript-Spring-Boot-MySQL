import { describe, it, expect } from 'vitest';
import {
  ALLOWED_TRANSITIONS,
  APPOINTMENT_ACTIONS,
  APPOINTMENT_STATUS_META,
  getActionDisabledState,
  getAllowedActions,
  getAppointmentBadge,
  getStatusMeta,
} from '../lib/appointmentStatus';
import { VISIBLE_APPOINTMENT_TYPES } from '../types/appointment';
import type { AppointmentStatus } from '../types/appointment';
import type { Role } from '../types/auth';

const ALL_STATUSES: AppointmentStatus[] = [
  'BOOKED',
  'CONFIRMED',
  'CHECKED_IN',
  'IN_CONSULTATION',
  'COMPLETED',
  'CANCELLED',
  'NO_SHOW',
  'RESCHEDULED',
];

describe('appointmentStatus: status meta', () => {
  it('provides a non-empty label and tone for every status', () => {
    for (const status of ALL_STATUSES) {
      const meta = getStatusMeta(status);
      expect(meta.label).toBeTruthy();
      expect(meta.tone).toBeTruthy();
    }
  });

  it('exposes human-readable text labels (not colour only)', () => {
    expect(APPOINTMENT_STATUS_META.CONFIRMED.label).toBe('Confirmed');
    expect(APPOINTMENT_STATUS_META.NO_SHOW.label).toBe('No-show');
    expect(APPOINTMENT_STATUS_META.CHECKED_IN.label).toBe('Checked in');
  });
});

describe('appointmentStatus: transition table', () => {
  it('matches the backend AppointmentStateMachine exactly', () => {
    expect(ALLOWED_TRANSITIONS).toEqual({
      BOOKED: ['CONFIRMED', 'CANCELLED', 'RESCHEDULED'],
      CONFIRMED: ['CHECKED_IN', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED'],
      CHECKED_IN: ['IN_CONSULTATION', 'CANCELLED'],
      IN_CONSULTATION: ['COMPLETED', 'CANCELLED'],
      COMPLETED: [],
      CANCELLED: [],
      NO_SHOW: [],
      RESCHEDULED: [],
    });
  });

  it('treats COMPLETED, CANCELLED, NO_SHOW and RESCHEDULED as terminal', () => {
    for (const terminal of ['COMPLETED', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED'] as AppointmentStatus[]) {
      expect(ALLOWED_TRANSITIONS[terminal]).toEqual([]);
    }
  });
});

describe('appointmentStatus: per-role action visibility', () => {
  it('a DOCTOR never sees Reschedule or Check-in', () => {
    for (const status of ALL_STATUSES) {
      const keys = getAllowedActions(status, 'DOCTOR').map((a) => a.key);
      expect(keys).not.toContain('RESCHEDULE');
      expect(keys).not.toContain('CHECK_IN');
    }
  });

  it('a RECEPTIONIST can check-in and no-show but cannot start or complete a consultation', () => {
    const keys = getAllowedActions('CONFIRMED', 'RECEPTIONIST').map((a) => a.key);
    expect(keys).toContain('CHECK_IN');
    expect(keys).toContain('NO_SHOW');
    expect(keys).toContain('CANCEL');
    expect(keys).toContain('RESCHEDULE');
    expect(keys).not.toContain('START_CONSULTATION');
    expect(keys).not.toContain('COMPLETE');
  });

  it('an ADMIN can perform every action available for a CONFIRMED appointment', () => {
    const keys = getAllowedActions('CONFIRMED', 'ADMIN').map((a) => a.key);
    expect(keys).toEqual(expect.arrayContaining(['CHECK_IN', 'NO_SHOW', 'CANCEL', 'RESCHEDULE']));
  });

  it('a DOCTOR can start and complete a CHECKED_IN appointment', () => {
    expect(getAllowedActions('CHECKED_IN', 'DOCTOR').map((a) => a.key)).toEqual(['START_CONSULTATION']);
    expect(getAllowedActions('IN_CONSULTATION', 'DOCTOR').map((a) => a.key)).toEqual(['COMPLETE']);
  });

  it('terminal statuses expose no actions to any role', () => {
    for (const status of ['COMPLETED', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED'] as AppointmentStatus[]) {
      for (const role of ['ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PATIENT'] as Role[]) {
        expect(getAllowedActions(status, role)).toEqual([]);
      }
    }
  });
});

describe('appointmentStatus: time hints', () => {
  it('check-in is disabled before the appointment IST date and enabled only on that date', () => {
    const checkIn = APPOINTMENT_ACTIONS.find((a) => a.key === 'CHECK_IN')!;
    const startTime = '2026-11-03T04:00:00.000Z'; // 09:30 IST on 2026-11-03

    const before = getActionDisabledState(checkIn, startTime, new Date('2026-11-02T12:00:00.000Z'));
    expect(before.disabled).toBe(true);
    expect(before.tooltip).toBeTruthy();

    const onDate = getActionDisabledState(checkIn, startTime, new Date('2026-11-03T05:00:00.000Z'));
    expect(onDate.disabled).toBe(false);
    expect(onDate.tooltip).toBeNull();

    const after = getActionDisabledState(checkIn, startTime, new Date('2026-11-04T00:00:00.000Z'));
    expect(after.disabled).toBe(true);
  });

  it('no-show is disabled before the start time and enabled once it has passed', () => {
    const noShow = APPOINTMENT_ACTIONS.find((a) => a.key === 'NO_SHOW')!;
    const startTime = '2026-11-03T09:30:00.000Z';

    expect(getActionDisabledState(noShow, startTime, new Date('2026-11-03T09:00:00.000Z')).disabled).toBe(true);
    expect(getActionDisabledState(noShow, startTime, new Date('2026-11-03T09:30:00.000Z')).disabled).toBe(false);
    expect(getActionDisabledState(noShow, startTime, new Date('2026-11-03T10:00:00.000Z')).disabled).toBe(false);
  });
});

describe('appointment types: VIDEO is hidden', () => {
  it('excludes VIDEO from the visible booking types', () => {
    expect(VISIBLE_APPOINTMENT_TYPES).not.toContain('VIDEO');
    expect(VISIBLE_APPOINTMENT_TYPES).toEqual(['IN_PERSON', 'WALK_IN', 'FOLLOW_UP']);
  });
});

describe('appointmentStatus: needsRescheduling badge', () => {
  it('flags the badge when needsRescheduling is true', () => {
    expect(getAppointmentBadge({ status: 'CONFIRMED', needsRescheduling: true }).needsRescheduling).toBe(true);
  });

  it('does not flag the badge when needsRescheduling is false', () => {
    expect(getAppointmentBadge({ status: 'CONFIRMED', needsRescheduling: false }).needsRescheduling).toBe(false);
  });

  it('carries the status label and tone alongside the badge decision', () => {
    const badge = getAppointmentBadge({ status: 'NO_SHOW', needsRescheduling: true });
    expect(badge.label).toBe('No-show');
    expect(badge.tone).toBe('danger');
  });
});
