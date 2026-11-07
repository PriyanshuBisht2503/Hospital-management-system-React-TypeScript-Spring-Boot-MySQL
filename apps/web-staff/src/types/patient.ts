export type Gender = 'MALE' | 'FEMALE' | 'OTHER';

/**
 * Patient contact embedded in appointment responses.
 * phone/email are masked (null) for DOCTOR callers.
 */
export interface PatientSummary {
  id: number;
  fullName: string;
  age: number | null;
  phone: string | null;
  email: string | null;
}

/** Full patient record (mirrors the backend `PatientDto`). */
export interface Patient {
  id: number;
  userId: number | null;
  fullName: string;
  phone: string | null;
  email: string | null;
  dateOfBirth: string | null;
  age: number | null;
  gender: Gender | null;
  createdAt: string;
}

export interface CreatePatientRequest {
  fullName: string;
  phone?: string;
  email?: string;
  dateOfBirth?: string;
  gender?: Gender;
}
