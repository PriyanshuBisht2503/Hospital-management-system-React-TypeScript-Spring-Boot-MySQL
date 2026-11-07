import { apiRequest } from './client';
import { buildQueryString } from './query';
import type { PageResponse } from '../types/masterData';
import type {
  Appointment,
  AppointmentFilters,
  BookAppointmentRequest,
  CancelAppointmentRequest,
  RescheduleAppointmentRequest,
} from '../types/appointment';

export function listAppointments(filters: AppointmentFilters = {}) {
  return apiRequest<PageResponse<Appointment>>(`/api/appointments${buildQueryString({ ...filters })}`);
}

export function getAppointment(id: number) {
  return apiRequest<Appointment>(`/api/appointments/${id}`);
}

export function bookAppointment(body: BookAppointmentRequest) {
  return apiRequest<Appointment>('/api/appointments', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function confirmAppointment(id: number) {
  return apiRequest<Appointment>(`/api/appointments/${id}/confirm`, { method: 'PATCH' });
}

export function checkInAppointment(id: number) {
  return apiRequest<Appointment>(`/api/appointments/${id}/check-in`, { method: 'PATCH' });
}

export function startConsultation(id: number) {
  return apiRequest<Appointment>(`/api/appointments/${id}/start-consultation`, { method: 'PATCH' });
}

export function completeAppointment(id: number) {
  return apiRequest<Appointment>(`/api/appointments/${id}/complete`, { method: 'PATCH' });
}

export function markNoShow(id: number) {
  return apiRequest<Appointment>(`/api/appointments/${id}/no-show`, { method: 'PATCH' });
}

export function cancelAppointment(id: number, body: CancelAppointmentRequest) {
  return apiRequest<Appointment>(`/api/appointments/${id}/cancel`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
}

export function rescheduleAppointment(id: number, body: RescheduleAppointmentRequest) {
  return apiRequest<Appointment>(`/api/appointments/${id}/reschedule`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
