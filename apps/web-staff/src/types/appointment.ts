import type { PatientSummary } from './patient';

export type AppointmentStatus =
  | 'BOOKED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'IN_CONSULTATION'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'RESCHEDULED';

export type AppointmentType = 'IN_PERSON' | 'VIDEO' | 'WALK_IN' | 'FOLLOW_UP';

/**
 * Appointment types offered in the booking UI. VIDEO exists backend-side but
 * is hidden until telemedicine is built (Phase 9).
 */
export const VISIBLE_APPOINTMENT_TYPES: AppointmentType[] = ['IN_PERSON', 'WALK_IN', 'FOLLOW_UP'];

/** Mirrors the backend `AppointmentResponseDto`. Timestamps are ISO-8601 UTC instants. */
export interface Appointment {
  id: number;
  patient: PatientSummary;
  doctorId: number;
  doctorName: string | null;
  branchId: number;
  branchName: string | null;
  startTime: string;
  endTime: string;
  type: AppointmentType;
  status: AppointmentStatus;
  reasonForVisit: string | null;
  cancelReason: string | null;
  cancelledBy: string | null;
  createdBy: string | null;
  rescheduledFromId: number | null;
  needsRescheduling: boolean;
  confirmedAt: string | null;
  checkedInAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
}

export interface BookAppointmentRequest {
  patientId: number;
  doctorId: number;
  branchId: number;
  startTime: string;
  endTime: string;
  type: AppointmentType;
  reasonForVisit?: string;
}

export interface RescheduleAppointmentRequest {
  newStartTime: string;
  newEndTime: string;
  newBranchId?: number;
  reason?: string;
}

export interface CancelAppointmentRequest {
  reason: string;
}

export interface AppointmentFilters {
  doctorId?: number;
  branchId?: number;
  status?: AppointmentStatus;
  patientId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}
