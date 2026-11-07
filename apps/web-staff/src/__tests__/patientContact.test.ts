import { describe, it, expect } from 'vitest';
import { patientContactText } from '../lib/patientContact';

describe('patientContactText: masked contact display', () => {
  it('shows the value when present', () => {
    expect(patientContactText('+971501234567', 'DOCTOR')).toBe('+971501234567');
  });

  it('shows "Hidden" for a DOCTOR when the value is masked (null)', () => {
    expect(patientContactText(null, 'DOCTOR')).toBe('Hidden');
  });

  it('shows an em dash placeholder for ADMIN and RECEPTIONIST when absent', () => {
    expect(patientContactText(null, 'ADMIN')).toBe('—');
    expect(patientContactText(null, 'RECEPTIONIST')).toBe('—');
  });
});
