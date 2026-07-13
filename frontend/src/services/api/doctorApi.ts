import { api } from '../api';
import type { Doctor, PagedResponse } from '../../types';

export const doctorApi = {
  getDoctors: async (): Promise<PagedResponse<Doctor>> => {
    const response = await api.get('/doctors');
    return response.data;
  },
};
