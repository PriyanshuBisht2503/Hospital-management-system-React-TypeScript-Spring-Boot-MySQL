import { describe, it, expect } from 'vitest';
import { scheduleTemplateSchema, findTemplateOverlap } from '../components/ScheduleTemplateForm';
import { timeToMinutes, stripSeconds } from '../lib/localTime';
import type { DoctorSchedule } from '../types/schedule';

describe('ScheduleTemplateForm validation', () => {
  const base = {
    branchId: 1,
    dayOfWeek: 'MONDAY',
    startTime: '09:00',
    endTime: '17:00',
    slotMinutes: 30,
    breakStart: '',
    breakEnd: '',
    validFrom: '2026-01-01',
    validTo: '',
  };

  it('accepts a valid template', () => {
    expect(scheduleTemplateSchema.safeParse(base).success).toBe(true);
  });

  it('requires end time after start time', () => {
    expect(scheduleTemplateSchema.safeParse({ ...base, endTime: '08:00' }).success).toBe(false);
  });

  it('requires slotMinutes between 5 and 120', () => {
    expect(scheduleTemplateSchema.safeParse({ ...base, slotMinutes: 4 }).success).toBe(false);
    expect(scheduleTemplateSchema.safeParse({ ...base, slotMinutes: 121 }).success).toBe(false);
    expect(scheduleTemplateSchema.safeParse({ ...base, slotMinutes: 120 }).success).toBe(true);
  });

  it('requires break start and end both-or-neither', () => {
    expect(scheduleTemplateSchema.safeParse({ ...base, breakStart: '12:00', breakEnd: '' }).success).toBe(false);
    expect(scheduleTemplateSchema.safeParse({ ...base, breakStart: '', breakEnd: '13:00' }).success).toBe(false);
    expect(scheduleTemplateSchema.safeParse({ ...base, breakStart: '12:00', breakEnd: '13:00' }).success).toBe(true);
  });

  it('requires break inside the working window with breakStart < breakEnd', () => {
    expect(scheduleTemplateSchema.safeParse({ ...base, breakStart: '08:00', breakEnd: '09:00' }).success).toBe(false);
    expect(scheduleTemplateSchema.safeParse({ ...base, breakStart: '13:00', breakEnd: '12:00' }).success).toBe(false);
  });

  it('requires validTo on or after validFrom', () => {
    expect(scheduleTemplateSchema.safeParse({ ...base, validTo: '2025-12-31' }).success).toBe(false);
    expect(scheduleTemplateSchema.safeParse({ ...base, validTo: '2026-01-02' }).success).toBe(true);
  });

  it('treats schedule times as local minutes, NOT UTC instants', () => {
    expect(timeToMinutes('09:00:00')).toBe(540);
    expect(timeToMinutes('00:00')).toBe(0);
    expect(stripSeconds('09:00:00')).toBe('09:00');
  });
});

describe('ScheduleTemplateForm overlap check', () => {
  const existing: DoctorSchedule[] = [
    {
      id: 1,
      doctorId: 1,
      branchId: 1,
      branchName: 'Main',
      dayOfWeek: 'MONDAY',
      startTime: '09:00:00',
      endTime: '12:00:00',
      slotMinutes: 30,
      breakStart: null,
      breakEnd: null,
      validFrom: '2026-01-01',
      validTo: null,
      isActive: true,
    },
  ];

  it('flags an overlapping same-day, same-branch template', () => {
    expect(
      findTemplateOverlap({ dayOfWeek: 'MONDAY', branchId: 1, startTime: '11:00', endTime: '13:00', validFrom: '2026-01-01', validTo: null }, existing),
    ).toBeTruthy();
  });

  it('does not flag non-overlapping times', () => {
    expect(
      findTemplateOverlap({ dayOfWeek: 'MONDAY', branchId: 1, startTime: '13:00', endTime: '14:00', validFrom: '2026-01-01', validTo: null }, existing),
    ).toBeNull();
  });

  it('does not flag a different day', () => {
    expect(
      findTemplateOverlap({ dayOfWeek: 'TUESDAY', branchId: 1, startTime: '09:00', endTime: '12:00', validFrom: '2026-01-01', validTo: null }, existing),
    ).toBeNull();
  });

  it('does not flag non-overlapping validity windows', () => {
    expect(
      findTemplateOverlap({ dayOfWeek: 'MONDAY', branchId: 1, startTime: '09:00', endTime: '12:00', validFrom: '2026-02-01', validTo: null }, existing),
    ).toBeNull();
  });
});
