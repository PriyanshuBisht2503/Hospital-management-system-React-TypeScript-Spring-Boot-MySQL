import { describe, it, expect } from 'vitest';
import { addIstDays, formatIstDateTime, formatIstTime, nextIstDates, toIstDateString } from '../lib/time';

describe('IST time helpers', () => {
  it('formats a UTC instant as an IST date-time', () => {
    expect(formatIstDateTime('2026-11-10T04:00:00.000Z')).toBe('2026-11-10 09:30 IST');
  });

  it('formats a UTC instant as an IST time', () => {
    expect(formatIstTime('2026-11-10T04:00:00.000Z')).toBe('09:30');
    expect(formatIstTime('2026-11-10T18:30:00.000Z')).toBe('00:00');
  });

  it('maps an instant to its IST calendar date and adds days', () => {
    expect(toIstDateString('2026-11-10T18:30:00.000Z')).toBe('2026-11-11');
    expect(addIstDays('2026-11-10', 2)).toBe('2026-11-12');
    expect(addIstDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('returns the next N IST dates', () => {
    const dates = nextIstDates(3, new Date('2026-11-10T04:00:00.000Z'));
    expect(dates).toEqual(['2026-11-10', '2026-11-11', '2026-11-12']);
  });
});
