import type { Role } from '../types/auth';

/**
 * Renders a patient contact value. A null value is masked (hidden) for DOCTOR
 * callers and shown as a placeholder otherwise, so no cell ever looks empty.
 */
export function patientContactText(value: string | null, role: Role): string {
  if (value) return value;
  return role === 'DOCTOR' ? 'Hidden' : '—';
}
