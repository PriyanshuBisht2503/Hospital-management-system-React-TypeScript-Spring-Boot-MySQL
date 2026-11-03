import type { Role } from '../types/auth';

/** Roles that may view the patient directory and profile (backend GET /api/patients). */
export const PATIENT_DIRECTORY_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST', 'DOCTOR'];

/** Roles that may create a patient (backend POST /api/patients). */
export const PATIENT_CREATE_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST'];
