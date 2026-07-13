import { api } from '../api';
import type { Appointment, PagedResponse } from '../../types';

export const appointmentApi = {
  getPatientAppointments: async (patientId: number): Promise<PagedResponse<Appointment>> => {
    const response = await api.get(`/appointments/patient/${patientId}`);
    return response.data;
  },
  getDoctorAppointments: async (doctorId: number): Promise<PagedResponse<Appointment>> => {
    const response = await api.get(`/appointments/doctor/${doctorId}`);
    return response.data;
  },
  createAppointment: async (payload: {
    patientId: number;
    doctorId: number;
    appointmentDate: string;
    timeSlot: string;
  }): Promise<Appointment> => {
    const response = await api.post('/appointments', payload);
    return response.data;
  },
  cancelAppointment: async (id: number): Promise<void> => {
    await api.patch(`/appointments/${id}/cancel`);
  },
};
