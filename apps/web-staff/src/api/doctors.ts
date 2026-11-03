import { apiRequest } from './client';
import { buildQueryString } from './query';
import type { PageResponse } from '../types/masterData';
import type {
  AvailableSlot,
  DoctorAdmin,
  DoctorPublic,
  DoctorSearchResult,
} from '../types/doctor';

export interface DoctorSearchParams {
  specialtyId?: number;
  branchId?: number;
  search?: string;
  page?: number;
  size?: number;
}

/** Public doctor search. Used by the booking wizard and day agenda (works for RECEPTIONIST). */
export function searchDoctors(params: DoctorSearchParams = {}) {
  return apiRequest<PageResponse<DoctorSearchResult>>(`/api/doctors/search${buildQueryString({ ...params })}`);
}

export function getDoctor(doctorId: number) {
  return apiRequest<DoctorPublic>(`/api/doctors/${doctorId}`);
}

export interface DoctorSlotsParams {
  branchId?: number;
  date?: string;
  from?: string;
  to?: string;
}

export function getDoctorSlots(doctorId: number, params: DoctorSlotsParams = {}) {
  return apiRequest<AvailableSlot[]>(`/api/doctors/${doctorId}/slots${buildQueryString({ ...params })}`);
}

/** Admin-only doctor list (schedule manager only). */
export function listAdminDoctors(params: DoctorSearchParams = {}) {
  return apiRequest<PageResponse<DoctorAdmin>>(`/api/admin/doctors${buildQueryString({ ...params })}`);
}

export function getAdminDoctor(doctorId: number) {
  return apiRequest<DoctorAdmin>(`/api/admin/doctors/${doctorId}`);
}
