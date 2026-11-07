import { describe, it, expect, afterEach, vi } from 'vitest';
import { buildPatientAppUrl, patientAppBaseUrl } from '../lib/patientAppUrl';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('buildPatientAppUrl', () => {
  it('uses the localhost default when the env var is not set', () => {
    vi.stubEnv('VITE_PATIENT_APP_URL', '');
    expect(patientAppBaseUrl()).toBe('http://localhost:3001');
    expect(buildPatientAppUrl('/login')).toBe('http://localhost:3001/login');
  });

  it('uses the env override and strips a trailing slash', () => {
    vi.stubEnv('VITE_PATIENT_APP_URL', 'https://portal.example.com/');
    expect(buildPatientAppUrl('/book')).toBe('https://portal.example.com/book');
  });

  it('encodes query parameters and skips empty values', () => {
    const url = buildPatientAppUrl('/book', { doctorId: 7, branchId: 2, search: 'A B', empty: '', missing: undefined });
    expect(url).toBe('http://localhost:3001/book?doctorId=7&branchId=2&search=A+B');
    expect(url).not.toContain('empty=');
    expect(url).not.toContain('missing=');
  });
});
