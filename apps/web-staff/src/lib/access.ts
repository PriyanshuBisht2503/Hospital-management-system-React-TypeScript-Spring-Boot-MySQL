import type { Role } from '../types/auth';

/** Roles that may view the patient directory and profile (backend GET /api/patients). */
export const PATIENT_DIRECTORY_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST', 'DOCTOR'];

/** Roles that may create a patient (backend POST /api/patients). */
export const PATIENT_CREATE_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST'];

/** Roles that may open the schedule pages (backend GET /api/admin/doctors/{id}/schedules). */
export const SCHEDULE_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST', 'DOCTOR'];

/** Roles that may view the appointment list and detail. */
export const APPOINTMENT_VIEW_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST', 'DOCTOR'];

/** Roles that may create an appointment (booking wizard). */
export const APPOINTMENT_CREATE_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST'];

/** Roles that may open their own "My Day" agenda. */
export const MY_DAY_ROLES: Role[] = ['DOCTOR'];

/** Roles that may open the day agenda for any doctor. */
export const DAY_AGENDA_ROLES: Role[] = ['ADMIN', 'RECEPTIONIST'];
