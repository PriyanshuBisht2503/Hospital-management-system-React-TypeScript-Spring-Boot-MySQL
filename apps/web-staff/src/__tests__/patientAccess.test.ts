import { describe, it, expect } from 'vitest';
import { PATIENT_CREATE_ROLES, PATIENT_DIRECTORY_ROLES } from '../lib/access';

describe('patients access: role-based route guard', () => {
  it('allows ADMIN and RECEPTIONIST to create patients, but not DOCTOR or PATIENT', () => {
    expect(PATIENT_CREATE_ROLES).toEqual(['ADMIN', 'RECEPTIONIST']);
    expect(PATIENT_CREATE_ROLES.includes('DOCTOR')).toBe(false);
    expect(PATIENT_CREATE_ROLES.includes('PATIENT')).toBe(false);
  });

  it('allows ADMIN, RECEPTIONIST and DOCTOR to view the directory', () => {
    expect(PATIENT_DIRECTORY_ROLES).toEqual(['ADMIN', 'RECEPTIONIST', 'DOCTOR']);
    expect(PATIENT_DIRECTORY_ROLES.includes('DOCTOR')).toBe(true);
    expect(PATIENT_DIRECTORY_ROLES.includes('PATIENT')).toBe(false);
  });
});
