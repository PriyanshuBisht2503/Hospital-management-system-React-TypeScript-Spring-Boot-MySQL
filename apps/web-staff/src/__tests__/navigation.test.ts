import { describe, it, expect } from 'vitest';
import { getPhase2Nav } from '../lib/navigation';

describe('navigation: role-based sidebar entries', () => {
  it('ADMIN sees Patients, Appointments, Schedules and Day Agenda', () => {
    expect(getPhase2Nav('ADMIN').map((i) => i.label)).toEqual([
      'Patients',
      'Appointments',
      'Schedules',
      'Day Agenda',
    ]);
  });

  it('RECEPTIONIST sees the same clinical pages as ADMIN', () => {
    expect(getPhase2Nav('RECEPTIONIST').map((i) => i.label)).toEqual([
      'Patients',
      'Appointments',
      'Schedules',
      'Day Agenda',
    ]);
  });

  it('DOCTOR sees Patients, My Day and their own schedule only', () => {
    expect(getPhase2Nav('DOCTOR').map((i) => i.label)).toEqual(['Patients', 'My Day', 'My Schedule']);
  });

  it('DOCTOR My Schedule href resolves to their own doctorId', () => {
    const items = getPhase2Nav('DOCTOR', 42);
    expect(items.find((i) => i.label === 'My Schedule')?.href).toBe('/schedules/42');
  });

  it('DOCTOR My Schedule href falls back to /schedules when doctorId is unknown', () => {
    const items = getPhase2Nav('DOCTOR', null);
    expect(items.find((i) => i.label === 'My Schedule')?.href).toBe('/schedules');
  });

  it('PATIENT sees no staff pages', () => {
    expect(getPhase2Nav('PATIENT')).toEqual([]);
  });
});
