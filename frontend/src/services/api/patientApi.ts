import { api } from '../api';
import type { Patient, PagedResponse } from '../../types';

export const patientApi = {
  getPatients: async (query: string): Promise<PagedResponse<Patient>> => {
    const response = await api.get(`/patients?query=${encodeURIComponent(query)}`);
    return response.data;
  },
  createPatient: async (payload: any): Promise<Patient> => {
    const response = await api.post('/patients', payload);
    return response.data;
  },
  deletePatient: async (id: number): Promise<void> => {
    await api.delete(`/patients/${id}`);
  },
};
