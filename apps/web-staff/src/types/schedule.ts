export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export type ScheduleExceptionType = 'LEAVE' | 'HOLIDAY' | 'EXTRA_HOURS';

/**
 * Weekly schedule template (mirrors the backend `DoctorScheduleDto`).
 * Times are LocalTime "HH:mm"; dates are LocalDate "YYYY-MM-DD".
 */
export interface DoctorSchedule {
  id: number;
  doctorId: number;
  branchId: number;
  branchName: string | null;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  slotMinutes: number;
  breakStart: string | null;
  breakEnd: string | null;
  validFrom: string;
  validTo: string | null;
  isActive: boolean;
}

export interface CreateDoctorScheduleRequest {
  branchId: number;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  slotMinutes: number;
  breakStart?: string;
  breakEnd?: string;
  validFrom: string;
  validTo?: string;
}

export interface UpdateDoctorScheduleRequest extends CreateDoctorScheduleRequest {
  isActive?: boolean;
}

/** Schedule exception (leave / holiday / extra hours). Mirrors `ScheduleExceptionDto`. */
export interface ScheduleException {
  id: number;
  doctorId: number;
  date: string;
  startTime: string | null;
  endTime: string | null;
  type: ScheduleExceptionType;
  reason: string | null;
}

export interface CreateScheduleExceptionRequest {
  date: string;
  startTime?: string;
  endTime?: string;
  type: ScheduleExceptionType;
  reason?: string;
  force: boolean;
}
