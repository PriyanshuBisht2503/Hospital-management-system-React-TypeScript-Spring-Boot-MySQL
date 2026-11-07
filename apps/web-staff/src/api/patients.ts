import { apiRequest } from './client';
import { buildQueryString } from './query';
import type { PageResponse } from '../types/masterData';
import type { CreatePatientRequest, Patient } from '../types/patient';

export interface PatientSearchParams {
  search?: string;
  page?: number;
  size?: number;
}

export function listPatients(params: PatientSearchParams = {}) {
  return apiRequest<PageResponse<Patient>>(`/api/patients${buildQueryString({ ...params })}`);
}

export function getPatient(id: number) {
  return apiRequest<Patient>(`/api/patients/${id}`);
}

export function createPatient(body: CreatePatientRequest) {
  return apiRequest<Patient>('/api/patients', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
