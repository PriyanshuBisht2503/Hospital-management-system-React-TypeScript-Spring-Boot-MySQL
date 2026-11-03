import { describe, it, expect } from 'vitest';
import { scheduleExceptionSchema } from '../components/ScheduleExceptionForm';

describe('ScheduleExceptionForm validation', () => {
  const base = { date: '2026-01-01', type: 'LEAVE', startTime: '', endTime: '', reason: '' };

  it('accepts a full-day leave or holiday', () => {
    expect(scheduleExceptionSchema.safeParse(base).success).toBe(true);
    expect(scheduleExceptionSchema.safeParse({ ...base, type: 'HOLIDAY' }).success).toBe(true);
  });

  it('requires both start and end time, or neither', () => {
    expect(scheduleExceptionSchema.safeParse({ ...base, startTime: '09:00', endTime: '' }).success).toBe(false);
    expect(scheduleExceptionSchema.safeParse({ ...base, startTime: '', endTime: '10:00' }).success).toBe(false);
  });

  it('requires end time after start time', () => {
    expect(scheduleExceptionSchema.safeParse({ ...base, startTime: '10:00', endTime: '09:00' }).success).toBe(false);
  });

  it('requires start and end time for EXTRA_HOURS', () => {
    expect(scheduleExceptionSchema.safeParse({ ...base, type: 'EXTRA_HOURS' }).success).toBe(false);
    expect(scheduleExceptionSchema.safeParse({ ...base, type: 'EXTRA_HOURS', startTime: '18:00', endTime: '20:00' }).success).toBe(true);
  });
});
