import { apiRequest } from './client';
import type {
  CreateDoctorScheduleRequest,
  CreateScheduleExceptionRequest,
  DoctorSchedule,
  ScheduleException,
  UpdateDoctorScheduleRequest,
} from '../types/schedule';

export function listDoctorSchedules(doctorId: number) {
  return apiRequest<DoctorSchedule[]>(`/api/admin/doctors/${doctorId}/schedules`);
}

export function createDoctorSchedule(doctorId: number, body: CreateDoctorScheduleRequest) {
  return apiRequest<DoctorSchedule>(`/api/admin/doctors/${doctorId}/schedules`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateDoctorSchedule(doctorId: number, scheduleId: number, body: UpdateDoctorScheduleRequest) {
  return apiRequest<DoctorSchedule>(`/api/admin/doctors/${doctorId}/schedules/${scheduleId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function deleteDoctorSchedule(doctorId: number, scheduleId: number) {
  return apiRequest<void>(`/api/admin/doctors/${doctorId}/schedules/${scheduleId}`, { method: 'DELETE' });
}

export function listScheduleExceptions(doctorId: number) {
  return apiRequest<ScheduleException[]>(`/api/admin/doctors/${doctorId}/exceptions`);
}

export function createScheduleException(doctorId: number, body: CreateScheduleExceptionRequest) {
  return apiRequest<ScheduleException>(`/api/admin/doctors/${doctorId}/exceptions`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function deleteScheduleException(doctorId: number, exceptionId: number) {
  return apiRequest<void>(`/api/admin/doctors/${doctorId}/exceptions/${exceptionId}`, { method: 'DELETE' });
}
