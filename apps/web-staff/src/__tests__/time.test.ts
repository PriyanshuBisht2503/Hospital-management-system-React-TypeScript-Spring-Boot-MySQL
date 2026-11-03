import { describe, it, expect } from 'vitest';
import {
  IST_OFFSET_MINUTES,
  endOfIstDayUtc,
  istTimeToUtc,
  startOfIstDayUtc,
  toIstDateString,
} from '../lib/time';

describe('time.ts: IST day-range to UTC instants', () => {
  it('startOfIstDayUtc maps IST midnight (00:00) to the previous UTC day at 18:30Z', () => {
    expect(startOfIstDayUtc('2026-11-03')).toBe('2026-11-02T18:30:00.000Z');
  });

  it('endOfIstDayUtc maps 24:00 IST to the same UTC day at 18:30Z', () => {
    expect(endOfIstDayUtc('2026-11-03')).toBe('2026-11-03T18:30:00.000Z');
  });

  it('maps the 00:00-00:30 IST slot to 18:30-19:00 UTC (previous day)', () => {
    expect(istTimeToUtc('2026-11-03', '00:00')).toBe('2026-11-02T18:30:00.000Z');
    expect(istTimeToUtc('2026-11-03', '00:30')).toBe('2026-11-02T19:00:00.000Z');
  });

  it('maps the 23:30-24:00 IST slot to 18:00-18:30 UTC (same day)', () => {
    expect(istTimeToUtc('2026-11-03', '23:30')).toBe('2026-11-03T18:00:00.000Z');
    expect(endOfIstDayUtc('2026-11-03')).toBe('2026-11-03T18:30:00.000Z');
  });

  it('uses a fixed +05:30 offset with no DST shift across the year', () => {
    expect(IST_OFFSET_MINUTES).toBe(330);
    // A winter and a summer date both sit exactly 5h30m behind the UTC midnight.
    expect(startOfIstDayUtc('2026-01-15')).toBe('2026-01-14T18:30:00.000Z');
    expect(startOfIstDayUtc('2026-07-15')).toBe('2026-07-14T18:30:00.000Z');
  });

  it('toIstDateString converts a UTC instant to its IST calendar date', () => {
    expect(toIstDateString('2026-11-02T18:30:00.000Z')).toBe('2026-11-03');
    expect(toIstDateString('2026-11-03T17:59:59.000Z')).toBe('2026-11-03');
    expect(toIstDateString('2026-11-03T18:30:00.000Z')).toBe('2026-11-04');
  });
});
